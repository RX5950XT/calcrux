//! Unified numeric type: exact rational fast-path + arbitrary-precision real fallback.
//!
//! Mirrors the AOSP `UnifiedReal + BoundedRational` design but uses pure-Rust
//! backends: [`malachite::Rational`] for exact rationals and
//! [`astro_float::BigFloat`] for irrational / transcendental values.

use std::cell::RefCell;
use std::fmt;

use astro_float::{BigFloat, Consts, RoundingMode};
use malachite::num::arithmetic::traits::Pow;
use malachite::num::basic::traits::{One, Zero};
use malachite::{Integer, Natural, Rational};

use crate::error::{EngineError, Result};

/// Default working precision in bits (≈ 77 decimal digits).
pub const DEFAULT_PREC: usize = 256;

/// Rounding mode shared across all BigFloat ops.
pub const ROUND: RoundingMode = RoundingMode::ToEven;

thread_local! {
    /// Transcendental-constant cache used by `BigFloat::parse` and
    /// evaluator transcendental ops. Constructed lazily per thread.
    pub(crate) static CONSTS: RefCell<Consts> =
        RefCell::new(Consts::new().expect("astro-float Consts init"));
}

pub(crate) fn with_consts<F, R>(f: F) -> R
where
    F: FnOnce(&mut Consts) -> R,
{
    CONSTS.with(|c| f(&mut c.borrow_mut()))
}

#[derive(Debug, Clone)]
pub enum Number {
    /// Exact rational. Preserved across +, −, ×, ÷ and integer powers.
    Rational(Rational),
    /// Arbitrary-precision real. Used once we hit a transcendental function,
    /// an irrational root, or a non-integer exponent.
    Real(BigFloat),
}

impl Number {
    pub fn zero() -> Self {
        Number::Rational(Rational::ZERO)
    }

    pub fn one() -> Self {
        Number::Rational(Rational::ONE)
    }

    pub fn from_i64(v: i64) -> Self {
        Number::Rational(Rational::from(v))
    }

    /// Parse a decimal literal (`"3.14"`, `".5"`, `"2e10"`, `"1.5E-3"`).
    /// Exact scientific-notation values round-trip through `Rational` so
    /// `0.1 + 0.2 == 0.3`.
    pub fn parse_decimal(src: &str) -> Result<Self> {
        // Split mantissa / exponent.
        let (mantissa, exp_sign, exp_abs) = split_exponent(src)?;

        // Parse mantissa into (digits, fraction_len).
        let (digits, frac_len) = parse_mantissa(mantissa)?;
        if digits.is_empty() {
            return Err(EngineError::InvalidNumber(src.into()));
        }

        let num_int: Integer = digits
            .parse()
            .map_err(|_| EngineError::InvalidNumber(src.into()))?;
        let mut rat = Rational::from(num_int);

        // Apply fraction (divide by 10^frac_len).
        if frac_len > 0 {
            let scale = Rational::from(Natural::from(10u32).pow(frac_len as u64));
            rat /= scale;
        }

        // Apply scientific exponent.
        if exp_abs > 0 {
            let scale = Rational::from(Natural::from(10u32).pow(exp_abs as u64));
            if exp_sign {
                rat /= scale;
            } else {
                rat *= scale;
            }
        }

        Ok(Number::Rational(rat))
    }

    pub fn is_zero(&self) -> bool {
        match self {
            Number::Rational(r) => *r == Rational::ZERO,
            Number::Real(f) => f.is_zero(),
        }
    }

    /// Promote to an arbitrary-precision real.
    pub fn to_real(&self, prec: usize) -> BigFloat {
        match self {
            Number::Real(f) => f.clone(),
            Number::Rational(r) => rational_to_bigfloat(r, prec),
        }
    }

    pub fn neg(self) -> Self {
        match self {
            Number::Rational(r) => Number::Rational(-r),
            Number::Real(f) => Number::Real(f.neg()),
        }
    }

