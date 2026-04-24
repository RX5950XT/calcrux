//! Expression evaluator.
//!
//! Walks the AST from `parser::Expr` and produces a `Number` result.
//! Transcendental functions are computed by `astro-float` at a configurable
//! working precision (default 256 bits ≈ 77 decimal digits).

use astro_float::BigFloat;

use crate::error::{EngineError, Result};
use crate::number::{with_consts, Number, DEFAULT_PREC, ROUND};
use crate::parser::{BinOp, Expr, UnaryFn};

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub enum AngleMode {
    #[default]
    Radians,
    Degrees,
}

#[derive(Debug, Clone)]
pub struct Evaluator {
    pub angle_mode: AngleMode,
    pub precision: usize,
}

impl Default for Evaluator {
    fn default() -> Self {
        Evaluator {
            angle_mode: AngleMode::Radians,
            precision: DEFAULT_PREC,
        }
    }
}

impl Evaluator {
    pub fn new(angle_mode: AngleMode) -> Self {
        Evaluator { angle_mode, ..Default::default() }
    }

    /// Evaluate a pre-parsed expression.
    pub fn eval(&self, expr: &Expr) -> Result<Number> {
        match expr {
            Expr::Number(s) => Number::parse_decimal(s),

            Expr::Pi => Ok(Number::Real(self.pi())),
            Expr::Euler => Ok(Number::Real(self.euler_e())),

            Expr::Negate(e) => Ok(self.eval(e)?.neg()),

            Expr::BinOp(l, op, r) => {
                let lv = self.eval(l)?;
                let rv = self.eval(r)?;
                match op {
                    BinOp::Add => Ok(lv.add(rv, self.precision)),
                    BinOp::Sub => Ok(lv.sub(rv, self.precision)),
                    BinOp::Mul => Ok(lv.mul(rv, self.precision)),
                    BinOp::Div => lv.div(rv, self.precision),
                }
            }

            Expr::Pow(base, exp) => self.eval_pow(base, exp),

            Expr::Factorial(e) => self.eval_factorial(e),

            Expr::Percent(e) => {
                // Percent: x% = x / 100
                let v = self.eval(e)?;
                v.div(Number::from_i64(100), self.precision)
            }

            Expr::UnaryFn(func, arg) => self.eval_fn(*func, arg),
        }
    }

    /// Parse `src` string and evaluate it.
    pub fn eval_str(&self, src: &str) -> Result<Number> {
        let expr = crate::parser::parse(src)?;
        self.eval(&expr)
    }

    // --- power -----------------------------------------------------------

    fn eval_pow(&self, base_expr: &Expr, exp_expr: &Expr) -> Result<Number> {
        let base = self.eval(base_expr)?;
        let exp = self.eval(exp_expr)?;

        // Try integer exponent fast-path (stays Rational).
        if let Number::Rational(ref r) = exp {
            if let Some(n) = r.try_i64() {
                if let Some(result) = base.pow_int(n) {
                    return Ok(result);
                }
                // base is Real or exponent is too big for integer power.
            }
        }

        // General case: b^e = exp(e * ln(b)).
        let p = self.precision;
        let base_f = base.to_real(p);
        let exp_f = exp.to_real(p);

        if base_f.is_negative() && !is_integer_bigfloat(&exp_f, p) {
            return Err(EngineError::Domain("base must be non-negative for non-integer exponent"));
        }

        // Rational exponent optimisation: x^(n/d) via integer power then n-th root.
        // Avoids the slow BigFloat::pow(BigFloat) path for common cases.
        let result = if let Number::Rational(ref r) = exp {
            let n = r.numerator_ref();
            let d = r.denominator_ref();
            let two = malachite::Natural::from(2u32);
            // x^(1/2) → sqrt(x)
            if *n == malachite::Natural::from(1u32) && *d == two {
                base_f.sqrt(p, ROUND)
            } else if *d == malachite::Natural::from(1u32) {
                // Integer exponent that didn't fit i64 — fall through to general pow.
                with_consts(|cc| base_f.pow(&exp_f, p, ROUND, cc))
            } else {
                // General rational exponent: exp(e * ln(base)).
                let ln_base = with_consts(|cc| base_f.ln(p, ROUND, cc));
                let product = exp_f.mul(&ln_base, p, ROUND);
                with_consts(|cc| product.exp(p, ROUND, cc))
            }
        } else {
            let ln_base = with_consts(|cc| base_f.ln(p, ROUND, cc));
            let product = exp_f.mul(&ln_base, p, ROUND);
            with_consts(|cc| product.exp(p, ROUND, cc))
        };
        Ok(Number::Real(result))
    }

