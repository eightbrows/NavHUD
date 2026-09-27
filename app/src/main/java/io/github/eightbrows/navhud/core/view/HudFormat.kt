package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.nav.Rate
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

/** 画面に出す文字の整形。値がないときは "---"。 */
object HudFormat {
    const val NONE = "---"

    private val TIME: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss")

    /** 方位 "062°"。 */
    fun bearing(deg: Double?): String {
        if (deg == null) return NONE
        val d = deg.roundToInt().mod(360)
        return "%03d°".format(Locale.US, d)
    }

    fun bearing(deg: Float?): String = bearing(deg?.toDouble())

    /** 距離: 1km 未満は "850 m"、10km 未満は "1.17 km"、それ以上は "11.9 km"。 */
    fun distance(m: Double?): String = when {
        m == null -> NONE
        m < 1_000 -> "%.0f m".format(Locale.US, m)
        m < 10_000 -> "%.2f km".format(Locale.US, m / 1000)
        else -> "%.1f km".format(Locale.US, m / 1000)
    }

    /**
     * 距離環の文字: 1000m 未満は m の数字だけ（25 / 50 / 250）、以上は k（1k / 2.5k / 25k）。
     */
    fun ringLabel(m: Double): String =
        if (m < 1_000) plain(m) else plain(m / 1000) + "k"

    /** 縮尺の段の表示: "500m" / "1km" / "2.5km"（RNG ボタン・設定画面）。 */
    fun rangeStep(m: Double): String =
        if (m < 1_000) plain(m) + "m" else plain(m / 1000) + "km"

    /** 小数点以下の余分な 0 を付けない数（12.50 → "12.5"、100.0 → "100"）。 */
    private fun plain(x: Double): String =
        java.math.BigDecimal.valueOf(x).setScale(3, java.math.RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()

    /** 方位目盛りの文字: N / E / S / W、それ以外は 10 で割った数（ND の慣習）。 */
    fun compassLabel(deg: Int): String = when (deg.mod(360)) {
        0 -> "N"
        90 -> "E"
        180 -> "S"
        270 -> "W"
        else -> (deg.mod(360) / 10).toString()
    }

    fun speedKmh(mps: Float?): String = if (mps == null) NONE else "%.0f km/h".format(Locale.US, mps * 3.6)

    fun altitude(m: Double?): String = if (m == null) NONE else "%.0f m".format(Locale.US, m)

    fun latLon(lat: Double?, lon: Double?): String =
        if (lat == null || lon == null) NONE else "%.5f, %.5f".format(Locale.US, lat, lon)

    fun time(ms: Long?, zone: ZoneId): String =
        if (ms == null) NONE else Instant.ofEpochMilli(ms).atZone(zone).format(TIME)

    /** 経過時間 "h:mm"（リプレイの帯）。 */
    fun elapsed(ms: Long): String {
        val min = (ms.coerceAtLeast(0) / 60_000)
        return "%d:%02d".format(Locale.US, min / 60, min % 60)
    }

    /** カウントダウン "+0:10:00" / "-0:05:00"。 */
    fun countdown(sec: Long?): String {
        if (sec == null) return NONE
        val a = abs(sec)
        return "%s%d:%02d:%02d".format(Locale.US, if (sec < 0) "-" else "+", a / 3600, a / 60 % 60, a % 60)
    }

    /** RATE: "1.20 km  +35 m"。値がなければ "---"。 */
    fun rate(r: Rate?): String {
        if (r == null) return NONE
        val alt = r.altDiffM?.let { "%+.0f m".format(Locale.US, it) } ?: NONE
        return "${distance(r.distanceM)}  $alt"
    }
}
