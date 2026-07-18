# CalcRux 開發交接

## 版本

- 最新發行：`v0.1.3`（versionCode 4）

## 最近完成（2026-07-19）

### 循環小數誤算修正（P0）

**根因：** 結果格式化為 `0.(3)` 後寫回 expression，parser 隱式乘法解成 `0×3=0`。

**修正：**

1. **Display**：循環小數改上橫線（U+0305），例 `1/3` → `0.3̅`、`1/6` → `0.16̅`
2. **Refeed**：循環有理數輸出 atom `(p/q)` / `(-p/q)`，保證續算優先序正確
3. **FFI**：`calc_eval` → `CalcResult { display, refeed }`
4. **Android**：`=` 後 expression=refeed、畫面=resultDisplay；digit/⌫/Func 覆寫；四則續接 refeed
5. **Harden**：舊字串 `0.(3)` / `0.1(6)` 等改 **Error**，不再靜默錯算；保留 `2(3+4)`

### 驗證

```powershell
cargo test --workspace
# Android（需先 regenerate bindings + 重建 .so）
cd android
# 本機可用 host DLL 產 bindings：
# cargo run -p calcrux-ffi --bin uniffi-bindgen -- generate --library target/debug/calcrux.dll --language kotlin --out-dir android/app/src/main/java/com/calcrux/generated
.\gradlew.bat testDebugUnitTest
.\gradlew.bat assembleDebug
```

## 注意

- `display` 不可當 expression refeed；copy 在 isResult 時複製 display（不保證可再算）
- 工作站 pagefile 偏低時 cross-compile 可能失敗
- 無理數（`√2`）仍為有限位小數
- 勿提交：`jniLibs/*.so`、`generated/`、`*.log`、APK、`local.properties`
