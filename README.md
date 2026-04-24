# CalcRux

Android 計算機，Rust 核心 + Kotlin/Jetpack Compose UI。

---

## 功能

- **計算機**：基本與科學模式，支援三角函數、對數、階乘
- **單位換算**：長度、面積、體積、重量、速度、時間、溫度、資料
- **貸款計算**：等額本息與等額本金，可匯出 CSV
- **匯率換算**：透過 [Frankfurter](https://www.frankfurter.app/) 取得即時匯率，離線備援，可釘選常用幣別
- **歷史紀錄**：Room 持久化，支援搜尋與刪除

---

## 架構

```
Kotlin/Compose UI
       │  Hilt DI
  ViewModel + Repository
  ├── Room（歷史紀錄）
  ├── DataStore（設定）
  └── Retrofit + Moshi（匯率 API）
       │
  UniFFI JNI 橋接
       │
  libcalcrux.so（Rust）
  ├── calcrux-engine   表達式解析與求值
  ├── calcrux-units    單位換算
  ├── calcrux-loan     貸款攤還計算
  ├── calcrux-fx       匯率運算（純計算，不含網路）
  └── calcrux-ffi      UniFFI 聚合層（對外 API）
```

### Rust 核心

**calcrux-engine**

遞迴下降解析器，6 層運算符優先級，支援隱式乘法（`2π`、`3(4+1)`）。

數值型別：
```
Number::Rational(Rational)   ← malachite，精確有理數，快速路徑
Number::Real(BigFloat)       ← astro-float，任意精度，無法精確表示時自動切換
```

支援運算符：`+ − × ÷ ^ ! % √ sin cos tan asin acos atan ln log exp`，角度/弧度可切換。

**calcrux-units**

單位資料從 `data/units.json` 載入（8 類別，~120 個單位）。線性單位用換算率，溫度用公式字串（`x * 1.8 + 32` 等），由 calcrux-engine 的 evaluator 在啟動時解析一次。

**calcrux-loan**

兩種攤還模型：
- 等額本息：`M = P·r·(1+r)ⁿ / ((1+r)ⁿ − 1)`
- 等額本金：`Mₖ = P/n + (P − P·(k−1)/n)·r`

回傳 `Vec<Instalment>`，包含每期本金、利息、餘額。

**calcrux-ffi**

以 [UniFFI](https://github.com/mozilla/uniffi-rs) proc-macro 方式暴露 API（`#[uniffi::export]`），避免手寫 JNI boilerplate。`cargo-ndk` 交叉編譯四個 ABI（arm64-v8a、armeabi-v7a、x86、x86_64）。

### 依賴

| crate | 用途 |
|-------|------|
| `astro-float` | 任意精度浮點數與超越函數 |
| `malachite` | 任意精度整數與有理數 |
| `logos` | 詞法分析器（宣告式，codegen） |
| `uniffi` 0.28 | JNI 綁定產生器 |
| `serde_json` | 載入 units.json |
| `thiserror` | 錯誤型別 |

| Kotlin 函式庫 | 用途 |
|--------------|------|
| Jetpack Compose + Material 3 | UI |
| Room 2.7 | 歷史紀錄 SQLite |
| DataStore Preferences | 設定儲存 |
| Hilt | 依賴注入 |
| Retrofit + Moshi + OkHttp | 匯率 API |
| KSP 2.0.21 | annotation 處理（取代 KAPT） |
| JNA 5.14 | UniFFI 執行期依賴 |

---

## 建置需求

| 工具 | 版本 |
|------|------|
| Rust stable | ≥ 1.80 |
| cargo-ndk | ≥ 3.5 |
| Android SDK | API 35 |
| Android NDK | 27.x |
| JDK | 17 |
| Gradle | 8.9（已附 gradlew） |

加入 Android targets：
```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk
```

---

## 建置步驟

```bash
# 執行 Rust 測試
cargo test --workspace

# 產生 UniFFI Kotlin 綁定（需先有 .so）
cd android && ./gradlew generateUniFFIBindings

# Debug APK（~92 MB，含 debug 符號）
./gradlew assembleDebug

# Release APK（~11 MB，R8 縮減 + 符號剝除）
# 預設使用 debug keystore 簽名，正式發布前需換成正式 keystore
./gradlew assembleRelease
```

### 環境變數（Windows）

```powershell
$env:ANDROID_HOME     = "C:\Android\sdk"
$env:ANDROID_NDK_HOME = "C:\Android\sdk\ndk\27.0.12077973"
$env:PATH            += ";$env:USERPROFILE\.cargo\bin"
```

### 正式簽名（發布用）

```kotlin
// app/build.gradle.kts
signingConfigs {
    create("release") {
        storeFile   = file(System.getenv("KEYSTORE_PATH"))
        storePassword = System.getenv("STORE_PASSWORD")
        keyAlias    = System.getenv("KEY_ALIAS")
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
```

---

## 授權

Apache-2.0，詳見 [LICENSE](LICENSE)。

`crates/calcrux-units/data/units.json` 的單位資料衍生自 MIUI Calculator 的 assets，同為 Apache 授權。