    // --- factorial -------------------------------------------------------

    fn eval_factorial(&self, e: &Expr) -> Result<Number> {
        let n = self.eval(e)?;
        let n_i64 = match &n {
            Number::Rational(r) => r
                .try_i64()
                .ok_or(EngineError::Domain("factorial requires a non-negative integer"))?,
            Number::Real(f) => {
                if !is_integer_bigfloat(f, self.precision) {
                    return Err(EngineError::Domain("factorial requires a non-negative integer"));
                }
                real_to_i64(f)?
            }
        };
        if n_i64 < 0 {
            return Err(EngineError::Domain("factorial requires a non-negative integer"));
        }
        if n_i64 > 10_000 {
            return Err(EngineError::Overflow);
        }
        // Iterative factorial, Rational (exact).
        let mut acc = malachite::Rational::from(1i64);
        for i in 2..=(n_i64 as u64) {
            acc *= malachite::Rational::from(i);
        }
        Ok(Number::Rational(acc))
    }

    // --- transcendentals -------------------------------------------------

    fn eval_fn(&self, func: UnaryFn, arg: &Expr) -> Result<Number> {
        let v = self.eval(arg)?;
        let p = self.precision;

        match func {
            UnaryFn::Sqrt => {
                if v.is_negative_real() {
                    return Err(EngineError::Domain("sqrt of negative number"));
                }
                let f = v.to_real(p);
                let r = f.sqrt(p, ROUND);
                Ok(Number::Real(r))
            }

            UnaryFn::Abs => {
                Ok(match v {
                    Number::Rational(r) => {
                        use malachite::num::arithmetic::traits::Abs;
                        Number::Rational(r.abs())
                    }
                    Number::Real(f) => Number::Real(f.abs()),
                })
            }

            UnaryFn::Sin | UnaryFn::Cos | UnaryFn::Tan => {
                let angle = self.to_radians(v, p);
                let r = match func {
                    UnaryFn::Sin => with_consts(|cc| angle.sin(p, ROUND, cc)),
                    UnaryFn::Cos => with_consts(|cc| angle.cos(p, ROUND, cc)),
                    UnaryFn::Tan => {
                        let s = with_consts(|cc| angle.sin(p, ROUND, cc));
                        let c = with_consts(|cc| angle.cos(p, ROUND, cc));
                        if c.is_zero() {
                            return Err(EngineError::Domain("tan undefined at this angle"));
                        }
                        s.div(&c, p, ROUND)
                    }
                    _ => unreachable!(),
                };
                Ok(Number::Real(r))
            }

            UnaryFn::Asin | UnaryFn::Acos | UnaryFn::Atan => {
                let f = v.to_real(p);
                let r = match func {
                    UnaryFn::Asin => {
                        if !in_neg1_pos1(&f, p) {
                            return Err(EngineError::Domain("asin domain is [-1, 1]"));
                        }
                        with_consts(|cc| f.asin(p, ROUND, cc))
                    }
                    UnaryFn::Acos => {
                        if !in_neg1_pos1(&f, p) {
                            return Err(EngineError::Domain("acos domain is [-1, 1]"));
                        }
                        with_consts(|cc| f.acos(p, ROUND, cc))
                    }
                    UnaryFn::Atan => with_consts(|cc| f.atan(p, ROUND, cc)),
                    _ => unreachable!(),
                };
                let result = self.from_radians(Number::Real(r), p);
                Ok(result)
            }

            UnaryFn::Ln => {
                let f = v.to_real(p);
                if f.is_negative() || f.is_zero() {
                    return Err(EngineError::Domain("ln domain is (0, ∞)"));
                }
                let r = with_consts(|cc| f.ln(p, ROUND, cc));
                Ok(Number::Real(r))
            }

            UnaryFn::Log => {
                let f = v.to_real(p);
                if f.is_negative() || f.is_zero() {
                    return Err(EngineError::Domain("log domain is (0, ∞)"));
                }
                let r = with_consts(|cc| f.log2(p, ROUND, cc));
                // log₁₀(x) = log₂(x) / log₂(10)
                let log2_10 = with_consts(|cc| {
                    BigFloat::from_i128(10, p).log2(p, ROUND, cc)
                });
                Ok(Number::Real(r.div(&log2_10, p, ROUND)))
            }

            UnaryFn::Exp => {
                let f = v.to_real(p);
                let r = with_consts(|cc| f.exp(p, ROUND, cc));
                Ok(Number::Real(r))
            }
        }
    }

