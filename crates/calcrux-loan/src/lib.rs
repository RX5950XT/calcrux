//! Loan amortization schedules.
//!
//! Supports two internationally-standard repayment methods:
//!
//! - **Equal Payment** (等額本息, "annuity") — fixed monthly payment;
//!   each instalment covers accrued interest first, then principal.
//!   `M = P × r × (1+r)^n / ((1+r)^n − 1)`
//!
//! - **Equal Principal** (等額本金) — fixed principal repayment each
//!   period; total payment decreases over time.
//!   `M_k = P/n + (P − P×(k−1)/n) × r`
//!
//! All calculations use `f64` which is sufficient for the ~6-decimal
//! precision displayed in a loan calculator UI.  No country-specific
//! rates (LPR, provident fund) are included; callers supply the rate.
//!
//! # Example
//! ```
//! use calcrux_loan::{LoanParams, repayment_method::EqualPayment, amortize};
//!
//! let params = LoanParams { principal: 200_000.0, annual_rate_pct: 4.5, months: 360 };
//! let schedule = amortize(&params, EqualPayment).unwrap();
//! assert_eq!(schedule.instalments.len(), 360);
//! println!("Monthly: {:.2}", schedule.instalments[0].payment);
//! ```

use thiserror::Error;

// ── error ─────────────────────────────────────────────────────────────────────

#[derive(Debug, Error, PartialEq)]
pub enum LoanError {
    #[error("principal must be positive")]
    InvalidPrincipal,
    #[error("annual rate must be non-negative")]
    InvalidRate,
    #[error("term must be at least 1 month")]
    InvalidTerm,
}

// ── public types ──────────────────────────────────────────────────────────────

/// Input parameters shared by both repayment methods.
#[derive(Debug, Clone, PartialEq)]
pub struct LoanParams {
    /// Loan principal (positive).
    pub principal: f64,
    /// Annual interest rate as a percentage (e.g. `4.5` for 4.5 %).
    pub annual_rate_pct: f64,
    /// Total term in months.
    pub months: u32,
}

/// One month's instalment detail.
#[derive(Debug, Clone, PartialEq)]
pub struct Instalment {
    /// 1-based period number.
    pub period: u32,
    /// Total amount paid this period (principal + interest).
    pub payment: f64,
    /// Principal portion of this period's payment.
    pub principal_part: f64,
    /// Interest portion of this period's payment.
    pub interest_part: f64,
    /// Remaining principal balance after this payment.
    pub balance: f64,
}

/// Full amortization schedule.
#[derive(Debug, Clone, PartialEq)]
pub struct Schedule {
    pub instalments: Vec<Instalment>,
    /// Sum of all payment amounts.
    pub total_payment: f64,
    /// Sum of all interest paid.
    pub total_interest: f64,
}

// ── repayment methods ─────────────────────────────────────────────────────────

pub mod repayment_method {
    /// Fixed monthly payment; interest-heavy at first.
    pub struct EqualPayment;
    /// Fixed principal per period; payment reduces each month.
    pub struct EqualPrincipal;
}

/// Trait implemented by each repayment method.
pub trait RepaymentMethod {
    fn build(
        &self,
        principal: f64,
        monthly_rate: f64,
        months: u32,
    ) -> Vec<Instalment>;
}

impl RepaymentMethod for repayment_method::EqualPayment {
    fn build(&self, principal: f64, r: f64, n: u32) -> Vec<Instalment> {
        if r == 0.0 {
            // Zero-interest: split principal equally.
            let p_part = principal / n as f64;
            let mut balance = principal;
            return (1..=n)
                .map(|k| {
                    balance -= p_part;
                    Instalment {
                        period: k,
                        payment: p_part,
                        principal_part: p_part,
                        interest_part: 0.0,
                        balance: balance.max(0.0),
                    }
                })
                .collect();
        }

        // M = P * r * (1+r)^n / ((1+r)^n - 1)
        let factor = (1.0 + r).powi(n as i32);
        let monthly = principal * r * factor / (factor - 1.0);

        let mut balance = principal;
        (1..=n)
            .map(|k| {
                let interest = balance * r;
                let principal_part = monthly - interest;
                balance -= principal_part;
                // Clamp last period floating-point dust.
                if k == n {
                    balance = 0.0;
                }
                Instalment {
                    period: k,
                    payment: monthly,
                    principal_part,
                    interest_part: interest,
                    balance: balance.max(0.0),
                }
            })
            .collect()
    }
}

impl RepaymentMethod for repayment_method::EqualPrincipal {
    fn build(&self, principal: f64, r: f64, n: u32) -> Vec<Instalment> {
        let p_part = principal / n as f64;
        let mut balance = principal;

        (1..=n)
            .map(|k| {
                let interest = balance * r;
                let payment = p_part + interest;
                balance -= p_part;
                if k == n {
                    balance = 0.0;
                }
                Instalment {
                    period: k,
                    payment,
                    principal_part: p_part,
                    interest_part: interest,
                    balance: balance.max(0.0),
                }
            })
            .collect()
    }
}

// ── public API ────────────────────────────────────────────────────────────────

