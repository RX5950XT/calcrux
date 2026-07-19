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

            Expr::BinOp(l, op, r) => self.eval_binop(l, *op, r),

            Expr::Pow(base, exp) => self.eval_pow(base, exp),

            Expr::Factorial(e) => self.eval_factorial(e),

            Expr::Percent(e) => {
                // Bare / postfix percent: x% = x / 100
                let v = self.eval(e)?;
                v.div(Number::from_i64(100), self.precision)
            }

            Expr::UnaryFn(func, arg) => self.eval_fn(*func, arg),
        }
    }

    /// Binary ops, with phone-calculator percent forms:
    /// `a + b%` → `a × (1 + b/100)`, `a - b%` → `a × (1 − b/100)`,
    /// `a × b%` → `a × (b/100)`, `a ÷ b%` → `a ÷ (b/100)`.
    fn eval_binop(&self, l: &Expr, op: BinOp, r: &Expr) -> Result<Number> {
        if let Expr::Percent(inner) = r {
            let base = self.eval(l)?;
            let pct = self.eval(inner)?; // the number before `%`
            let hundred = Number::from_i64(100);
            let fraction = pct.div(hundred, self.precision)?;
            return match op {
                BinOp::Add => {
                    // base + base*(pct/100)
                    let delta = base.clone().mul(fraction, self.precision);
                    Ok(base.add(delta, self.precision))
                }
                BinOp::Sub => {
                    let delta = base.clone().mul(fraction, self.precision);
                    Ok(base.sub(delta, self.precision))
                }
                BinOp::Mul => Ok(base.mul(fraction, self.precision)),
                BinOp::Div => base.div(fraction, self.precision),
            };
        }

        let lv = self.eval(l)?;
        let rv = self.eval(r)?;
        match op {
            BinOp::Add => Ok(lv.add(rv, self.precision)),
            BinOp::Sub => Ok(lv.sub(rv, self.precision)),
            BinOp::Mul => Ok(lv.mul(rv, self.precision)),
            BinOp::Div => lv.div(rv, self.precision),
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
        let p = self.precision;

        // 0^e: positive → 0, zero → 1 (convention), negative → division by zero.
        if base.is_zero() {
            return match exp_sign(&exp) {
                ExpSign::Positive => Ok(Number::zero()),
                ExpSign::Zero => Ok(Number::one()),
                ExpSign::Negative => Err(EngineError::DivisionByZero),
                ExpSign::Unknown => Err(EngineError::Domain("undefined power of zero")),
            };
        }

        // Try integer exponent fast-path (stays Rational).
        if let Number::Rational(ref r) = exp {
            if let Some(n) = r.try_i64() {
                if let Some(result) = base.pow_int(n) {
                    return Ok(result);
                }
                // base is Real or exponent is too big for integer power.
            }
        }

        let base_f = base.to_real(p);
        let exp_f = exp.to_real(p);

        // Negative base: only defined for integer exp (handled above) or
        // rational exp with odd denominator (real q-th root exists).
        if base_f.is_negative() {
            return self.eval_pow_negative_base(base_f, &exp, exp_f, p);
        }

        // Rational exponent optimisation: x^(n/d) via integer power then n-th root.
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

    /// `base < 0`, non-integer exponent path.
    fn eval_pow_negative_base(
        &self,
        base_f: BigFloat,
        exp: &Number,
        exp_f: BigFloat,
        p: usize,
    ) -> Result<Number> {
        let Number::Rational(r) = exp else {
            return Err(EngineError::Domain(
                "base must be non-negative for non-rational exponent",
            ));
        };
        if is_integer_bigfloat(&exp_f, p) {
            // Large integer that missed i64 fast-path.
            let result = with_consts(|cc| base_f.pow(&exp_f, p, ROUND, cc));
            return Ok(Number::Real(result));
        }

        let num = r.numerator_ref();
        let den = r.denominator_ref();
        // Reduced p/q: real q-th root of negative exists iff q is odd.
        if !natural_is_odd(den) {
            return Err(EngineError::Domain(
                "base must be non-negative for even-root exponent",
            ));
        }

        let abs_base = base_f.abs();
        let abs_exp = exp_f.abs();
        // |base|^|exp| via exp(|exp| * ln(|base|))
        let ln_abs = with_consts(|cc| abs_base.ln(p, ROUND, cc));
        let product = abs_exp.mul(&ln_abs, p, ROUND);
        let mut mag = with_consts(|cc| product.exp(p, ROUND, cc));

        // x^(a/b) for x<0, b odd: sign is negative iff numerator a is odd.
        if natural_is_odd(num) {
            mag = mag.neg();
        }
        // Negative exponent → reciprocal (sign already applied).
        if *r < malachite::Rational::from(0i64) {
            if mag.is_zero() {
                return Err(EngineError::DivisionByZero);
            }
            let one = BigFloat::from_i128(1, p);
            mag = one.div(&mag, p, ROUND);
        }
        Ok(Number::Real(mag))
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
                        // Near odd multiples of π/2, cos underflows to ~1e-76 rather
                        // than exact 0 — treat as a pole.
                        if cos_near_zero(&c, p) {
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

/// `|cos|` smaller than ~10^(−prec/4) counts as a tan pole (covers π/2 noise).
fn cos_near_zero(c: &BigFloat, prec: usize) -> bool {
    if c.is_zero() {
        return true;
    }
    let abs = c.abs();
    // 10^(−prec/4): at 256-bit ≈ 1e-19 — well above cos(π/2)≈1e-77 noise,
    // well below legitimate tan(89.999°) still useful on a calculator.
    let exp = -((prec / 4) as i32).max(12);
    let thresh = BigFloat::from_f64(10f64.powi(exp), prec);
    matches!(abs.cmp(&thresh), Some(n) if n <= 0)
}

fn natural_is_odd(n: &malachite::Natural) -> bool {
    // Least significant bit of an odd natural is 1.
    n.to_limbs_asc().first().map(|w| w & 1 == 1).unwrap_or(false)
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
enum ExpSign {
    Positive,
    Zero,
    Negative,
    Unknown,
}

fn exp_sign(exp: &Number) -> ExpSign {
    match exp {
        Number::Rational(r) => {
            if *r == malachite::Rational::from(0i64) {
                ExpSign::Zero
            } else if *r > malachite::Rational::from(0i64) {
                ExpSign::Positive
            } else {
                ExpSign::Negative
            }
        }
        Number::Real(f) => {
            if f.is_nan() || f.is_inf() {
                ExpSign::Unknown
            } else if f.is_zero() {
                ExpSign::Zero
            } else if f.is_negative() {
                ExpSign::Negative
            } else {
                ExpSign::Positive
            }
        }
    }
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

    #[test]
    fn percent_binary_phone_semantics() {
        // a ± b% → a × (1 ± b/100)
        assert_eq!(eval("100+10%"), Number::from_i64(110));
        assert_eq!(eval("100-10%"), Number::from_i64(90));
        assert_eq!(eval("200*50%"), Number::from_i64(100));
        assert_eq!(eval("200/50%"), Number::from_i64(400));
        assert_eq!(eval("50+50%"), Number::parse_decimal("75").unwrap());
        // Chained: (1000+5%)+5% = 1050+5% = 1102.5
        assert_eq!(eval("1000+5%+5%"), Number::parse_decimal("1102.5").unwrap());
    }

    #[test]
    fn zero_to_negative_power_is_div_zero() {
        let err = Evaluator::default().eval_str("0^-1").unwrap_err();
        assert_eq!(err, EngineError::DivisionByZero);
        let err = Evaluator::default().eval_str("0^(-2)").unwrap_err();
        assert_eq!(err, EngineError::DivisionByZero);
    }

    #[test]
    fn zero_to_positive_is_zero() {
        assert_eq!(eval("0^5"), Number::zero());
    }

    #[test]
    fn negative_base_odd_root() {
        let r = eval("(-8)^(1/3)");
        approx_eq(&r, &Number::from_i64(-2), "1e-50");
        let r = eval("(-8)^(2/3)");
        approx_eq(&r, &Number::from_i64(4), "1e-50");
        let r = eval("(-32)^(1/5)");
        approx_eq(&r, &Number::from_i64(-2), "1e-40");
    }

    #[test]
    fn negative_base_even_root_errors() {
        let err = Evaluator::default().eval_str("(-8)^(1/2)").unwrap_err();
        assert!(matches!(err, EngineError::Domain(_)));
        let err = Evaluator::default().eval_str("(-1)^0.5").unwrap_err();
        assert!(matches!(err, EngineError::Domain(_)));
    }

    #[test]
    fn tan_pole_errors() {
        let err = Evaluator::new(AngleMode::Degrees)
            .eval_str("tan(90)")
            .unwrap_err();
        assert!(matches!(err, EngineError::Domain(_)));
        let err = Evaluator::default().eval_str("tan(π/2)").unwrap_err();
        assert!(matches!(err, EngineError::Domain(_)));
    }

    #[test]
    fn sin_pi_displays_as_zero() {
        let d = eval("sin(π)").to_display_string(18);
        assert_eq!(d, "0", "trig underflow must snap for display, got {d}");
        let d = eval("cos(π/2)").to_display_string(18);
        assert_eq!(d, "0", "cos(π/2) display must be 0, got {d}");
    }

    #[test]
    fn number_juxtaposition_does_not_eval() {
        assert!(Evaluator::default().eval_str("1..2").is_err());
        assert!(Evaluator::default().eval_str(".5.5").is_err());
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

    // --- display / refeed safety -----------------------------------------

    #[test]
    fn refeed_round_trip_repeating_rationals() {
        let e = Evaluator::default();
        for src in ["1/3", "1/6", "1/7", "7/3", "-1/3", "1+1/3", "2/3", "1/8"] {
            let n = e.eval_str(src).expect(src);
            let refeed = n.to_refeed_string(18);
            let again = e.eval_str(&refeed).unwrap_or_else(|err| {
                panic!("refeed of {src:?} = {refeed:?} failed: {err}")
            });
            assert_eq!(n, again, "round-trip mismatch for {src} via {refeed}");
        }
    }

    #[test]
    fn refeed_chain_third_times_three() {
        let e = Evaluator::default();
        let third = e.eval_str("1/3").unwrap();
        let chained = format!("{}*3", third.to_refeed_string(18));
        assert_eq!(e.eval_str(&chained).unwrap(), Number::one());
    }

    #[test]
    fn refeed_chain_two_thirds_squared() {
        // Bare `2/3^2` would be 2/9; atom refeed must yield (2/3)^2 = 4/9.
        let e = Evaluator::default();
        let two_thirds = e.eval_str("2/3").unwrap();
        let chained = format!("{}^2", two_thirds.to_refeed_string(18));
        let expected = e.eval_str("(2/3)^2").unwrap();
        assert_eq!(e.eval_str(&chained).unwrap(), expected);
        assert_eq!(expected, e.eval_str("4/9").unwrap());
    }

    #[test]
    fn refeed_chain_neg_third_squared() {
        let e = Evaluator::default();
        let n = e.eval_str("-1/3").unwrap();
        let chained = format!("{}^2", n.to_refeed_string(18));
        assert_eq!(e.eval_str(&chained).unwrap(), e.eval_str("1/9").unwrap());
    }

    #[test]
    fn legacy_repeating_strings_do_not_eval_to_zero() {
        let e = Evaluator::default();
        for bad in ["0.(3)", "2.(3)", "0.1(6)", "-0.(3)"] {
            let err = e.eval_str(bad).expect_err(bad);
            assert!(
                matches!(err, EngineError::InvalidNumber(_)),
                "{bad} must error, got {err:?}"
            );
        }
    }

    #[test]
    fn display_third_uses_overline_not_parens() {
        let n = eval("1/3");
        let d = n.to_display_string(18);
        assert!(!d.contains('('), "display must not use parens: {d}");
        assert!(d.contains('\u{0305}'), "display must use overline: {d}");
    }

    // --- P0 display / magnitude regressions --------------------------------

    #[test]
    fn display_hundredth_not_thousandth() {
        assert_eq!(eval("0.01").to_display_string(18), "0.01");
        assert_eq!(eval("1/100").to_display_string(18), "0.01");
        assert_eq!(eval("1e-2").to_display_string(18), "0.01");
        assert_eq!(eval("0.1^2").to_display_string(18), "0.01");
        assert_eq!(eval("0.1^2").to_refeed_string(18), "0.01");
    }

    #[test]
    fn tan_45_deg_is_one_not_point_one() {
        let r = eval_deg("tan(45)");
        approx_eq(&r, &Number::one(), "1e-50");
        assert_eq!(r.to_display_string(18), "1");
    }

    #[test]
    fn sin_over_cos_45_deg_displays_one() {
        let r = eval_deg("sin(45)/cos(45)");
        approx_eq(&r, &Number::one(), "1e-50");
        assert_eq!(r.to_display_string(18), "1");
    }
}
