/// Thin binary wrapper so Gradle can invoke:
///   cargo run --bin uniffi-bindgen -- generate --library <lib> --language kotlin --out-dir <dir>
fn main() {
    uniffi::uniffi_bindgen_main()
}
