package io.github.eightbrows.navhud.core.io

data class LatLon(val lat: Double, val lon: Double)

/** 座標の貼り付け（§7.3）。Google マップのピン座標 "34.69370, 135.50230" 形式だけを読む。 */
object CoordinateText {

    private val PATTERN = Regex("""([+-]?\d+(?:\.\d+)?)\s*,\s*([+-]?\d+(?:\.\d+)?)""")

    /** 読めなければ null（範囲外・形式違い・複数行など）。 */
    fun parse(text: String): LatLon? {
        val m = PATTERN.matchEntire(text.trim()) ?: return null
        val lat = m.groupValues[1].toDouble()
        val lon = m.groupValues[2].toDouble()
        if (lat !in -90.0..90.0 || lon !in -180.0..180.0) return null
        return LatLon(lat, lon)
    }

    /** 編集欄に出す "lat, lon"。丸めない（読み直すと同じ値になる）。 */
    fun format(lat: Double, lon: Double): String = "${plain(lat)}, ${plain(lon)}"

    private fun plain(d: Double) = java.math.BigDecimal.valueOf(d).stripTrailingZeros().toPlainString()
}
