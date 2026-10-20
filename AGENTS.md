# CalcRux

## 專案定位

本專案是以小米計算機逆向成果為藍圖，重新實作的 Android 計算機。

- Rust 負責計算核心、單位換算、貸款與匯率運算
- Kotlin + Jetpack Compose 負責 Android UI
- UniFFI 負責 Rust / Kotlin 橋接

## 目前範圍

- `crates/calcrux-engine`：表達式解析與任意精度運算
- `crates/calcrux-units`：8 類別單位換算
- `crates/calcrux-loan`：貸款分期計算
- `crates/calcrux-fx`：匯率換算核心
- `crates/calcrux-ffi`：對 Android 暴露統一 FFI API
- `android/`：Compose UI、ViewModel、Room、DataStore、網路層

## 建置環境

- Rust `1.94.0`（見 `rust-toolchain.toml`，避免較新 stable 在 Android cross-compile 出現 ICE）
- `cargo-ndk`
- Android SDK：`C:\Android\sdk`
- Android NDK：`C:\Android\sdk\ndk\27.0.12077973`
- JDK 17

PowerShell 環境變數：

```powershell
$env:PATH += ";$env:USERPROFILE\.cargo\bin"
$env:ANDROID_HOME = "C:\Android\sdk"
$env:ANDROID_NDK_HOME = "C:\Android\sdk\ndk\27.0.12077973"
```

## 常用指令

```powershell
# Rust 測試
cargo test --workspace

# Android 綁定與建置
cd android
.\gradlew.bat generateUniFFIBindings
.\gradlew.bat assembleDebug
.\gradlew.bat assembleRelease
```

APK 輸出位置：

```text
android/app/build/outputs/apk/debug/app-debug.apk
android/app/build/outputs/apk/release/app-release.apk
```

## 近期注意

- 計算機四則運算輸入規則：`+ - × ÷` 不可連續重複；若上一個字元已是四則運算符，再點新的運算符時直接覆寫最後一個符號
- 計算機按鍵點擊已加入 haptic feedback；單位換算與匯率 NumPad 共用相同震動
- 循環小數：**display** 用上橫線（`1/3` → `0.3̅`）；**refeed** 用 atom `(1/3)` 寫回 expression 續算。勿把 display 當 expression
- FFI `calc_eval` 回傳 `CalcResult { display, refeed }`；改 API 後需 regenerate UniFFI + 重建 `.so`
- 舊括號循環字串 `0.(3)` 會 parse error（防靜默錯算）；`2(3+4)` 隱式乘法仍合法
- 小寫 `e` 一律是自然常數（`2e+3` ≈ 8.44）；科學記號只認大寫 `E`
- 結果超過約 2^131072（≈39k 位，`10000!` 仍可）回 `overflow`，避免 `9^9^9` 卡死主執行緒；循環節超過 18 位改四捨五入顯示，refeed 仍為精確 `(p/q)`
- 預覽與 `=` 會自動補右括號；結果後按 `! % x² xʸ )` 接續結果，其他鍵重新開始
- 貸款期數上限 1200 個月（Rust `MAX_MONTHS`，Kotlin 端同步）
- 匯率來源：ExchangeRate-API 免費端點 `open.er-api.com/v6/latest/USD`（約 160 幣別含 TWD，每日更新一次，中間價；條款要求畫面標示 "Rates By Exchange Rate API"）。成功下載存 `fx_prefs`，離線優先用快取；更新失敗保留現有匯率
- 匯率頁為多欄位（預設 TWD/USD/JPY/EUR/CNY，可新增／移除／長按拖動排序，存 `fx_rows`），點任一欄即成為輸入欄；切到匯率頁時若距上次抓取超過 30 分鐘自動更新
- 歷史已併入計算機：在顯示區下拉（或點歷史圖示）開啟；最新一筆在最下面，列表到底再上推／拖把手／返回鍵關閉；主分頁只剩「計算機／換算」
- 歷史面板一直保持組合狀態，用 `graphicsLayer` 平移（不要改回改高度，會每幀重新量測列表而卡頓）；開關狀態用 `derivedStateOf`，避免整個計算機每幀重組
- 16 KB 分頁（Android 15+）：`.cargo/config.toml` 對 64 位元 Android target 加 `-z max-page-size=16384`；JNA 需 ≥ 5.17.0。驗證：`zipalign -c -P 16 -v 4 <apk>` 與 `llvm-readelf -lW <so>`（LOAD Align 需為 `0x4000`）
- 上架：`targetSdk/compileSdk = 36`（Play 自 2026-08-31 要求）；release 簽章讀 `android/keystore.properties` + `calcrux-upload.jks`（皆 gitignore，遺失就無法更新 Play 上的 app，需另外備份）；上傳用 `.\gradlew.bat bundleRelease` 產出 `app/build/outputs/bundle/release/app-release.aab`；商店 icon 原稿 `store/icon.svg`、`store/icon-512.png`
- 此工作站 `AvailPageFile` 偏低時，Rust Android cross-compile 與 Gradle daemon 可能失敗；重建 native library 前先確認分頁檔空間

## 現況摘要

- 主畫面：calc / convert / loan / history / fx 已有 UI 與 ViewModel
- 匯率：Frankfurter API + 離線 fallback
- 歷史紀錄：Room
- 設定：DataStore
- Android 端使用 Hilt、KSP、Material 3

## 工作原則

- 優先維持 Rust 核心與 Android UI 的責任邊界
- FFI API 變更時，同步檢查 Rust 匯出與 Kotlin 呼叫端
- 單位、匯率、貸款公式修改時，先補測試再改實作
- Compose 畫面調整時，避免破壞既有分頁與輸入流程
- 不提交產生物：例如 `target/`、generated bindings、APK

## 參考來源

- 逆向母專案：`../mi_calculator_re`
- 單位資料來源：依 SI／NIST SP 811／IAU 定義自行整理（`units.json` 每類 `source` 欄位；格式為「1 單位 = ratio 基準單位」）