    pub fn add(self, other: Self, prec: usize) -> Self {
        match (self, other) {
            (Number::Rational(a), Number::Rational(b)) => Number::Rational(a + b),
            (a, b) => Number::Real(a.to_real(prec).add(&b.to_real(prec), prec, ROUND)),
        }
    }

    pub fn sub(self, other: Self, prec: usize) -> Self {
        match (self, other) {
            (Number::Rational(a), Number::Rational(b)) => Number::Rational(a - b),
            (a, b) => Number::Real(a.to_real(prec).sub(&b.to_real(prec), prec, ROUND)),
        }
    }

    pub fn mul(self, other: Self, prec: usize) -> Self {
        match (self, other) {
            (Number::Rational(a), Number::Rational(b)) => Number::Rational(a * b),
            (a, b) => Number::Real(a.to_real(prec).mul(&b.to_real(prec), prec, ROUND)),
        }
    }

    pub fn div(self, other: Self, prec: usize) -> Result<Self> {
        if other.is_zero() {
            return Err(EngineError::DivisionByZero);
        }
        Ok(match (self, other) {
            (Number::Rational(a), Number::Rational(b)) => Number::Rational(a / b),
            (a, b) => Number::Real(a.to_real(prec).div(&b.to_real(prec), prec, ROUND)),
        })
    }

    /// Raise to a signed integer power while staying in `Rational`.
    /// Returns `None` if the exponent does not fit in `i64` or the base is
    /// already `Real` (caller should fall back to BigFloat `pow`).
    pub fn pow_int(&self, exp: i64) -> Option<Self> {
        let Number::Rational(base) = self else {
            return None;
        };
        if exp >= 0 {
            Some(Number::Rational(base.clone().pow(exp as u64)))
        } else if *base == Rational::ZERO {
            None
        } else {
            // base^(-n) = 1 / base^n  — use owned division to avoid reference semantics.
            let pos_pow = base.clone().pow((-exp) as u64);
            Some(Number::Rational(Rational::ONE / pos_pow))
        }
    }

    /// Convert to f64 (lossy; suitable for unit-conversion display precision).
    pub fn to_f64(&self) -> f64 {
        match self {
            Number::Rational(r) => {
                if let Ok(i) = i128::try_from(r) {
                    return i as f64;
                }
                let s = r.to_string();
                if let Some((n, d)) = s.split_once('/') {
                    let nf: f64 = n.parse().unwrap_or(f64::NAN);
                    let df: f64 = d.parse().unwrap_or(f64::NAN);
                    nf / df
                } else {
                    s.parse().unwrap_or(f64::NAN)
                }
            }
            Number::Real(f) => {
                let raw = format!("{f}");
                match raw.as_str() {
                    "NaN" => f64::NAN,
                    "Inf" => f64::INFINITY,
                    "-Inf" => f64::NEG_INFINITY,
                    _ => raw.parse().unwrap_or(f64::NAN),
                }
            }
        }
    }

    /// Format as a decimal string (up to `digits` significant digits).
    pub fn to_decimal_string(&self, digits: usize) -> String {
        match self {
            Number::Rational(r) => format_rational(r, digits),
            Number::Real(f) => format_bigfloat(f, digits),
        }
    }
}

// --- helpers ---------------------------------------------------------------

fn split_exponent(src: &str) -> Result<(&str, bool, u64)> {
    if let Some(idx) = src.find(['e', 'E']) {
        let (mant, exp) = src.split_at(idx);
        let chars = exp[1..].chars();
        let (neg, rest) = match chars.clone().next() {
            Some('+') => (false, &exp[2..]),
            Some('-') => (true, &exp[2..]),
            _ => (false, &exp[1..]),
        };
        let abs: u64 = rest
            .parse()
            .map_err(|_| EngineError::InvalidNumber(src.into()))?;
        Ok((mant, neg, abs))
    } else {
        Ok((src, false, 0))
    }
}