/// Compute a full amortization schedule.
pub fn amortize<M: RepaymentMethod>(
    params: &LoanParams,
    method: M,
) -> Result<Schedule, LoanError> {
    if params.principal <= 0.0 {
        return Err(LoanError::InvalidPrincipal);
    }
    if params.annual_rate_pct < 0.0 {
        return Err(LoanError::InvalidRate);
    }
    if params.months == 0 {
        return Err(LoanError::InvalidTerm);
    }

    let monthly_rate = params.annual_rate_pct / 100.0 / 12.0;
    let instalments = method.build(params.principal, monthly_rate, params.months);

    let total_payment: f64 = instalments.iter().map(|i| i.payment).sum();
    let total_interest: f64 = instalments.iter().map(|i| i.interest_part).sum();

    Ok(Schedule { instalments, total_payment, total_interest })
}

// ── version ───────────────────────────────────────────────────────────────────

pub fn version() -> &'static str {
    env!("CARGO_PKG_VERSION")
}

// ── tests ─────────────────────────────────────────────────────────────────────

#[cfg(test)]
mod tests {
    use super::*;
    use repayment_method::{EqualPayment, EqualPrincipal};

    fn approx(a: f64, b: f64, tol: f64) -> bool {
        (a - b).abs() <= tol
    }

    // ── EqualPayment ──────────────────────────────────────────────────────────

    #[test]
    fn equal_payment_instalment_count() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 12 };
        let s = amortize(&p, EqualPayment).unwrap();
        assert_eq!(s.instalments.len(), 12);
    }

    #[test]
    fn equal_payment_constant_monthly() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 60 };
        let s = amortize(&p, EqualPayment).unwrap();
        let first = s.instalments[0].payment;
        for inst in &s.instalments {
            assert!(approx(inst.payment, first, 0.01), "payment should be constant");
        }
    }

    #[test]
    fn equal_payment_balance_reaches_zero() {
        let p = LoanParams { principal: 200_000.0, annual_rate_pct: 4.5, months: 360 };
        let s = amortize(&p, EqualPayment).unwrap();
        assert!(approx(s.instalments.last().unwrap().balance, 0.0, 1e-6));
    }

    #[test]
    fn equal_payment_total_equals_sum_of_parts() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 6.0, months: 120 };
        let s = amortize(&p, EqualPayment).unwrap();
        // total_payment ≈ principal + total_interest
        assert!(approx(s.total_payment, p.principal + s.total_interest, 1e-4));
    }

    /// Cross-check against a known-good result from Bankrate:
    /// $200,000 @ 4.5% / 30y → ~$1,013.37/month
    #[test]
    fn equal_payment_known_result() {
        let p = LoanParams { principal: 200_000.0, annual_rate_pct: 4.5, months: 360 };
        let s = amortize(&p, EqualPayment).unwrap();
        assert!(
            approx(s.instalments[0].payment, 1013.37, 0.01),
            "got {:.4}",
            s.instalments[0].payment
        );
    }

    #[test]
    fn equal_payment_zero_rate() {
        let p = LoanParams { principal: 12_000.0, annual_rate_pct: 0.0, months: 12 };
        let s = amortize(&p, EqualPayment).unwrap();
        assert!(approx(s.instalments[0].payment, 1000.0, 1e-9));
        assert!(approx(s.total_interest, 0.0, 1e-9));
    }

    // ── EqualPrincipal ────────────────────────────────────────────────────────

    #[test]
    fn equal_principal_instalment_count() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 12 };
        let s = amortize(&p, EqualPrincipal).unwrap();
        assert_eq!(s.instalments.len(), 12);
    }

    #[test]
    fn equal_principal_constant_principal_part() {
        let p = LoanParams { principal: 120_000.0, annual_rate_pct: 6.0, months: 12 };
        let s = amortize(&p, EqualPrincipal).unwrap();
        let pp = s.instalments[0].principal_part;
        for inst in &s.instalments {
            assert!(approx(inst.principal_part, pp, 1e-9));
        }
    }

    #[test]
    fn equal_principal_decreasing_payment() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 60 };
        let s = amortize(&p, EqualPrincipal).unwrap();
        for i in 1..s.instalments.len() {
            assert!(
                s.instalments[i].payment < s.instalments[i - 1].payment,
                "payment should decrease each month"
            );
        }
    }

    #[test]
    fn equal_principal_balance_reaches_zero() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 120 };
        let s = amortize(&p, EqualPrincipal).unwrap();
        assert!(approx(s.instalments.last().unwrap().balance, 0.0, 1e-6));
    }

    #[test]
    fn equal_principal_less_total_interest_than_equal_payment() {
        // Equal principal always pays less total interest than annuity.
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 120 };
        let ep = amortize(&p, EqualPayment).unwrap();
        let epp = amortize(&p, EqualPrincipal).unwrap();
        assert!(epp.total_interest < ep.total_interest);
    }

    // ── error cases ───────────────────────────────────────────────────────────

    #[test]
    fn error_zero_principal() {
        let p = LoanParams { principal: 0.0, annual_rate_pct: 5.0, months: 12 };
        assert_eq!(amortize(&p, EqualPayment), Err(LoanError::InvalidPrincipal));
    }

    #[test]
    fn error_negative_principal() {
        let p = LoanParams { principal: -1.0, annual_rate_pct: 5.0, months: 12 };
        assert_eq!(amortize(&p, EqualPrincipal), Err(LoanError::InvalidPrincipal));
    }

    #[test]
    fn error_negative_rate() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: -1.0, months: 12 };
        assert_eq!(amortize(&p, EqualPayment), Err(LoanError::InvalidRate));
    }

    #[test]
    fn error_zero_months() {
        let p = LoanParams { principal: 100_000.0, annual_rate_pct: 5.0, months: 0 };
        assert_eq!(amortize(&p, EqualPayment), Err(LoanError::InvalidTerm));
    }
}
