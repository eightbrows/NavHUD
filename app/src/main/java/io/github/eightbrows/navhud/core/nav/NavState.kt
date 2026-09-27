package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.SourceMode
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.sensor.CompassQuality

/** 位置のもと。 */
enum class SourceKind { LIVE, REPLAY }

/** 画面が見る計算済みの値。画面はこれだけを見る。 */
data class NavState(
    /** 現在時刻（epoch ms）。LIVE は端末の時刻、REPLAY はトラックの時刻。まだ時刻がなければ null */
    val nowMs: Long? = null,
    /** 最後に受け取った Fix */
    val fix: Fix? = null,
    val noFix: Boolean = true,
    val heading: Heading = Heading.NONE,
    val sourceMode: SourceMode = SourceMode.HYBRID,
    /** 対地速度 [m/s] */
    val groundSpeedMps: Float? = null,
    /** 標高 [m] = 楕円体高 − 標高オフセット */
    val altM: Double? = null,
    val rate: Rate? = null,
    val waypoints: List<Waypoint> = emptyList(),
    val nextWpIndex: Int? = null,
    /** 次WPへの方位（真北, 0..360） */
    val nextWpBearingDeg: Double? = null,
    val nextWpDistanceM: Double? = null,
    /** 次WPの到着予定（epoch ms） */
    val etaMs: Long? = null,
    /** 次WPの目標時刻までの秒数（過ぎたら負） */
    val targetCountdownSec: Long? = null,
    /** 次WPの締切時刻までの秒数（過ぎたら負） */
    val deadlineCountdownSec: Long? = null,
    val sourceKind: SourceKind = SourceKind.REPLAY,
    /** 再生中なら true、一時停止中なら false（LIVE では常に true） */
    val playing: Boolean = false,
    /** 表示に使う設定（表示モード、距離環、RATE 窓など） */
    val settings: NavSettings = NavSettings(),
    /** コンパスの状態（CAL / MAG の印）。コンパスの値がなければ null */
    val compass: CompassQuality? = null,
    /** 縮尺 [m]（ARC では基準の距離環が左右端に接する距離、North Up では最外周の距離環） */
    val rangeM: Double = 1_000.0,
    /** 縮尺の AUTO が ON */
    val rangeAuto: Boolean = true,
    /** PAN（ドラッグで地図を動かしている）なら、その表示。null なら通常（自機が基準） */
    val pan: PanView? = null,
    /** LIVE の軌跡（起動してからの分、間引き済み） */
    val liveTrail: List<TrackPoint> = emptyList(),
    /** REPLAY のトラック全体（間引き済み）。再生済みの部分は fix の時刻まで */
    val replayTrack: List<TrackPoint> = emptyList(),
)

/**
 * PAN の表示: 地点 (lat, lon) を表示枠の中心に置き、画面の上を upDeg にする。
 * ARC ではドラッグを始めた時点の機首方位で向きを固定する（North Up は 0）。
 */
data class PanView(val lat: Double, val lon: Double, val upDeg: Double)
