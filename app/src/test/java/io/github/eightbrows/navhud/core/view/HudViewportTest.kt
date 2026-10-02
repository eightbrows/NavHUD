package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.RangeAuto
import io.github.eightbrows.navhud.core.nav.SourceKind
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.hypot

/**
 * AUTO 縮尺: 次の WP を画面に投影して、矢印と AUTO の枠（TargetFrame）に余白付きで収まる最小の段。
 * 枠: 上は数値の下端、左・下は描画の枠、右は操作列のある高さの範囲だけ操作列の左端（それより上・下は画面の右端）。
 * 寸法はエミュレータで測った値（密度 1.7）: 描画の枠 720×1239 px、上（上部バー・数値）211 px、右の操作列 102 px、
 * 下（WP 列 40dp・プロファイル 小 56dp・再生の帯 36dp・ナビゲーションバー）LIVE 204 px / REPLAY 265 px。
 * 操作列（＋ / RNG / −、306 px）は回避枠の縦中央: LIVE y 470..776、REPLAY y 439.5..745.5。
 */
class HudViewportTest {

    private val k = 1.7f
    private val m = HudMetrics().scaled(k)
    private val rect = HudRect(0f, 0f, 720f, 1239f)
    private val viewport = HudViewport(rect, m, HudInsets(top = 211f, right = 102f, bottom = 204f, rightSpan = 470f..776f))
    private val replayViewport = HudViewport(rect, m, HudInsets(top = 211f, right = 102f, bottom = 265f, rightSpan = 439.5f..745.5f))
    private val stepsM = RangeAuto.ALL_STEPS_KM.map { it * 1000 }
    private val arc = NavSettings(displayMode = DisplayMode.ARC)
    private val arcHigh = arc.copy(ownshipPosition = OwnshipPosition.HIGH)
    private val northUp = NavSettings(displayMode = DisplayMode.NORTH_UP)

    // 余白は 22 + 16 dp = 64.6 px。収まる範囲: x 64.6..655.4、y 275.6..1174.4。ただし操作列の高さの範囲
    // （LIVE y 405.4..840.6、REPLAY y 374.9..810.1）では x 553.4 まで
    // ARC（標準）: 自機は画面の横中央、WP 列の上端から 24dp。LIVE (360, 994.2)、REPLAY (360, 933.2)。1 m = 360 / 縮尺 px
    private fun auto(target: EN, headingDeg: Double?, s: NavSettings, vp: HudViewport = viewport): Double =
        RangeAuto.desired(stepsM) { vp.fits(it, target, headingDeg, s) }!!

    /** 自機から見て、機首方位から右回りに relDeg の向き・distM 先の点（機首 000 なので北が前）。 */
    private fun at(relDeg: Double, distM: Double) =
        Math.toRadians(relDeg).let { EN(distM * Math.sin(it), distM * Math.cos(it)) }

    @Test
    fun arcAhead() {
        // 前方 1.9km: 上まで 718.6 px（LIVE）→ 1km の段（684 px）に収まる。REPLAY は 657.6 px → 2km
        assertEquals(1_000.0, auto(EN(0.0, 1_900.0), 0.0, arc), 0.0)
        assertEquals(2_000.0, auto(EN(0.0, 1_900.0), 0.0, arc, replayViewport), 0.0)
        assertEquals(5_000.0, RangeAuto.desired(stepsM, 1_900.0, null)!!, 0.0)
        // 機首方位で回る: 東を向いて東 1.9km も前方
        assertEquals(1_000.0, auto(EN(1_900.0, 0.0), 90.0, arc), 0.0)
    }

