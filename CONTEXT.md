# CalcRux 開發交接

## 版本

- 最新發行目標：`v0.1.4`（versionCode 5）

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
