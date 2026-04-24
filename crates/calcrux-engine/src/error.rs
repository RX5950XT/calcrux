use thiserror::Error;

#[derive(Debug, Error, Clone, PartialEq, Eq)]
pub enum EngineError {
    #[error("unexpected character at position {pos}: {ch:?}")]
    UnexpectedChar { pos: usize, ch: char },

    #[error("unexpected token at position {pos}")]
    UnexpectedToken { pos: usize },

    #[error("unexpected end of input")]
    UnexpectedEof,

    #[error("expected closing parenthesis")]
    MissingCloseParen,

    #[error("division by zero")]
    DivisionByZero,

    #[error("domain error: {0}")]
    Domain(&'static str),

    #[error("overflow")]
    Overflow,

    #[error("invalid number literal: {0}")]
    InvalidNumber(String),
}

pub type Result<T> = std::result::Result<T, EngineError>;
