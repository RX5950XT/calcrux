//! Recursive-descent parser — mirrors the 6-level precedence hierarchy from
//! Mi Calculator's `CalculatorExpr` (g/k/i/h/j/l methods).
//!
//! Precedence (lowest → highest):
//!  1. Additive      (+, −)
//!  2. Multiplicative (×, ÷, implicit multiplication)
//!  3. Unary         (leading −)
//!  4. Power         (^, right-associative)
//!  5. Postfix       (!, %)
//!  6. Atom          (number, constant, func call, paren group)

use crate::error::{EngineError, Result};
use crate::lexer::{tokenize, Token};

/// Intermediate representation: an AST node.
#[derive(Debug, Clone, PartialEq)]
pub enum Expr {
    Number(String),
    Pi,
    Euler,
    Negate(Box<Expr>),
    BinOp(Box<Expr>, BinOp, Box<Expr>),
    UnaryFn(UnaryFn, Box<Expr>),
    Pow(Box<Expr>, Box<Expr>),
    Factorial(Box<Expr>),
    Percent(Box<Expr>),
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum BinOp {
    Add,
    Sub,
    Mul,
    Div,
}

#[derive(Debug, Clone, Copy, PartialEq, Eq)]
pub enum UnaryFn {
    Sin,
    Cos,
    Tan,
    Asin,
    Acos,
    Atan,
    Ln,
    Log,
    Exp,
    Abs,
    Sqrt,
}

struct Parser {
    tokens: Vec<(Token, std::ops::Range<usize>)>,
    pos: usize,
}

impl Parser {
    fn new(tokens: Vec<(Token, std::ops::Range<usize>)>) -> Self {
        Parser { tokens, pos: 0 }
    }

    fn peek(&self) -> Option<&Token> {
        self.tokens.get(self.pos).map(|(t, _)| t)
    }

    fn pos_span(&self) -> usize {
        self.tokens.get(self.pos).map(|(_, s)| s.start).unwrap_or(usize::MAX)
    }

    fn advance(&mut self) -> Option<Token> {
        self.tokens.get(self.pos).map(|(t, _)| {
            let tok = t.clone();
            self.pos += 1;
            tok
        })
    }

    fn expect(&mut self, expected: Token) -> Result<()> {
        match self.peek() {
            Some(t) if *t == expected => {
                self.advance();
                Ok(())
            }
            Some(_) => Err(EngineError::UnexpectedToken { pos: self.pos_span() }),
            None => Err(EngineError::UnexpectedEof),
        }
    }

    // Level 1 — additive (+, −).
    fn parse_add(&mut self) -> Result<Expr> {
        let mut lhs = self.parse_mul()?;
        loop {
            match self.peek() {
                Some(Token::Plus) => {
                    self.advance();
                    let rhs = self.parse_mul()?;
                    lhs = Expr::BinOp(Box::new(lhs), BinOp::Add, Box::new(rhs));
                }
                Some(Token::Minus) => {
                    self.advance();
                    let rhs = self.parse_mul()?;
                    lhs = Expr::BinOp(Box::new(lhs), BinOp::Sub, Box::new(rhs));
                }
                _ => break,
            }
        }
        Ok(lhs)
    }

    // Level 2 — multiplicative (×, ÷, implicit).
    fn parse_mul(&mut self) -> Result<Expr> {
        let mut lhs = self.parse_unary()?;
        loop {
            match self.peek() {
                Some(Token::Mul) => {
                    self.advance();
                    let rhs = self.parse_unary()?;
                    lhs = Expr::BinOp(Box::new(lhs), BinOp::Mul, Box::new(rhs));
                }
                Some(Token::Div) => {
                    self.advance();
                    let rhs = self.parse_unary()?;
                    lhs = Expr::BinOp(Box::new(lhs), BinOp::Div, Box::new(rhs));
                }
                // Implicit multiplication: `2π`, `2(x+1)`, `(a)(b)`.
                Some(Token::Number(_))
                | Some(Token::Pi)
                | Some(Token::Euler)
                | Some(Token::LParen)
                | Some(Token::Sin)
                | Some(Token::Cos)
                | Some(Token::Tan)
                | Some(Token::Asin)
                | Some(Token::Acos)
                | Some(Token::Atan)
                | Some(Token::Ln)
                | Some(Token::Log)
                | Some(Token::Exp)
                | Some(Token::Abs)
                | Some(Token::Sqrt) => {
                    let rhs = self.parse_unary()?;
                    lhs = Expr::BinOp(Box::new(lhs), BinOp::Mul, Box::new(rhs));
                }
                _ => break,
            }
        }
        Ok(lhs)
    }