    // --- angle helpers ---------------------------------------------------

    /// Convert `v` to radians if angle_mode is Degrees.
    fn to_radians(&self, v: Number, prec: usize) -> BigFloat {
        let f = v.to_real(prec);
        if self.angle_mode == AngleMode::Radians {
            return f;
        }
        // degrees * π / 180
        let pi = self.pi();
        let scale = pi.div(&BigFloat::from_i128(180, prec), prec, ROUND);
        f.mul(&scale, prec, ROUND)
    }

    /// Convert a radian result back to degrees if angle_mode is Degrees.
    fn from_radians(&self, v: Number, prec: usize) -> Number {
        if self.angle_mode == AngleMode::Radians {
            return v;
        }
        let f = v.to_real(prec);
        let pi = self.pi();
        let scale = BigFloat::from_i128(180, prec).div(&pi, prec, ROUND);
        Number::Real(f.mul(&scale, prec, ROUND))
    }

    fn pi(&self) -> BigFloat {
        with_consts(|cc| cc.pi(self.precision, ROUND))
    }

    fn euler_e(&self) -> BigFloat {
        let one = BigFloat::from_i128(1, self.precision);
        with_consts(|cc| one.exp(self.precision, ROUND, cc))
    }
}

// --- small helpers -------------------------------------------------------

fn is_integer_bigfloat(f: &BigFloat, prec: usize) -> bool {
    if f.is_nan() || f.is_inf() {
        return false;
    }
    let floored = f.floor();
    let diff = f.sub(&floored, prec, ROUND);
    diff.is_zero()
}

fn real_to_i64(f: &BigFloat) -> Result<i64> {
    let s = format!("{f}");
    // astro-float scientific: "1e+2", parse mantissa * 10^exp.
    let (mantissa, exp) = match s.split_once(['e', 'E']) {
        Some((m, e)) => (m, e.parse::<i64>().unwrap_or(0)),
        None => (s.as_str(), 0),
    };
    let base: f64 = mantissa.parse().map_err(|_| EngineError::Overflow)?;
    let val = base * 10_f64.powi(exp as i32);
    if val < i64::MIN as f64 || val > i64::MAX as f64 {
        return Err(EngineError::Overflow);
    }
    Ok(val as i64)
}

fn in_neg1_pos1(f: &BigFloat, p: usize) -> bool {
    let pos1 = BigFloat::from_i128(1, p);
    let neg1 = BigFloat::from_i128(-1, p);
    // cmp returns Some(positive) when self > other, Some(0) equal, Some(negative) less, None for NaN.
    matches!(f.cmp(&neg1), Some(n) if n >= 0)
        && matches!(f.cmp(&pos1), Some(n) if n <= 0)
}

