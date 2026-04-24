//! International unit conversions.
//!
//! Supports 8 categories: length, weight, area, volume, time, temperature,
//! velocity, data. Chinese-specific units (里、丈、尺、寸、錢、兩、斤) are
//! excluded; all remaining units are internationally recognised.
//!
//! # Design
//!
//! Unit data lives in `data/units.json` (embedded at compile time).  Each
//! category records a **base** unit and a list of directed **conversion rules**
//! that are either:
//!
//! - **Ratio** – a constant arithmetic expression (`"10000/254"`).
//!   `to_value = from_value × ratio` (base→unit direction).
//! - **Formula** – an expression string containing `x` as the input variable
//!   (`"1.8*(x)+32"`).  Used for non-linear conversions (temperature, time).
//!
//! All ratio expressions are evaluated once at startup using `calcrux-engine`
//! and cached as `f64`.  Formula strings are evaluated on each call by
//! substituting the concrete value for `x`.
//!
//! To convert **A → B** the converter:
//! 1. Checks for a direct `(A, B)` rule.
//! 2. Otherwise chains `A → base` then `base → B`.
//!    For the `A → base` leg, if only the inverse `base → A` (ratio) is stored
//!    the ratio is inverted (`1 / r`).

use std::collections::HashMap;

use calcrux_engine::Evaluator;
use serde::Deserialize;

pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

// ── data model ────────────────────────────────────────────────────────────────

static UNITS_JSON: &str = include_str!("../data/units.json");

/// Parsed entry from `units.json`.
#[derive(Debug, Deserialize)]
struct RawCategory {
    base: String,
    conversions: Vec<RawConversion>,
}

#[derive(Debug, Deserialize)]
struct RawConversion {
    from: String,
    to: String,
    ratio: Option<String>,
    formula: Option<String>,
}

/// Runtime rule for a directed (from, to) conversion.
#[derive(Debug, Clone)]
enum Rule {
    /// `result = input * ratio`
    Ratio(f64),
    /// Expression string with `x` as input variable.
    Formula(String),
}

#[derive(Debug)]
struct CategoryData {
    base: String,
    rules: HashMap<(String, String), Rule>,
    units: Vec<String>,
}

// ── error ─────────────────────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error, PartialEq)]
pub enum UnitError {
    #[error("unknown category '{0}'")]
    UnknownCategory(String),
    #[error("no conversion path from '{from}' to '{to}'")]
    NoPath { from: String, to: String },
    #[error("formula evaluation error: {0}")]
    Eval(String),
}

// ── public API ────────────────────────────────────────────────────────────────

/// Unit conversion engine.  Constructed once and reused.
pub struct Converter {
    categories: HashMap<String, CategoryData>,
}

impl Default for Converter {
    fn default() -> Self {
        Self::new()
    }
}

impl Converter {
    /// Build a `Converter` from the embedded `units.json`.
    pub fn new() -> Self {
        let raw: HashMap<String, RawCategory> =
            serde_json::from_str(UNITS_JSON).expect("units.json must be valid");
        let eval = Evaluator::default();
        let mut categories = HashMap::new();
        for (id, cat) in raw {
            categories.insert(id, build_category(cat, &eval));
        }
        Converter { categories }
    }

    /// Return the list of supported category IDs.
    pub fn categories(&self) -> Vec<&str> {
        let mut ids: Vec<&str> = self.categories.keys().map(|s| s.as_str()).collect();
        ids.sort_unstable();
        ids
    }

    /// Return the unit IDs for a category (sorted).
    pub fn units(&self, category: &str) -> Option<Vec<&str>> {
        self.categories.get(category).map(|c| {
            let mut v: Vec<&str> = c.units.iter().map(|s| s.as_str()).collect();
            v.sort_unstable();
            v
        })
    }

    /// Convert `value` in unit `from` to unit `to` within `category`.
    pub fn convert(
        &self,
        category: &str,
        from: &str,
        to: &str,
        value: f64,
    ) -> Result<f64, UnitError> {
        if from == to {
            return Ok(value);
        }
        let cat = self
            .categories
            .get(category)
            .ok_or_else(|| UnitError::UnknownCategory(category.to_string()))?;
        convert_impl(cat, from, to, value)
    }
}

// ── internals ─────────────────────────────────────────────────────────────────

fn build_category(raw: RawCategory, eval: &Evaluator) -> CategoryData {
    let mut rules: HashMap<(String, String), Rule> = HashMap::new();
    let mut unit_set: std::collections::HashSet<String> = std::collections::HashSet::new();

    unit_set.insert(raw.base.clone());

    for conv in raw.conversions {
        unit_set.insert(conv.from.clone());
        unit_set.insert(conv.to.clone());

        let rule = if let Some(ratio_expr) = conv.ratio {
            let ratio = eval
                .eval_str(&ratio_expr)
                .map(|n| n.to_f64())
                .unwrap_or(f64::NAN);
            Rule::Ratio(ratio)
        } else if let Some(formula) = conv.formula {
            Rule::Formula(formula)
        } else {
            continue;
        };
        rules.insert((conv.from, conv.to), rule);
    }

    let units: Vec<String> = unit_set.into_iter().collect();
    CategoryData { base: raw.base, rules, units }
}

