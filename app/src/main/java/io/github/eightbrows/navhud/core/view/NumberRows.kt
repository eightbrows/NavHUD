package io.github.eightbrows.navhud.core.view

/** 数値の表示（§6.1）の欄。caption は値の左に小さく出す見出し（RATE は窓の秒数を付けて出す）。 */
enum class NumberField(val caption: String) {
    TIME("TIME"),
    ALT("ALT"),
    RATE("RATE"),
    HDG("HDG"),
    GS("GS"),
    ETA("ETA"),
    TGT("TGT"),
    DDL("DDL"),
    LAT_LON("LAT/LON"),

    /** 地上高（海抜高度 − 最高標高。20261025-D07） */
    AGL("AGL"),
}

/**
 * 数値の表示の4行（上から）。画面（MainScreen の NumbersPanel）はこの順に並べる。
 * 次の WP の名前・方位・距離は地図上の WP の文字で見るので、数値には出さない（D10 で NEXT をなくした）。
 */
object NumberRows {
    val ROWS: List<List<NumberField>> = listOf(
        listOf(NumberField.TIME, NumberField.ALT, NumberField.RATE),
        listOf(NumberField.HDG, NumberField.GS, NumberField.ETA),
        listOf(NumberField.TGT, NumberField.DDL),
        // 地上高は、1 行目（ALT の隣）が幅 360dp ほどの画面で入りきらないので、今は 4 行目の LAT/LON の右に置く（20261025-D07。置き場所は回答待ち）
        listOf(NumberField.LAT_LON, NumberField.AGL),
    )
}