trait IsNegativeReal {
    fn is_negative_real(&self) -> bool;
}

impl IsNegativeReal for Number {
    fn is_negative_real(&self) -> bool {
        match self {
            Number::Rational(r) => *r < malachite::Rational::from(0i64),
            Number::Real(f) => f.is_negative(),
        }
    }
}

trait TryI64 {
    fn try_i64(&self) -> Option<i64>;
}

impl TryI64 for malachite::Rational {
    fn try_i64(&self) -> Option<i64> {
        if *self.denominator_ref() != malachite::Natural::from(1u32) {
            return None;
        }
        // numerator_ref() gives the unsigned magnitude; apply sign separately.
        let magnitude = i64::try_from(self.numerator_ref()).ok()?;
        if *self < malachite::Rational::from(0i64) {
            magnitude.checked_neg()
        } else {
            Some(magnitude)
        }
    }
}

#[cfg(test)]
mod tests {
    use super::*;
    use crate::number::DEFAULT_PREC;

    fn eval(s: &str) -> Number {
        Evaluator::default().eval_str(s).expect(s)
    }

    fn eval_deg(s: &str) -> Number {
        Evaluator::new(AngleMode::Degrees).eval_str(s).expect(s)
    }

    /// Assert that two Numbers are within `tol` of each other.
    fn approx_eq(a: &Number, b: &Number, tol: &str) {
        let a_f = a.to_real(DEFAULT_PREC);
        let b_f = b.to_real(DEFAULT_PREC);
        let tol_f = BigFloat::parse(tol, astro_float::Radix::Dec, DEFAULT_PREC, ROUND,
            &mut astro_float::Consts::new().unwrap());
        let diff = a_f.sub(&b_f, DEFAULT_PREC, ROUND);
        let abs_diff = diff.abs();
        assert!(
            matches!(abs_diff.cmp(&tol_f), Some(n) if n < 0),
            "expected |{a} − {b}| < {tol}, got {abs_diff:?}"
        );
    }

    // --- basic arithmetic ------------------------------------------------

    #[test]
    fn add() {
        assert_eq!(eval("1+2"), Number::from_i64(3));
    }

    #[test]
    fn sub() {
        assert_eq!(eval("5-3"), Number::from_i64(2));
    }

    #[test]
    fn mul() {
        assert_eq!(eval("3*4"), Number::from_i64(12));
    }

    #[test]
    fn div() {
        assert_eq!(eval("10/4"), Number::parse_decimal("2.5").unwrap());
    }

    #[test]
    fn third_times_three_is_exactly_one() {
        // The hallmark precision test: must be exact, not approximately 1.
        let r = eval("1/3*3");
        assert_eq!(r, Number::one());
    }

    #[test]
    fn point_one_plus_point_two_is_point_three() {
        assert_eq!(eval("0.1+0.2"), Number::parse_decimal("0.3").unwrap());
    }

    #[test]
    fn unary_neg() {
        assert_eq!(eval("-7"), Number::from_i64(-7));
    }

    #[test]
    fn nested_neg() {
        assert_eq!(eval("--7"), Number::from_i64(7));
    }

    #[test]
    fn implicit_mul_pi() {
        // 2π should equal 2 * π (Real).
        let a = eval("2π");
        let b = eval("2*π");
        approx_eq(&a, &b, "1e-50");
    }

    // --- power -----------------------------------------------------------

    #[test]
    fn integer_power() {
        assert_eq!(eval("2^10"), Number::from_i64(1024));
    }

    #[test]
    fn negative_power() {
        assert_eq!(eval("2^-3"), Number::parse_decimal("0.125").unwrap());
    }

    #[test]
    fn fractional_power_sqrt() {
        // 4^0.5 = 2
        let r = eval("4^0.5");
        approx_eq(&r, &Number::from_i64(2), "1e-70");
    }

