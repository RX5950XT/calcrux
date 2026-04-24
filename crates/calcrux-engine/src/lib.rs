//! OpenCalc calculation engine.
//!
//! Provides an expression parser and arbitrary-precision evaluator,
//! replacing the AOSP HP CR + UnifiedReal design used by Mi Calculator.

pub mod error;
pub mod lexer;
pub mod number;
pub mod parser;
pub mod eval;

pub use error::{EngineError, Result};
pub use eval::{AngleMode, Evaluator};
pub use number::Number;
pub use parser::Expr;
