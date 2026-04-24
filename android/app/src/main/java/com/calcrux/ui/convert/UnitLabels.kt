package com.calcrux.ui.convert

fun categoryLabel(key: String): String = when (key) {
    "length"      -> "長度"
    "weight"      -> "重量"
    "area"        -> "面積"
    "volume"      -> "體積"
    "time"        -> "時間"
    "temperature" -> "溫度"
    "velocity"    -> "速度"
    "data"        -> "資料"
    else          -> key
}

// Returns "中文名 (abbr)" e.g. "公尺 (m)"
fun unitLabel(key: String): String = when (key) {
    // 長度
    "m"      -> "公尺 (m)"
    "km"     -> "公里 (km)"
    "dm"     -> "公寸 (dm)"
    "cm"     -> "公分 (cm)"
    "mm"     -> "公釐 (mm)"
    "um"     -> "微米 (μm)"
    "nm"     -> "奈米 (nm)"
    "ang"    -> "埃 (Å)"           // 1 Å = 0.1 nm = 10⁻¹⁰ m
    "pm"     -> "皮米 (pm)"        // 10⁻¹² m
    "in"     -> "英吋 (in)"
    "ft"     -> "英尺 (ft)"
    "yd"     -> "碼 (yd)"
    "mi"     -> "英里 (mi)"
    "nmi"    -> "海里 (nmi)"
    "fm"     -> "英噚 (ftm)"       // fathom = 6 ft = 1.8288 m
    "fur"    -> "弗隆 (fur)"
    "ly"     -> "光年 (ly)"
    "au"     -> "天文單位 (au)"
    "ld"     -> "月距 (ld)"
    "pc"     -> "秒差距 (pc)"
    // 重量
    "kg"     -> "公斤 (kg)"
    "t"      -> "公噸 (t)"
    "g"      -> "公克 (g)"
    "mg"     -> "毫克 (mg)"
    "ug"     -> "微克 (μg)"
    "q"      -> "公擔 (q)"
    "ct"     -> "克拉 (ct)"
    "lb"     -> "磅 (lb)"
    "oz"     -> "盎司 (oz)"
    "gr"     -> "格令 (gr)"
    "lt"     -> "長噸 (lt)"
    "st"     -> "短噸 (ton)"       // short ton ≈ 907 kg（US ton）
    "uk.st"  -> "英石 (st)"        // stone = 14 lb ≈ 6.35 kg
    "dr"     -> "打蘭 (dr)"
    "uk.cwt" -> "英擔 (cwt)"
    "us.cwt" -> "美擔 (cwt)"
    // 面積
    "sq.m"   -> "平方公尺 (m²)"
    "sq.km"  -> "平方公里 (km²)"
    "ha"     -> "公頃 (ha)"        // 10000 m²
    "are"    -> "公畝 (a)"         // 100 m²
    "acre"   -> "英畝 (acre)"      // 4046.86 m²
    "sq.dm"  -> "平方公寸 (dm²)"
    "sq.cm"  -> "平方公分 (cm²)"
    "sq.mm"  -> "平方公釐 (mm²)"
    "sq.um"  -> "平方微米 (μm²)"
    "sq.in"  -> "平方英吋 (in²)"
    "sq.rd"  -> "平方竿 (rd²)"
    "sq.mi"  -> "平方英里 (mi²)"
    "sq.yd"  -> "平方碼 (yd²)"
    "sq.ft"  -> "平方英尺 (ft²)"
    // 體積
    "cu.m"   -> "立方公尺 (m³)"
    "cu.cm"  -> "立方公分 (cm³)"
    "cu.dm"  -> "立方公寸 (dm³)"
    "cu.mm"  -> "立方公釐 (mm³)"
    "cu.in"  -> "立方英吋 (in³)"
    "cu.ft"  -> "立方英尺 (ft³)"
    "cu.yd"  -> "立方碼 (yd³)"
    "cu.af"  -> "英畝英尺 (ac·ft)"
    "hl"     -> "公石 (hL)"
    "l"      -> "公升 (L)"
    "cl"     -> "厘升 (cL)"
    "dl"     -> "分升 (dL)"
    "ml"     -> "毫升 (mL)"
    // 時間
    "s"      -> "秒 (s)"
    "ms"     -> "毫秒 (ms)"
    "us"     -> "微秒 (μs)"
    "ps"     -> "皮秒 (ps)"
    "min"    -> "分鐘 (min)"
    "h"      -> "小時 (h)"
    "day"    -> "天 (day)"
    "week"   -> "週 (week)"
    "yr"     -> "年 (yr)"
    // 溫度
    "C"      -> "攝氏 (°C)"
    "F"      -> "華氏 (°F)"
    "K"      -> "克耳文 (K)"
    "R"      -> "蘭金 (°R)"
    "Re"     -> "列氏 (°Re)"
    // 速度
    "mps"    -> "公尺/秒 (m/s)"
    "kmph"   -> "公里/小時 (km/h)"
    "kmps"   -> "公里/秒 (km/s)"
    "c"      -> "光速 (c)"
    "mach"   -> "馬赫 (Mach)"
    "nmiph"  -> "節 (kt)"
    "mileph" -> "英里/小時 (mph)"
    "ftps"   -> "英尺/秒 (ft/s)"
    "inps"   -> "英吋/秒 (in/s)"
    // 資料（bit 為基底，1024-base 二進位前綴）
    "b"      -> "位元 (bit)"
    "kb"     -> "千位元 (Kbit)"    // 1024 bit（二進位千）
    "mb"     -> "百萬位元 (Mbit)"  // 1024 Kbit
    "gb"     -> "十億位元 (Gbit)"  // 1024 Mbit
    "tb"     -> "兆位元 (Tbit)"    // 1024 Gbit
    "pb"     -> "拍位元 (Pbit)"    // 1024 Tbit
    else     -> key
}

