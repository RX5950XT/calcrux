# CalcRux 開發交接

## 版本

- 最新發行：`v0.1.6`（versionCode 7）
- Release：https://github.com/RX5950XT/calcrux/releases/tag/v0.1.6

## Google Play 上架（2026-10-21 更新）

- Play Console：個人帳號 `rx5950xt`；套件 `com.calcrux`；類別「工具」；內容分級全年齡；目標年齡 13+；資料安全「不收集資料」
- 隱私權政策：獨立 repo `RX5950XT/calcrux-privacy` → https://rx5950xt.github.io/calcrux-privacy/
- 商店素材：`store/`（icon、主題圖片 `feature-1024x500.png`、4 張 1080×2160 截圖）
- 封閉測試（Alpha）：版本 7（0.1.6）已過審；測試者改用 Google 群組 `rx5950xt-testers@googlegroups.com`（2026-10-21 已生效）
  - 加入群組：https://groups.google.com/g/rx5950xt-testers ，或寄信到 `rx5950xt-testers+subscribe@googlegroups.com`
  - 加入測試：https://play.google.com/apps/testing/com.calcrux
  - 群組可給其他 app（如 DroidProbe）共用；12 人 × 連續 14 天是每個 app 各自計算
- 目前狀態：封閉測試中，商店頁只有測試者看得到；尚未申請正式版
- DroidProbe 同帳號、同群組，封閉測試版本 2（0.1.0）亦已生效
- 下一步：湊滿 12 名測試者連續 14 天 → 申請正式版存取權（問卷）
- 帳號綁有商家帳戶，商店頁「App support」會公開本名與地址（帳號層級，已決定接受）
- 0.1.6 上傳的 AAB 移除了 `BUNDLE-METADATA`（R8 mapping、native debug symbols）後重簽，因瀏覽器自動化上傳上限 10 MB；app 本身不受影響，只是 Vitals 當機堆疊不可讀。之後手動上傳原始 `app-release.aab` 即可

## 最近完成（2026-10-18）

### Bug 修正

| 級 | 問題 | 修正 |
|---|---|---|
| P0 | `9^9^9` 等巨大結果卡死主執行緒（預覽每鍵都算） | `check_magnitude` + 冪次預估，超過 2^131072 回 `overflow` |
| P0 | `1/1000003` 產生 33 萬字元顯示 | 循環節 > 18 位改四捨五入；refeed 不變 |
| P1 | 鍵盤 `2 e + 3` 被當科學記號 = 2000 | lexer 只認大寫 `E` 為指數 |
| P1 | 貸款期數 40 億 → OOM；NaN 通過驗證 | `MAX_MONTHS = 1200`、`is_finite` |
| P1 | 結果後按 `! % x²` 變成非法算式；`=` 重複寫歷史；開頭不能打負號 | `CalculatorViewModel` 規則修正 + 單元測試 |
| P1 | 匯率更新失敗丟掉已下載匯率；離線只能用 2024 匯率 | 保留現有匯率；成功時存 `fx_prefs` 快取 |
| P1 | 16 KB 分頁裝置：`libcalcrux.so` LOAD 4 KB 對齊、JNA 5.14 `libjnidispatch.so` 不相容 | `.cargo/config.toml` 加 `max-page-size=16384`；JNA → 5.17.0 |
| P1 | Frankfurter（ECB）不含 TWD/VND 等，新台幣一直用 2024 內建匯率 | 改用 ExchangeRate-API `open.er-api.com`（~160 幣別、每日更新） |
| P2 | 每次換算重新解析 units.json | FFI `OnceLock<Converter>` |
| P2 | 溫度無法輸入負數；換算無清除鍵；預設單位 pm→Å | NumPad ±/00/AC；常用預設單位 |

### UI/UX

- 計算機鍵盤改小米科學模式排列（INV／角度進鍵盤、移除重複 C）；顯示區大字自動縮放、千分位、結果上方顯示原算式、中文錯誤訊息、自動補右括號、退格整個刪函數
- 主題補齊 M3 surface 角色（原本卡片／對話框帶 baseline 紫色）；淺色橘 `#E65A00` 以符合對比
- 換算／匯率：NumPad 加高、右側 ⌫／AC；數值千分位與等寬數字
- 貸款：千分位、等額本金顯示「首月還款」、切換方式自動重算；歷史：省略號、空狀態
- 匯率：多欄位同時換算（預設 5 欄，可新增／移除，任一欄可輸入）、國旗 emoji（`currencyFlag`）、未翻譯幣別用 `java.util.Currency` 中文名
- 歷史併入計算機：顯示區下拉開啟（`CalculatorScreen` 的 `historyProgress`，graphicsLayer 平移 + nested scroll 上推關閉），最新在最下；移除搜尋框與全部清除；單筆顯示截到 120 字（舊紀錄的超長循環小數會讓捲動卡頓）；主分頁剩 2 個；列表共用 `DateTimeFormatter`、`contentType`
- 匯率欄位長按拖動排序；切到匯率頁 30 分鐘內未更新就自動更新；貸款兩種方式加說明

## 最近完成（2026-07-19）

### 計算錯誤全面修復

| 級 | 問題 | 修正 |
|---|---|---|
| P0 | `0.01`→`0.001`、`tan(45°)`→`0.1` | 終止有理數精確長除；`format_bigfloat` 進位移小數點 |
| P1 | `1..2` / `.5.5` → 靜默乘法 | 拒收 bare number×number 隱式乘 |
| P1 | `0^-1` → Inf | 改 `DivisionByZero` |
| P1 | `tan(90°)` / `tan(π/2)` 巨大數 | cos 近零當 domain error |
| P2 | `100+10%`→100.1 | 手機語意：`a±b%`→`a(1±b/100)`，`a×÷b%` 同理 |
| P2 | `(-8)^(1/3)` domain | 負底 + 奇分母有理冪允許 |
| P3 | `sin(π)` 顯示極小非零 | `|x|` 過小 snap 顯示 `0` |

**刻意不變：** `5!!` = `(5!)!`（連續 postfix 階乘，非 double-factorial）

### 循環小數（v0.1.3）

display 上橫線 + refeed `(p/q)` + 舊 `0.(3)` 拒收。

## 驗證

```powershell
cargo test --workspace
cargo test -p calcrux-engine percent_binary tan_pole zero_to_negative negative_base number_juxtaposition display_hundredth
```

## 注意

- `display` 不可當 expression refeed
- 發版需 bump version（勿重複 0.1.3 / code 4）
- 勿提交：`jniLibs/*.so`、`generated/`、`*.log`、APK、`local.properties`
