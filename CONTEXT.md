# CalcRux 開發交接

## 版本

- 最新發行：`v0.1.2`（versionCode 3）

## 最近完成（2026-06-29）

### 循環小數與顯示

- Rust `format_rational`：循環小數改為括號標記（例 `1/3` → `0.(3)`、`1/6` → `0.1(6)`），不再展開 18 位。
- 計算機：按 `=` 後 `isResult=true`，顯示區 `scrollTo(0)` 從開頭看結果；輸入運算式仍捲到尾端。
- 單位換算 / 匯率：換算結果欄位水平捲動至開頭；輸入欄捲至尾端。

### 震動回饋

- `NumPad.kt` 共用元件已加入 `HapticFeedbackType.LongPress`，單位換算與匯率九宮格與計算機一致。

## 驗證

```powershell
cargo test --workspace                              # 145 tests OK
cd android; .\gradlew.bat testDebugUnitTest         # OK
```

重建 native library 後需執行 `.\gradlew.bat generateUniFFIBindings`（若 FFI 有變更）。

## 注意

- 工作站 `AvailPageFile` 偏低時，Rust Android cross-compile 可能失敗。
- 循環小數格式僅適用 Rational 精確結果；`√2` 等無理數仍為有限有效數字。