//! UniFFI aggregation layer — exposes the Rust core to Kotlin/Android.
//!
//! Uses the proc-macro approach (no .udl file required).  All public
//! functions are annotated with `#[uniffi::export]`; error types derive
//! `uniffi::Error`; data records derive `uniffi::Record`.
//!
//! To regenerate Kotlin bindings run:
//!   cargo run --bin uniffi-bindgen generate \
//!     --library target/debug/libcalcrux.so \
//!     --language kotlin --out-dir android/app/src/main/java/com/calcrux/generated

// Register this crate as the UniFFI scaffolding root.
uniffi::setup_scaffolding!();

use calcrux_engine::{AngleMode, Evaluator};
use calcrux_units::Converter;
use calcrux_loan::{LoanParams, amortize, repayment_method};

// ── error types ───────────────────────────────────────────────────────────────

/// Calculator evaluation error (exposed to Kotlin as a sealed class).
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum CalcError {
    #[error("{0}")]
    Eval(String),
}

/// Unit conversion error.
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum ConvertError {
    #[error("{0}")]
    Convert(String),
}

/// Loan calculation error.
#[derive(Debug, thiserror::Error, uniffi::Error)]
#[uniffi(flat_error)]
pub enum FfiLoanError {
    #[error("{0}")]
    Loan(String),
}

// ── data records ──────────────────────────────────────────────────────────────

/// One month's instalment (mirrors `calcrux_loan::Instalment`).
#[derive(Debug, uniffi::Record)]
pub struct Instalment {
    pub period: u32,
    pub payment: f64,
    pub principal_part: f64,
    pub interest_part: f64,
    pub balance: f64,
}

/// Full amortization schedule.
#[derive(Debug, uniffi::Record)]
pub struct LoanSchedule {
    pub instalments: Vec<Instalment>,
    pub total_payment: f64,
    pub total_interest: f64,
}

// ── calculator ────────────────────────────────────────────────────────────────

/// Evaluate an arithmetic expression.
///
/// `degrees_mode = true` → trig functions work in degrees.
/// Returns the result as a decimal string (up to 18 significant digits).
#[uniffi::export]
pub fn calc_eval(expression: String, degrees_mode: bool) -> Result<String, CalcError> {
    let mode = if degrees_mode { AngleMode::Degrees } else { AngleMode::Radians };
    let eval = Evaluator::new(mode);
    eval.eval_str(&expression)
        .map(|n| n.to_string())
        .map_err(|e| CalcError::Eval(e.to_string()))
}

// ── unit conversion ───────────────────────────────────────────────────────────

/// Return all supported category IDs (sorted).
#[uniffi::export]
pub fn unit_categories() -> Vec<String> {
    Converter::new().categories().iter().map(|s| s.to_string()).collect()
}

/// Return all unit IDs in `category` (sorted), or `None` if unknown.
#[uniffi::export]
pub fn unit_list(category: String) -> Option<Vec<String>> {
    Converter::new()
        .units(&category)
        .map(|v| v.iter().map(|s| s.to_string()).collect())
}

/// Convert `value` from unit `from` to unit `to` within `category`.
#[uniffi::export]
pub fn convert_unit(
    category: String,
    from: String,
    to: String,
    value: f64,
) -> Result<f64, ConvertError> {
    Converter::new()
        .convert(&category, &from, &to, value)
        .map_err(|e| ConvertError::Convert(e.to_string()))
}

// ── loan ──────────────────────────────────────────────────────────────────────

fn to_ffi_schedule(
    s: calcrux_loan::Schedule,
) -> LoanSchedule {
    let instalments = s.instalments.into_iter().map(|i| Instalment {
        period: i.period,
        payment: i.payment,
        principal_part: i.principal_part,
        interest_part: i.interest_part,
        balance: i.balance,
    }).collect();
    LoanSchedule {
        instalments,
        total_payment: s.total_payment,
        total_interest: s.total_interest,
    }
}

fn make_params(principal: f64, annual_rate_pct: f64, months: u32) -> LoanParams {
    LoanParams { principal, annual_rate_pct, months }
}

/// Compute an equal-payment (annuity) amortization schedule.
#[uniffi::export]
pub fn amortize_equal_payment(
    principal: f64,
    annual_rate_pct: f64,
    months: u32,
) -> Result<LoanSchedule, FfiLoanError> {
    amortize(&make_params(principal, annual_rate_pct, months), repayment_method::EqualPayment)
        .map(to_ffi_schedule)
        .map_err(|e| FfiLoanError::Loan(e.to_string()))
}

/// Compute an equal-principal amortization schedule.
#[uniffi::export]
pub fn amortize_equal_principal(
    principal: f64,
    annual_rate_pct: f64,
    months: u32,
) -> Result<LoanSchedule, FfiLoanError> {
    amortize(&make_params(principal, annual_rate_pct, months), repayment_method::EqualPrincipal)
        .map(to_ffi_schedule)
        .map_err(|e| FfiLoanError::Loan(e.to_string()))
}

// ── version ───────────────────────────────────────────────────────────────────

/// Library version string.
#[uniffi::export]
pub fn calcrux_version() -> String {
    env!("CARGO_PKG_VERSION").to_string()
}

// ── tests ─────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn eval_addition() {
        assert_eq!(calc_eval("1+2".into(), false).unwrap(), "3");
    }

    #[test]
    fn eval_sin_90_deg() {
        let r: f64 = calc_eval("sin(90)".into(), true).unwrap().parse().unwrap();
        assert!((r - 1.0).abs() < 1e-10);
    }

    #[test]
    fn eval_error_propagates() {
        assert!(calc_eval("1/0".into(), false).is_err());
    }

    #[test]
    fn unit_categories_not_empty() {
        let cats = unit_categories();
        assert!(!cats.is_empty());
        assert!(cats.contains(&"length".to_string()));
    }

    #[test]
    fn unit_list_known_category() {
        let units = unit_list("length".into()).unwrap();
        assert!(units.contains(&"m".to_string()));
        assert!(units.contains(&"km".to_string()));
    }

    #[test]
    fn unit_list_unknown_category() {
        assert!(unit_list("energy".into()).is_none());
    }

    #[test]
    fn convert_unit_metres_to_km() {
        let r = convert_unit("length".into(), "m".into(), "km".into(), 1000.0).unwrap();
        assert!((r - 1.0).abs() < 1e-10);
    }

    #[test]
    fn loan_equal_payment_monthly() {
        let s = amortize_equal_payment(200_000.0, 4.5, 360).unwrap();
        assert!((s.instalments[0].payment - 1013.37).abs() < 0.01);
    }

    #[test]
    fn loan_equal_principal_decreasing() {
        let s = amortize_equal_principal(100_000.0, 5.0, 60).unwrap();
        assert!(s.instalments[1].payment < s.instalments[0].payment);
    }

    #[test]
    fn version_non_empty() {
        assert!(!calcrux_version().is_empty());
    }
}