    // Level 3 — leading unary negation.
    fn parse_unary(&mut self) -> Result<Expr> {
        if matches!(self.peek(), Some(Token::Minus)) {
            self.advance();
            let e = self.parse_unary()?;
            return Ok(Expr::Negate(Box::new(e)));
        }
        self.parse_pow()
    }

    // Level 4 — power (right-associative).
    fn parse_pow(&mut self) -> Result<Expr> {
        let base = self.parse_postfix()?;
        if matches!(self.peek(), Some(Token::Pow)) {
            self.advance();
            let exp = self.parse_unary()?; // right-assoc: a^b^c = a^(b^c)
            return Ok(Expr::Pow(Box::new(base), Box::new(exp)));
        }
        Ok(base)
    }

    // Level 5 — postfix (!, %).
    fn parse_postfix(&mut self) -> Result<Expr> {
        let mut e = self.parse_atom()?;
        loop {
            match self.peek() {
                Some(Token::Fact) => {
                    self.advance();
                    e = Expr::Factorial(Box::new(e));
                }
                Some(Token::Percent) => {
                    self.advance();
                    e = Expr::Percent(Box::new(e));
                }
                _ => break,
            }
        }
        Ok(e)
    }

    // Level 6 — atoms: number literals, constants, function calls, paren groups.
    fn parse_atom(&mut self) -> Result<Expr> {
        match self.peek().cloned() {
            Some(Token::Number(s)) => {
                self.advance();
                Ok(Expr::Number(s))
            }
            Some(Token::Pi) => {
                self.advance();
                Ok(Expr::Pi)
            }
            Some(Token::Euler) => {
                self.advance();
                Ok(Expr::Euler)
            }
            Some(Token::LParen) => {
                self.advance();
                let inner = self.parse_add()?;
                self.expect(Token::RParen).map_err(|_| EngineError::MissingCloseParen)?;
                Ok(inner)
            }
            Some(Token::Sqrt) => self.parse_fn1(UnaryFn::Sqrt),
            Some(Token::Sin) => self.parse_fn1(UnaryFn::Sin),
            Some(Token::Cos) => self.parse_fn1(UnaryFn::Cos),
            Some(Token::Tan) => self.parse_fn1(UnaryFn::Tan),
            Some(Token::Asin) => self.parse_fn1(UnaryFn::Asin),
            Some(Token::Acos) => self.parse_fn1(UnaryFn::Acos),
            Some(Token::Atan) => self.parse_fn1(UnaryFn::Atan),
            Some(Token::Ln) => self.parse_fn1(UnaryFn::Ln),
            Some(Token::Log) => self.parse_fn1(UnaryFn::Log),
            Some(Token::Exp) => self.parse_fn1(UnaryFn::Exp),
            Some(Token::Abs) => self.parse_fn1(UnaryFn::Abs),
            Some(_) => Err(EngineError::UnexpectedToken { pos: self.pos_span() }),
            None => Err(EngineError::UnexpectedEof),
        }
    }

