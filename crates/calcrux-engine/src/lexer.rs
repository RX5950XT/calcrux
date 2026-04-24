//! Expression lexer.
//!
//! Accepts both ASCII (`*`, `/`, `-`) and Unicode math (`×`, `÷`, `−`, `√`, `π`)
//! tokens so the UI can forward button glyphs directly.

use logos::Logos;

use crate::error::EngineError;

#[derive(Debug, Clone, PartialEq, Logos)]
#[logos(skip r"[ \t\r\n]+")]
#[logos(error = LexErrorKind)]
pub enum Token {
    // Numeric literals (integer, decimal, scientific).
    // `e`/`E` only counts as exponent when followed by an optional sign and digits;
    // otherwise `e` lexes as the Euler constant below.
    #[regex(r"(?:[0-9]+\.[0-9]*|\.[0-9]+|[0-9]+)(?:[eE][+-]?[0-9]+)?", |lex| lex.slice().to_string(), priority = 3)]
    Number(String),

    #[token("+")]
    Plus,

    // Hyphen-minus (ASCII) and Unicode minus sign.
    #[token("-")]
    #[token("\u{2212}")]
    Minus,

    #[token("*")]
    #[token("\u{00D7}")] // ×
    #[token("\u{22C5}")] // ⋅
    Mul,

    #[token("/")]
    #[token("\u{00F7}")] // ÷
    Div,

    #[token("^")]
    Pow,

    #[token("!")]
    Fact,

    #[token("%")]
    Percent,

    #[token("(")]
    LParen,

    #[token(")")]
    RParen,

    #[token(",")]
    Comma,

    // Constants & functions.
    #[token("\u{03C0}")] // π
    #[token("pi", ignore(ascii_case))]
    Pi,

    #[token("e")]
    Euler,

    #[token("\u{221A}")] // √
    #[token("sqrt", ignore(ascii_case))]
    Sqrt,

    #[token("sin", ignore(ascii_case))]
    Sin,
    #[token("cos", ignore(ascii_case))]
    Cos,
    #[token("tan", ignore(ascii_case))]
    Tan,
    #[token("asin", ignore(ascii_case))]
    #[token("arcsin", ignore(ascii_case))]
    Asin,
    #[token("acos", ignore(ascii_case))]
    #[token("arccos", ignore(ascii_case))]
    Acos,
    #[token("atan", ignore(ascii_case))]
    #[token("arctan", ignore(ascii_case))]
    Atan,
    #[token("ln", ignore(ascii_case))]
    Ln,
    #[token("log", ignore(ascii_case))]
    Log,
    #[token("exp", ignore(ascii_case))]
    Exp,
    #[token("abs", ignore(ascii_case))]
    Abs,
}

#[derive(Debug, Clone, Copy, Default, PartialEq, Eq)]
pub struct LexErrorKind;

/// Lex into `(token, source_span)` pairs so the parser can report precise
/// error locations.
pub fn tokenize(src: &str) -> Result<Vec<(Token, std::ops::Range<usize>)>, EngineError> {
    let mut out = Vec::new();
    let mut lex = Token::lexer(src);
    while let Some(item) = lex.next() {
        match item {
            Ok(tok) => out.push((tok, lex.span())),
            Err(_) => {
                let span = lex.span();
                let ch = src[span.clone()].chars().next().unwrap_or('?');
                return Err(EngineError::UnexpectedChar { pos: span.start, ch });
            }
        }
    }
    Ok(out)
}

#[cfg(test)]
mod tests {
    use super::Token::*;
    use super::*;

    fn lex(src: &str) -> Vec<Token> {
        tokenize(src).unwrap().into_iter().map(|(t, _)| t).collect()
    }

    #[test]
    fn single_digits() {
        assert_eq!(lex("1+2"), vec![Number("1".into()), Plus, Number("2".into())]);
    }

    #[test]
    fn decimals_and_scientific() {
        assert_eq!(
            lex("3.14 + .5 - 2e10 * 1.5E-3"),
            vec![
                Number("3.14".into()),
                Plus,
                Number(".5".into()),
                Minus,
                Number("2e10".into()),
                Mul,
                Number("1.5E-3".into()),
            ]
        );
    }

    #[test]
    fn unicode_operators() {
        assert_eq!(
            lex("7 × 8 ÷ 2 − 1"),
            vec![
                Number("7".into()),
                Mul,
                Number("8".into()),
                Div,
                Number("2".into()),
                Minus,
                Number("1".into()),
            ]
        );
    }

    #[test]
    fn constants_and_functions() {
        assert_eq!(
            lex("sin(π) + e + ln(2)"),
            vec![
                Sin, LParen, Pi, RParen, Plus, Euler, Plus, Ln, LParen,
                Number("2".into()), RParen,
            ]
        );
    }

    #[test]
    fn sqrt_alternate_spellings() {
        assert_eq!(lex("√4"), vec![Sqrt, Number("4".into())]);
        assert_eq!(lex("sqrt(4)"), vec![Sqrt, LParen, Number("4".into()), RParen]);
    }

    #[test]
    fn factorial_and_percent_and_power() {
        assert_eq!(
            lex("5! + 10% ^ 2"),
            vec![
                Number("5".into()), Fact, Plus,
                Number("10".into()), Percent, Pow, Number("2".into()),
            ]
        );
    }

    #[test]
    fn euler_vs_scientific_notation() {
        // `2e5` → single number token (scientific).
        assert_eq!(lex("2e5"), vec![Number("2e5".into())]);
        // `2e` with no digit after → number then Euler (implicit multiplication handled by parser).
        assert_eq!(lex("2e"), vec![Number("2".into()), Euler]);
        // Bare `e` → Euler.
        assert_eq!(lex("e"), vec![Euler]);
    }

    #[test]
    fn unexpected_character() {
        let err = tokenize("1 @ 2").unwrap_err();
        assert!(matches!(err, EngineError::UnexpectedChar { ch: '@', .. }));
    }

    #[test]
    fn whitespace_is_ignored() {
        assert_eq!(lex("  1\t+\n2 "), vec![Number("1".into()), Plus, Number("2".into())]);
    }
}