fn convert_impl(cat: &CategoryData, from: &str, to: &str, value: f64) -> Result<f64, UnitError> {
    // 1 – direct rule
    if let Some(rule) = cat.rules.get(&(from.to_string(), to.to_string())) {
        return apply_rule(rule, value);
    }

    // 2 – two-hop via base: from → base → to
    let base = &cat.base;

    let in_base = if from == base {
        value
    } else {
        to_base(cat, from, value)?
    };

    if to == base {
        return Ok(in_base);
    }

    let key = (base.to_string(), to.to_string());
    let rule = cat.rules.get(&key).ok_or_else(|| UnitError::NoPath {
        from: from.to_string(),
        to: to.to_string(),
    })?;
    apply_rule(rule, in_base)
}

/// Convert `from` → base.
fn to_base(cat: &CategoryData, from: &str, value: f64) -> Result<f64, UnitError> {
    let base = &cat.base;

    // Direct (from → base) rule.
    if let Some(rule) = cat.rules.get(&(from.to_string(), base.to_string())) {
        return apply_rule(rule, value);
    }

    // Invert stored (base → from) ratio rule.
    if let Some(Rule::Ratio(r)) = cat.rules.get(&(base.to_string(), from.to_string())) {
        if *r == 0.0 || r.is_nan() {
            return Err(UnitError::Eval("ratio is zero or NaN".into()));
        }
        return Ok(value / r);
    }

    Err(UnitError::NoPath {
        from: from.to_string(),
        to: base.to_string(),
    })
}

fn apply_rule(rule: &Rule, value: f64) -> Result<f64, UnitError> {
    match rule {
        Rule::Ratio(r) => Ok(value * r),
        Rule::Formula(formula) => {
            // Substitute (value) for x, then evaluate with the engine.
            let expr = formula.replace('x', &format!("({})", value));
            Evaluator::default()
                .eval_str(&expr)
                .map(|n| n.to_f64())
                .map_err(|e| UnitError::Eval(e.to_string()))
        }
    }
}