fn parse_mantissa(s: &str) -> Result<(String, usize)> {
    if let Some(dot) = s.find('.') {
        let mut digits = String::new();
        digits.push_str(&s[..dot]);
        digits.push_str(&s[dot + 1..]);
        let frac_len = s.len() - dot - 1;
        if digits.is_empty() {
            return Err(EngineError::InvalidNumber(s.into()));
        }
        // Handle ".5" → digits "5", frac_len 1 (leading empty is fine).
        Ok((digits, frac_len))
    } else {
        Ok((s.to_string(), 0))
    }
}

fn rational_to_bigfloat(r: &Rational, prec: usize) -> BigFloat {
    let s = r.to_string();
    if let Some((num, den)) = s.split_once('/') {
        let n = parse_bigfloat(num, prec);
        let d = parse_bigfloat(den, prec);
        n.div(&d, prec, ROUND)
    } else {
        parse_bigfloat(&s, prec)
    }
}

fn parse_bigfloat(s: &str, prec: usize) -> BigFloat {
    with_consts(|c| BigFloat::parse(s, astro_float::Radix::Dec, prec, ROUND, c))
}

fn format_rational(r: &Rational, digits: usize) -> String {
    // Integer fast-path.
    if let Ok(i) = i128::try_from(r) {
        return i.to_string();
    }
    let bf = rational_to_bigfloat(r, digits.saturating_mul(4).max(DEFAULT_PREC));
    format_bigfloat(&bf, digits)
}

fn format_bigfloat(f: &BigFloat, max_sig_digits: usize) -> String {
    let raw = format!("{f}");
    // astro-float special markers round-trip as-is.
    match raw.as_str() {
        "NaN" | "Inf" | "-Inf" => return raw,
        _ => {}
    }
    if f.is_zero() {
        return "0".to_string();
    }

    // `raw` is always in scientific form like "4.2e+1", "-5.67e-3", "1e+0".
    let (neg, rest) = match raw.strip_prefix('-') {
        Some(r) => (true, r.to_string()),
        None => (false, raw),
    };
    let (mantissa_str, exp) = match rest.split_once(['e', 'E']) {
        Some((m, e)) => (m.to_string(), e.parse::<i64>().unwrap_or(0)),
        None => (rest, 0),
    };

    // Split mantissa into integer + fractional pieces and concatenate the
    // digits (we'll re-place the decimal point using `exp`).
    let (int_part, frac_part) = match mantissa_str.split_once('.') {
        Some((i, f)) => (i.to_string(), f.to_string()),
        None => (mantissa_str.clone(), String::new()),
    };
    let mut digits: String = format!("{int_part}{frac_part}");

    // Cap to `max_sig_digits` with banker's-ish rounding (round half to even).
    if digits.len() > max_sig_digits {
        digits = round_digits(&digits, max_sig_digits);
    }

    let current_point = int_part.len() as i64;
    let mut new_point = current_point + exp;
    // Rounding may have prepended a leading '1' (e.g. 9.99 → 10.0); if the
    // digit count grew, shift the decimal point right by one.
    if digits.len() as i64 > (int_part.len() + frac_part.len()) as i64 {
        new_point += 1;
    }

    let mut out = String::new();
    if neg {
        out.push('-');
    }
    if new_point <= 0 {
        out.push_str("0.");
        for _ in 0..(-new_point) {
            out.push('0');
        }
        out.push_str(&digits);
    } else if (new_point as usize) >= digits.len() {
        out.push_str(&digits);
        for _ in digits.len()..(new_point as usize) {
            out.push('0');
        }
    } else {
        out.push_str(&digits[..new_point as usize]);
        out.push('.');
        out.push_str(&digits[new_point as usize..]);
    }
    if out.contains('.') {
        while out.ends_with('0') {
            out.pop();
        }
        if out.ends_with('.') {
            out.pop();
        }
    }
    if out.is_empty() || out == "-" {
        out = "0".to_string();
    }
    out
}