    /// Parse a unary function that accepts `(arg)` or a bare `arg` (for √).
    fn parse_fn1(&mut self, func: UnaryFn) -> Result<Expr> {
        self.advance(); // consume function name
        // Allow `sqrt 4` (no paren) for √ symbol convenience; all others require parens.
        let arg = if matches!(self.peek(), Some(Token::LParen)) {
            self.advance(); // consume '('
            let e = self.parse_add()?;
            self.expect(Token::RParen).map_err(|_| EngineError::MissingCloseParen)?;
            e
        } else if func == UnaryFn::Sqrt {
            self.parse_atom()?
        } else {
            return Err(EngineError::UnexpectedToken { pos: self.pos_span() });
        };
        Ok(Expr::UnaryFn(func, Box::new(arg)))
    }
}

/// Parse an expression string into an [`Expr`] AST.
pub fn parse(src: &str) -> Result<Expr> {
    let tokens = tokenize(src)?;
    if tokens.is_empty() {
        return Err(EngineError::UnexpectedEof);
    }
    let mut p = Parser::new(tokens);
    let expr = p.parse_add()?;
    if p.pos < p.tokens.len() {
        return Err(EngineError::UnexpectedToken { pos: p.pos_span() });
    }
    Ok(expr)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn num(s: &str) -> Expr {
        Expr::Number(s.to_string())
    }
    fn add(l: Expr, r: Expr) -> Expr {
        Expr::BinOp(Box::new(l), BinOp::Add, Box::new(r))
    }
    fn sub(l: Expr, r: Expr) -> Expr {
        Expr::BinOp(Box::new(l), BinOp::Sub, Box::new(r))
    }
    fn mul(l: Expr, r: Expr) -> Expr {
        Expr::BinOp(Box::new(l), BinOp::Mul, Box::new(r))
    }
    fn div(l: Expr, r: Expr) -> Expr {
        Expr::BinOp(Box::new(l), BinOp::Div, Box::new(r))
    }
    fn pow(b: Expr, e: Expr) -> Expr {
        Expr::Pow(Box::new(b), Box::new(e))
    }
    fn neg(e: Expr) -> Expr {
        Expr::Negate(Box::new(e))
    }
    fn fact(e: Expr) -> Expr {
        Expr::Factorial(Box::new(e))
    }
    fn pct(e: Expr) -> Expr {
        Expr::Percent(Box::new(e))
    }
    fn f(func: UnaryFn, arg: Expr) -> Expr {
        Expr::UnaryFn(func, Box::new(arg))
    }

    #[test]
    fn simple_addition() {
        assert_eq!(parse("1+2").unwrap(), add(num("1"), num("2")));
    }

    #[test]
    fn precedence_mul_before_add() {
        // 1 + 2 * 3 → 1 + (2*3)
        assert_eq!(
            parse("1+2*3").unwrap(),
            add(num("1"), mul(num("2"), num("3")))
        );
    }

    #[test]
    fn precedence_parens_override() {
        // (1+2) * 3
        assert_eq!(
            parse("(1+2)*3").unwrap(),
            mul(add(num("1"), num("2")), num("3"))
        );
    }

    #[test]
    fn unary_negation() {
        assert_eq!(parse("-3").unwrap(), neg(num("3")));
    }

    #[test]
    fn double_negation() {
        assert_eq!(parse("--3").unwrap(), neg(neg(num("3"))));
    }

    #[test]
    fn power_right_assoc() {
        // 2^3^4 → 2^(3^4)
        assert_eq!(
            parse("2^3^4").unwrap(),
            pow(num("2"), pow(num("3"), num("4")))
        );
    }

    #[test]
    fn factorial() {
        assert_eq!(parse("5!").unwrap(), fact(num("5")));
    }

    #[test]
    fn percent() {
        assert_eq!(parse("50%").unwrap(), pct(num("50")));
    }

    #[test]
    fn function_with_parens() {
        assert_eq!(
            parse("sin(1)").unwrap(),
            f(UnaryFn::Sin, num("1"))
        );
    }

    #[test]
    fn sqrt_bare_arg() {
        assert_eq!(parse("√4").unwrap(), f(UnaryFn::Sqrt, num("4")));
    }

    #[test]
    fn sqrt_with_parens() {
        assert_eq!(parse("sqrt(9)").unwrap(), f(UnaryFn::Sqrt, num("9")));
    }

    #[test]
    fn implicit_mul_number_constant() {
        // 2π → 2 * π
        assert_eq!(
            parse("2π").unwrap(),
            mul(num("2"), Expr::Pi)
        );
    }

    #[test]
    fn implicit_mul_paren() {
        // 2(3+4) → 2 * (3+4)
        assert_eq!(
            parse("2(3+4)").unwrap(),
            mul(num("2"), add(num("3"), num("4")))
        );
    }

    #[test]
    fn constants_pi_and_e() {
        assert_eq!(parse("π+e").unwrap(), add(Expr::Pi, Expr::Euler));
    }

    #[test]
    fn nested_functions() {
        // sin(cos(0))
        assert_eq!(
            parse("sin(cos(0))").unwrap(),
            f(UnaryFn::Sin, f(UnaryFn::Cos, num("0")))
        );
    }

    #[test]
    fn complex_expression() {
        // -1 + 2 * (3 - 4) / 5
        let expected = add(
            neg(num("1")),
            div(mul(num("2"), sub(num("3"), num("4"))), num("5")),
        );
        assert_eq!(parse("-1+2*(3-4)/5").unwrap(), expected);
    }

    #[test]
    fn missing_close_paren() {
        assert!(matches!(parse("(1+2").unwrap_err(), EngineError::MissingCloseParen));
    }

    #[test]
    fn empty_input() {
        assert!(matches!(parse("").unwrap_err(), EngineError::UnexpectedEof));
    }

    #[test]
    fn unexpected_operator() {
        assert!(parse("+1").is_err());
    }

    #[test]
    fn unicode_operators_parsed() {
        // 7 × 8 ÷ 2
        assert_eq!(
            parse("7 × 8 ÷ 2").unwrap(),
            div(mul(num("7"), num("8")), num("2"))
        );
    }
}