// ── tests ─────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    fn conv() -> Converter {
        Converter::new()
    }

    fn approx(a: f64, b: f64, tol: f64) -> bool {
        (a - b).abs() < tol || (a - b).abs() / b.abs() < tol
    }

    // length
    #[test]
    fn angstrom_to_nm() {
        let c = conv();
        // 10 Å = 1 nm
        let r = c.convert("length", "ang", "nm", 10.0).unwrap();
        assert!(approx(r, 1.0, 1e-10));
    }

    #[test]
    fn nm_to_angstrom() {
        let c = conv();
        // 1 nm = 10 Å
        let r = c.convert("length", "nm", "ang", 1.0).unwrap();
        assert!(approx(r, 10.0, 1e-10));
    }

    #[test]
    fn angstrom_to_m() {
        let c = conv();
        // 1 Å = 1e-10 m
        let r = c.convert("length", "ang", "m", 1.0).unwrap();
        assert!(approx(r, 1e-10, 1e-20));
    }

    #[test]
    fn area_acre_vs_hectare() {
        let c = conv();
        // 1 acre ≈ 4046.856 m², 1 ha = 10000 m²  → 1 ha ≈ 2.471 acres
        let r = c.convert("area", "ha", "acre", 1.0).unwrap();
        assert!(approx(r, 2.47105, 1e-4));
    }

    #[test]
    fn metres_to_km() {
        let c = conv();
        assert!(approx(c.convert("length", "m", "km", 1000.0).unwrap(), 1.0, 1e-10));
    }

    #[test]
    fn metres_to_inches() {
        let c = conv();
        // 1 m ≈ 39.3701 in
        let r = c.convert("length", "m", "in", 1.0).unwrap();
        assert!(approx(r, 39.37007874015748, 1e-9));
    }

    #[test]
    fn km_to_miles() {
        let c = conv();
        // 1 km ≈ 0.621371 mi
        let r = c.convert("length", "km", "mi", 1.0).unwrap();
        assert!(approx(r, 0.6213711922373339, 1e-9));
    }

    #[test]
    fn feet_to_metres() {
        let c = conv();
        // 1 ft = 0.3048 m
        let r = c.convert("length", "ft", "m", 1.0).unwrap();
        assert!(approx(r, 0.3048, 1e-10));
    }

    // weight
    #[test]
    fn kg_to_lb() {
        let c = conv();
        // 1 kg ≈ 2.20462 lb
        let r = c.convert("weight", "kg", "lb", 1.0).unwrap();
        assert!(approx(r, 2.2046226218487757, 1e-9));
    }

    #[test]
    fn lb_to_kg() {
        let c = conv();
        let r = c.convert("weight", "lb", "kg", 1.0).unwrap();
        assert!(approx(r, 0.45359237, 1e-9));
    }

    // area
    #[test]
    fn sq_m_to_sq_ft() {
        let c = conv();
        // 1 m² ≈ 10.7639 ft²
        let r = c.convert("area", "sq.m", "sq.ft", 1.0).unwrap();
        assert!(approx(r, 10.763910416709722, 1e-8));
    }

    // volume
    #[test]
    fn cubic_m_to_litres() {
        let c = conv();
        assert!(approx(c.convert("volume", "cu.m", "l", 1.0).unwrap(), 1000.0, 1e-9));
    }

    // temperature
    #[test]
    fn celsius_to_fahrenheit() {
        let c = conv();
        // 0°C = 32°F
        let r = c.convert("temperature", "C", "F", 0.0).unwrap();
        assert!(approx(r, 32.0, 1e-10));
        // 100°C = 212°F
        let r2 = c.convert("temperature", "C", "F", 100.0).unwrap();
        assert!(approx(r2, 212.0, 1e-10));
    }

    #[test]
    fn fahrenheit_to_celsius() {
        let c = conv();
        // 32°F = 0°C
        let r = c.convert("temperature", "F", "C", 32.0).unwrap();
        assert!(approx(r, 0.0, 1e-10));
    }

    #[test]
    fn kelvin_to_celsius() {
        let c = conv();
        // 273.15 K = 0°C
        let r = c.convert("temperature", "K", "C", 273.15).unwrap();
        assert!(approx(r, 0.0, 1e-9));
    }

    #[test]
    fn celsius_to_kelvin() {
        let c = conv();
        let r = c.convert("temperature", "C", "K", 0.0).unwrap();
        assert!(approx(r, 273.15, 1e-10));
    }

    #[test]
    fn kelvin_to_fahrenheit_via_base() {
        let c = conv();
        // 373.15 K = 100°C = 212°F
        let r = c.convert("temperature", "K", "F", 373.15).unwrap();
        assert!(approx(r, 212.0, 1e-8));
    }

    #[test]
    fn celsius_to_reaumur() {
        let c = conv();
        // 80 Re = 100°C
        let r = c.convert("temperature", "C", "Re", 100.0).unwrap();
        assert!(approx(r, 80.0, 1e-10));
    }

    #[test]
    fn reaumur_to_celsius() {
        let c = conv();
        let r = c.convert("temperature", "Re", "C", 80.0).unwrap();
        assert!(approx(r, 100.0, 1e-10));
    }

    // time
    #[test]
    fn seconds_to_minutes() {
        let c = conv();
        assert!(approx(c.convert("time", "s", "min", 120.0).unwrap(), 2.0, 1e-10));
    }

    #[test]
    fn hours_to_seconds() {
        let c = conv();
        assert!(approx(c.convert("time", "h", "s", 1.0).unwrap(), 3600.0, 1e-10));
    }

    #[test]
    fn minutes_to_hours_via_base() {
        let c = conv();
        // 120 min = 2 h
        let r = c.convert("time", "min", "h", 120.0).unwrap();
        assert!(approx(r, 2.0, 1e-10));
    }

    #[test]
    fn days_to_years() {
        let c = conv();
        // 365 days = 1 yr
        let r = c.convert("time", "day", "yr", 365.0).unwrap();
        assert!(approx(r, 1.0, 1e-10));
    }

    // velocity
    #[test]
    fn mps_to_kmph() {
        let c = conv();
        assert!(approx(c.convert("velocity", "mps", "kmph", 1.0).unwrap(), 3.6, 1e-10));
    }

    // data
    #[test]
    fn bytes_to_kilobytes() {
        let c = conv();
        assert!(approx(c.convert("data", "b", "kb", 1024.0).unwrap(), 1.0, 1e-12));
    }

    #[test]
    fn megabytes_to_bytes() {
        let c = conv();
        assert!(approx(c.convert("data", "mb", "b", 1.0).unwrap(), 1048576.0, 1e-6));
    }

    #[test]
    fn gigabytes_to_megabytes_via_base() {
        let c = conv();
        assert!(approx(c.convert("data", "gb", "mb", 1.0).unwrap(), 1024.0, 1e-9));
    }

    // error cases
    #[test]
    fn unknown_category() {
        let c = conv();
        assert!(matches!(
            c.convert("energy", "J", "cal", 1.0),
            Err(UnitError::UnknownCategory(_))
        ));
    }

    #[test]
    fn same_unit_identity() {
        let c = conv();
        assert_eq!(c.convert("length", "m", "m", 42.0).unwrap(), 42.0);
    }

    #[test]
    fn negative_temperature() {
        let c = conv();
        // -40°C = -40°F (the crossover point)
        let r = c.convert("temperature", "C", "F", -40.0).unwrap();
        assert!(approx(r, -40.0, 1e-8));
    }
}