fn round_digits(digits: &str, keep: usize) -> String {
    debug_assert!(digits.len() > keep);
    let (head, tail) = digits.split_at(keep);
    let mut buf: Vec<u8> = head.as_bytes().to_vec();
    let round_up = match tail.bytes().next().unwrap_or(b'0') {
        b'5'..=b'9' => true,
        _ => false,
    };
    if round_up {
        let mut i = buf.len();
        loop {
            if i == 0 {
                buf.insert(0, b'1');
                break;
            }
            i -= 1;
            if buf[i] == b'9' {
                buf[i] = b'0';
            } else {
                buf[i] += 1;
                break;
            }
        }
    }
    String::from_utf8(buf).expect("ascii digits")
}

impl fmt::Display for Number {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "{}", self.to_decimal_string(18))
    }
}

impl PartialEq for Number {
    fn eq(&self, other: &Self) -> bool {
        match (self, other) {
            (Number::Rational(a), Number::Rational(b)) => a == b,
            // For real comparisons we require bit-exact equality of the
            // BigFloat representation at the current precision. A semantic
            // "approximately equal" helper lives in tests.
            (Number::Real(a), Number::Real(b)) => format!("{a}") == format!("{b}"),
            _ => false,
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;

    fn r(n: i64, d: i64) -> Number {
        Number::Rational(Rational::from_signeds(n, d))
    }

    #[test]
    fn parse_integer() {
        let n = Number::parse_decimal("42").unwrap();
        assert_eq!(n, Number::from_i64(42));
    }

    #[test]
    fn parse_decimal_point() {
        let n = Number::parse_decimal("3.14").unwrap();
        assert_eq!(n, r(314, 100));
    }

    #[test]
    fn parse_leading_dot() {
        let n = Number::parse_decimal(".5").unwrap();
        assert_eq!(n, r(1, 2));
    }

    #[test]
    fn parse_scientific_pos() {
        let n = Number::parse_decimal("1.5e3").unwrap();
        assert_eq!(n, Number::from_i64(1500));
    }

    #[test]
    fn parse_scientific_neg() {
        let n = Number::parse_decimal("25e-2").unwrap();
        assert_eq!(n, r(1, 4));
    }

    #[test]
    fn third_times_three_is_one() {
        // Fast-path rational: should be *exactly* one, no floating epsilon.
        let third = Number::one().div(Number::from_i64(3), DEFAULT_PREC).unwrap();
        let product = third.mul(Number::from_i64(3), DEFAULT_PREC);
        assert_eq!(product, Number::one());
    }

    #[test]
    fn add_sub_mul_div_rational_closed() {
        let a = Number::parse_decimal("0.1").unwrap();
        let b = Number::parse_decimal("0.2").unwrap();
        let c = a.add(b, DEFAULT_PREC);
        assert_eq!(c, Number::parse_decimal("0.3").unwrap());
    }

    #[test]
    fn division_by_zero() {
        let err = Number::one().div(Number::zero(), DEFAULT_PREC).unwrap_err();
        assert_eq!(err, EngineError::DivisionByZero);
    }

    #[test]
    fn integer_power_positive() {
        let two = Number::from_i64(2);
        assert_eq!(two.pow_int(10).unwrap(), Number::from_i64(1024));
    }

    #[test]
    fn integer_power_negative() {
        let two = Number::from_i64(2);
        assert_eq!(two.pow_int(-3).unwrap(), r(1, 8));
    }

    #[test]
    fn negate() {
        assert_eq!(Number::from_i64(5).neg(), Number::from_i64(-5));
    }

    #[test]
    fn display_integer() {
        assert_eq!(Number::from_i64(42).to_string(), "42");
    }

    #[test]
    fn display_rational() {
        let half = r(1, 2);
        assert_eq!(half.to_string(), "0.5");
    }

    #[test]
    fn to_real_roundtrip() {
        let r = Number::from_i64(42);
        let real = Number::Real(r.to_real(DEFAULT_PREC));
        // Our formatter must normalise "4.2e+1" → "42".
        assert_eq!(real.to_decimal_string(18), "42");
    }
}
