//! FX (foreign-exchange) conversion math.
//!
//! Network fetching lives in the Android/Kotlin layer; this crate only holds
//! the pure arithmetic and an embedded fallback rate table.
//!
//! All rates are stored relative to **USD** (1 USD = X foreign currency).
//! To convert `amount` from `from_code` to `to_code`:
//!   `amount × (rate[to_code] / rate[from_code])`

use std::collections::HashMap;

// ── embedded fallback rates (USD-based, approximate 2024-01) ─────────────────

static FALLBACK_JSON: &str = include_str!("../data/rates_fallback.json");

// ── error ─────────────────────────────────────────────────────────────────────

#[derive(Debug, thiserror::Error, PartialEq)]
pub enum FxError {
    #[error("unknown currency code '{0}'")]
    UnknownCode(String),
    #[error("invalid rate for '{0}': must be positive")]
    InvalidRate(String),
}

// ── public API ────────────────────────────────────────────────────────────────

/// Holds a map of currency codes → rate (1 USD = N units).
pub struct RateTable {
    rates: HashMap<String, f64>,
}

impl RateTable {
    /// Build from a JSON object: `{ "EUR": 0.92, "JPY": 148.5, ... }`.
    pub fn from_json(json: &str) -> Result<Self, String> {
        let rates: HashMap<String, f64> =
            serde_json::from_str(json).map_err(|e| e.to_string())?;
        Ok(RateTable { rates })
    }

    /// Build from the embedded fallback JSON.
    pub fn fallback() -> Self {
        Self::from_json(FALLBACK_JSON).expect("embedded fallback rates must be valid")
    }

    /// All supported currency codes (sorted).
    pub fn codes(&self) -> Vec<&str> {
        let mut v: Vec<&str> = self.rates.keys().map(|s| s.as_str()).collect();
        v.sort_unstable();
        v
    }

    /// Convert `amount` from `from` to `to` using stored rates.
    pub fn convert(&self, from: &str, to: &str, amount: f64) -> Result<f64, FxError> {
        if from == to {
            return Ok(amount);
        }
        let r_from = *self
            .rates
            .get(from)
            .ok_or_else(|| FxError::UnknownCode(from.to_string()))?;
        let r_to = *self
            .rates
            .get(to)
            .ok_or_else(|| FxError::UnknownCode(to.to_string()))?;
        if r_from <= 0.0 {
            return Err(FxError::InvalidRate(from.to_string()));
        }
        if r_to <= 0.0 {
            return Err(FxError::InvalidRate(to.to_string()));
        }
        Ok(amount * (r_to / r_from))
    }
}

/// Stateless single conversion (caller supplies both rates directly).
pub fn convert_with_rates(amount: f64, from_rate: f64, to_rate: f64) -> f64 {
    if from_rate <= 0.0 || to_rate <= 0.0 {
        return f64::NAN;
    }
    amount * (to_rate / from_rate)
}

pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

// ── tests ─────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    fn table() -> RateTable {
        RateTable::fallback()
    }

    #[test]
    fn usd_to_eur_reasonable() {
        let t = table();
        // 1 USD → EUR should be roughly 0.7–1.0
        let r = t.convert("USD", "EUR", 1.0).unwrap();
        assert!(r > 0.5 && r < 1.1, "unexpected EUR rate: {r}");
    }

    #[test]
    fn same_currency_identity() {
        let t = table();
        assert_eq!(t.convert("USD", "USD", 42.0).unwrap(), 42.0);
    }

    #[test]
    fn roundtrip_usd_jpy() {
        let t = table();
        let jpy = t.convert("USD", "JPY", 1.0).unwrap();
        let back = t.convert("JPY", "USD", jpy).unwrap();
        assert!((back - 1.0).abs() < 1e-9, "roundtrip failed: {back}");
    }

    #[test]
    fn unknown_code_error() {
        let t = table();
        assert_eq!(
            t.convert("USD", "XYZ", 1.0),
            Err(FxError::UnknownCode("XYZ".into()))
        );
    }

    #[test]
    fn codes_sorted() {
        let t = table();
        let codes = t.codes();
        let mut sorted = codes.clone();
        sorted.sort_unstable();
        assert_eq!(codes, sorted);
    }

    #[test]
    fn fallback_covers_major_currencies() {
        let t = table();
        let codes = t.codes();
        for code in ["USD", "EUR", "GBP", "JPY", "CNY", "CAD", "AUD", "CHF"] {
            assert!(codes.contains(&code), "missing {code}");
        }
    }

    #[test]
    fn convert_with_rates_direct() {
        // 1 USD → 0.92 EUR: amount=100, from_rate=1.0, to_rate=0.92
        let r = convert_with_rates(100.0, 1.0, 0.92);
        assert!((r - 92.0).abs() < 1e-9);
    }

    #[test]
    fn convert_with_rates_invalid() {
        assert!(convert_with_rates(1.0, 0.0, 1.0).is_nan());
    }

    #[test]
    fn from_json_roundtrip() {
        let json = r#"{"USD":1.0,"EUR":0.92}"#;
        let t = RateTable::from_json(json).unwrap();
        let r = t.convert("USD", "EUR", 100.0).unwrap();
        assert!((r - 92.0).abs() < 1e-9);
    }
}
