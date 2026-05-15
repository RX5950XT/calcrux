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

- Rust stable
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
```

APK 輸出位置：

```text
android/app/build/outputs/apk/debug/app-debug.apk
```

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
- 單位資料來源：MIUI Calculator assets 衍生資料