    @Test
    fun arcBehindUsesTheSpaceUnderTheBottomOverlays() {
        // 真後ろ 1km: 下は描画の枠の下端まで使う。自機の下に 180.2 px（LIVE）/ 241.2 px（REPLAY）→ 2km（180 px）
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arc), 0.0)
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arc, replayViewport), 0.0)
        // 自機の位置「高め」（84dp）でも自機の下は 282.2 px / 343.2 px で、1km の段（360 px）には足りない → 2km
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arcHigh), 0.0)
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arcHigh, replayViewport), 0.0)
    }

    @Test
    fun arcRightBehind() {
        // 右後ろ（右回り 126°）4.19km: 5km の段で (604.1, 1171.5)（LIVE）/ (604.1, 1110.5)（REPLAY）。
        // 操作列より下なので右は画面の右端まで使える → 5km
        assertEquals(5_000.0, auto(at(126.0, 4_190.0), 0.0, arc), 0.0)
        assertEquals(5_000.0, auto(at(126.0, 4_190.0), 0.0, arc, replayViewport), 0.0)
        // ほぼ真後ろ（右回り 166°、REPLAY 0:01 の展望台）4.19km: 5km の段では自機の 292.7 px 下で、下の余裕
        // （180.2 / 241.2 px）に入らない → 10km
        assertEquals(10_000.0, auto(at(166.0, 4_190.0), 0.0, arc), 0.0)
        assertEquals(10_000.0, auto(at(166.0, 4_190.0), 0.0, arc, replayViewport), 0.0)
        // 「高め」なら自機の下は 282.2 px（LIVE）/ 343.2 px（REPLAY）→ REPLAY は 5km
        assertEquals(10_000.0, auto(at(166.0, 4_190.0), 0.0, arcHigh), 0.0)
        assertEquals(5_000.0, auto(at(166.0, 4_190.0), 0.0, arcHigh, replayViewport), 0.0)
    }

    @Test
    fun arcSideIsBelowTheColumn() {
        // 真横 800m: 自機の高さ（994.2 / 933.2）は操作列の範囲より下なので、左右とも 295.4 px → 1km（288 px）
        assertEquals(1_000.0, auto(EN(800.0, 0.0), 0.0, arc), 0.0)
        assertEquals(1_000.0, auto(EN(-800.0, 0.0), 0.0, arc), 0.0)
        assertEquals(1_000.0, auto(EN(800.0, 0.0), 0.0, arc, replayViewport), 0.0)
    }

    @Test
    fun arcRightAheadExcludesTheSideColumn() {
        // 右前方（右回り 30°）1.1km: 1km の段で (558, 651) … 操作列の高さの範囲で、操作列の左端（553.4）より右 → 2km
        assertEquals(2_000.0, auto(at(30.0, 1_100.0), 0.0, arc), 0.0)
        // 操作列がなければ 1km
        val noColumn = viewport.copy(reserved = HudInsets(top = 211f, bottom = 204f))
        assertEquals(1_000.0, auto(at(30.0, 1_100.0), 0.0, arc, noColumn), 0.0)
    }

    @Test
    fun northUpUsesTheSameFunction() {
        // North Up: 中心は (画面の横中央 360, 回避枠の縦中央 623)。方位サークルの半径 = min(360, 412) − 8dp = 346.4 px
        // 北 3km: 上まで 347.4 px → 2km の段（519.6 px）は入らず 5km
        assertEquals(5_000.0, auto(EN(0.0, 3_000.0), 0.0, northUp), 0.0)
        // 南 1km: 下まで 551.4 px → 1km（346.4 px）。500m の段（692.8 px）は入らない
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 0.0, northUp), 0.0)
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 123.0, northUp), 0.0)
        // 東 1km: 中心の高さは操作列の範囲。右は 193.4 px → 2km。西 1km: 左は 295.4 px → 2km
        assertEquals(2_000.0, auto(EN(1_000.0, 0.0), 0.0, northUp), 0.0)
        assertEquals(2_000.0, auto(EN(-1_000.0, 0.0), 0.0, northUp), 0.0)
        // REPLAY（中心 592.5、半径は同じ 346.4 px）も同じ段
        assertEquals(5_000.0, auto(EN(0.0, 3_000.0), 0.0, northUp, replayViewport), 0.0)
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 0.0, northUp, replayViewport), 0.0)
        assertEquals(2_000.0, auto(EN(1_000.0, 0.0), 0.0, northUp, replayViewport), 0.0)
    }

    @Test
    fun smallStepsForNearWaypoints() {
        // 50m / 100m の段: 前方 130m → 100m、前方 30m → 50m
        assertEquals(100.0, auto(EN(0.0, 130.0), 0.0, arc), 0.0)
        assertEquals(50.0, auto(EN(0.0, 30.0), 0.0, arc), 0.0)
    }

    @Test
    fun farWaypointGetsTheLargestStep() {
        assertEquals(50_000.0, auto(EN(0.0, 200_000.0), 0.0, arc), 0.0)
        assertEquals(50_000.0, auto(EN(0.0, -200_000.0), 0.0, northUp), 0.0)
    }

    @Test
    fun frameCenterOffsetAndScale() {
        // ARC 標準・機首 000: PAN の中心に置く点 (360, 623) は、自機 (360, 994.2) の前方 371.2 px。2km で 1 m = 0.18 px
        val px = viewport.pxPerM(2_000.0, arc)
        assertEquals(360.0 / 2_000.0, px, 1e-6)
        val c = viewport.frameCenterOffset(2_000.0, 0.0, arc)
        assertEquals(0.0, c.e, 0.5)
        assertEquals(371.2 / px, c.n, 0.5)
        // 機首 090 なら、前方は東
        val east = viewport.frameCenterOffset(2_000.0, 90.0, arc)
        assertEquals(371.2 / px, east.e, 0.5)
        assertEquals(0.0, east.n, 0.5)
        // North Up は自機がその点
        val nu = viewport.frameCenterOffset(2_000.0, 45.0, northUp)
        assertEquals(0.0, hypot(nu.e, nu.n), 1e-6)
    }

    @Test
    fun engineUsesTheViewportWhenKnown() {
        val lat0 = 33.5
        val lon0 = 133.0
        val e = NavEngine(NavSettings(initialRangeKm = 1.0), sourceKind = SourceKind.LIVE)
        // 北へ 1.9km の WP。方位がないので北が上
        e.setWaypoints(listOf(Waypoint("A", lat0 + 1_900.0 / 111_195.0, lon0)))
        e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0), 0)
        // 画面が分からないうちは距離で判定（5km）
        assertEquals(5_000.0, e.state.rangeM, 0.0)
        // 画面が分かれば、前方の広さで 1km（狭める方向なので 5 秒待つ）
        e.setViewport(viewport)
        e.onTick(5_000)
        assertEquals(1_000.0, e.state.rangeM, 0.0)
    }
}