    #[test]
    fn right_assoc_power() {
        // 2^2^3 = 2^(2^3) = 2^8 = 256
        assert_eq!(eval("2^2^3"), Number::from_i64(256));
    }

    // --- factorial -------------------------------------------------------

    #[test]
    fn factorial_5() {
        assert_eq!(eval("5!"), Number::from_i64(120));
    }

    #[test]
    fn factorial_0() {
        assert_eq!(eval("0!"), Number::from_i64(1));
    }

    #[test]
    fn factorial_10() {
        assert_eq!(eval("10!"), Number::from_i64(3628800));
    }

    // --- percent ---------------------------------------------------------

    #[test]
    fn percent_50() {
        assert_eq!(eval("50%"), Number::parse_decimal("0.5").unwrap());
    }

    // --- sqrt ------------------------------------------------------------

    #[test]
    fn sqrt_4() {
        let r = eval("√4");
        approx_eq(&r, &Number::from_i64(2), "1e-70");
    }

    #[test]
    fn sqrt_2_precision() {
        // √2 should agree to many digits: known value ≈ 1.41421356237...
        let r = eval("√2");
        let expected = Number::parse_decimal("1.41421356237309504880168872420969807856967187537694").unwrap();
        approx_eq(&r, &expected, "1e-48");
    }

    #[test]
    fn sqrt_negative_error() {
        let err = Evaluator::default().eval_str("√(-1)").unwrap_err();
        assert!(matches!(err, EngineError::Domain(_)));
    }

    // --- trig (radians) --------------------------------------------------

    #[test]
    fn sin_zero() {
        let r = eval("sin(0)");
        approx_eq(&r, &Number::zero(), "1e-70");
    }

    #[test]
    fn cos_zero() {
        let r = eval("cos(0)");
        approx_eq(&r, &Number::one(), "1e-70");
    }

    #[test]
    fn sin_pi_near_zero() {
        // sin(π) is not exactly 0 but must be < 1e-70 at 256-bit precision.
        let r = eval("sin(π)");
        approx_eq(&r, &Number::zero(), "1e-50");
    }

    #[test]
    fn cos_pi() {
        let r = eval("cos(π)");
        approx_eq(&r, &Number::from_i64(-1), "1e-70");
    }

    // --- trig (degrees) --------------------------------------------------

    #[test]
    fn sin_90_deg() {
        let r = eval_deg("sin(90)");
        approx_eq(&r, &Number::one(), "1e-70");
    }

    #[test]
    fn cos_180_deg() {
        let r = eval_deg("cos(180)");
        approx_eq(&r, &Number::from_i64(-1), "1e-70");
    }

    #[test]
    fn asin_1_deg() {
        // asin(1) in degrees should be 90.
        let r = eval_deg("asin(1)");
        approx_eq(&r, &Number::from_i64(90), "1e-50");
    }

    // --- logarithms ------------------------------------------------------

    #[test]
    fn ln_e() {
        let r = eval("ln(e)");
        approx_eq(&r, &Number::one(), "1e-70");
    }

    #[test]
    fn ln_1() {
        let r = eval("ln(1)");
        approx_eq(&r, &Number::zero(), "1e-70");
    }

    #[test]
    fn log_100() {
        let r = eval("log(100)");
        approx_eq(&r, &Number::from_i64(2), "1e-70");
    }

    #[test]
    fn log_1000() {
        let r = eval("log(1000)");
        approx_eq(&r, &Number::from_i64(3), "1e-70");
    }

    // --- precision / big numbers -----------------------------------------

    #[test]
    fn large_number_no_overflow() {
        // (1e100 + 1) − 1e100 should equal 1 (no cancellation) at 256-bit.
        let r = eval("(1e100+1)-1e100");
        assert_eq!(r, Number::one());
    }

    #[test]
    fn division_by_zero_error() {
        let err = Evaluator::default().eval_str("1/0").unwrap_err();
        assert_eq!(err, EngineError::DivisionByZero);
    }
}