// Short name only (strip the "(abbr)" part)
fun unitShortLabel(key: String): String {
    val full = unitLabel(key)
    val idx = full.indexOf('(')
    return if (idx > 0) full.substring(0, idx).trim() else full
}

// Physical "small → large" order for each category.
// Units not in the list fall to the end in original order.
val UNIT_PHYSICAL_ORDER: Map<String, List<String>> = mapOf(
    // pm=10⁻¹²m, ang=10⁻¹⁰m, nm=10⁻⁹m, um=10⁻⁶m, mm=10⁻³m,
    // cm=0.01m, in=0.0254m, dm=0.1m, ft=0.3048m, yd=0.9144m, m,
    // fm=1.8288m, fur=201.168m, km, mi=1609m, nmi=1852m, ld, au, ly, pc
    "length" to listOf(
        "pm", "ang", "nm", "um", "mm", "cm", "in", "dm", "ft", "yd", "m",
        "fm", "fur", "km", "mi", "nmi", "ld", "au", "ly", "pc",
    ),
    // ug, mg, gr=0.0648g, ct=0.2g, g, dr=1.77g, oz=28.35g, lb=453.6g,
    // kg, uk.st=6.35kg, us.cwt=45.36kg, uk.cwt=50.8kg, q=100kg,
    // st≈907kg, t=1000kg, lt≈1016kg
    "weight" to listOf(
        "ug", "mg", "gr", "ct", "g", "dr", "oz", "lb", "kg",
        "uk.st", "us.cwt", "uk.cwt", "q", "st", "t", "lt",
    ),
    // sq.um, sq.mm, sq.cm, sq.in=6.45cm², sq.dm=100cm², sq.ft=929cm²,
    // sq.yd=0.836m², sq.m, sq.rd=25.3m², are=100m²,
    // acre=4047m², ha=10000m², sq.km, sq.mi
    "area" to listOf(
        "sq.um", "sq.mm", "sq.cm", "sq.in", "sq.dm", "sq.ft", "sq.yd",
        "sq.m", "sq.rd", "are", "acre", "ha", "sq.km", "sq.mi",
    ),
    // cu.mm=0.001mL, ml=cu.cm=1mL, cl=10mL, cu.in=16.4mL, dl=100mL,
    // l=cu.dm=1000mL, cu.ft=28316mL, hl=100000mL, cu.yd=764554mL,
    // cu.m, cu.af=1.233×10⁶ L
    "volume" to listOf(
        "cu.mm", "ml", "cu.cm", "cl", "cu.in", "dl", "l", "cu.dm",
        "cu.ft", "hl", "cu.yd", "cu.m", "cu.af",
    ),
    "time" to listOf(
        "ps", "us", "ms", "s", "min", "h", "day", "week", "yr",
    ),
    "temperature" to listOf(
        "K", "C", "F", "Re", "R",
    ),
    // in/s=0.0254m/s, km/h≈0.278m/s, ft/s=0.305m/s, mph≈0.447m/s,
    // kt≈0.514m/s, m/s, Mach≈340m/s, km/s, c
    "velocity" to listOf(
        "inps", "kmph", "ftps", "mileph", "nmiph", "mps", "mach", "kmps", "c",
    ),
    "data" to listOf(
        "b", "kb", "mb", "gb", "tb", "pb",
    ),
)
