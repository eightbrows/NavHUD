package io.github.eightbrows.navhud.core.model

import kotlinx.coroutines.flow.Flow
import java.time.LocalTime

/** 位置1件。実機GPSとリプレイで共通。 */
data class Fix(
    val timeMs: Long,
    val lat: Double,
    val lon: Double,
    /** 楕円体高（生値）。表示時に標高オフセットを引く。 */
    val altRawM: Double? = null,
    val speedMps: Float? = null,
    /** 進行方位（真北, 0..360）。null = 方位なし。 */
    val bearingDeg: Float? = null,
    val horizAccM: Float? = null,
    /** 方位の精度 [°]。端末が出さなければ null */
    val bearingAccDeg: Float? = null,
)

data class Waypoint(
    val name: String,
    val lat: Double,
    val lon: Double,
    val eleM: Double? = null,
    val targetTime: LocalTime? = null,
    val deadlineTime: LocalTime? = null,
    val enabled: Boolean = true,
    val reached: Boolean = false,
    /** この WP の到達半径 [m]。null なら全体の設定（NavSettings.reachRadiusM） */
    val radiusM: Double? = null,
)

/** ユーザーが選ぶ方位ソース。 */
enum class SourceMode { HYBRID, GPS, COMPASS }

/** 実際に使われている方位ソース。 */
enum class HeadingSrc { GPS, COMPASS, NONE }

/** Live GPS / Replay。 */
interface PositionSource {
    val fixes: Flow<Fix>
}

/** コンパス（真北補正済み）。 */
interface HeadingSource {
    val headingDeg: Flow<Float>
}
