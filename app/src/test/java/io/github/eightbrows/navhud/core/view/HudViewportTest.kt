package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestSettings
import io.github.eightbrows.navhud.core.MeasuredScreen
import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.geo.EN
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.RangeAuto
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.desired
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    private val k = MeasuredScreen.DENSITY
    private val m = HudMetrics().scaled(k)
    private val rect = MeasuredScreen.RECT
    private val viewport = HudViewport(rect, m, MeasuredScreen.LIVE)
    private val replayViewport = HudViewport(rect, m, MeasuredScreen.REPLAY)
    private val stepsM = RangeAuto.ALL_STEPS_KM.map { it * 1000 }
    // 寸法は自機の位置「標準」（24dp）で測った値（既定は D01 から「高め」。§9）
    private val arc = NavSettings(displayMode = DisplayMode.ARC, ownshipPosition = OwnshipPosition.STANDARD)
    private val arcHigh = arc.copy(ownshipPosition = OwnshipPosition.HIGH)
    private val northUp = NavSettings(displayMode = DisplayMode.NORTH_UP, ownshipPosition = OwnshipPosition.STANDARD)

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
    fun fitsWithTheZoomInMargin() {
        // 前方 1.9km は 1km の段で上へ 684 px（収まる範囲は 718.6 px）。1.25 倍遠く（855 px）にすると収まらない
        assertTrue(viewport.fits(1_000.0, EN(0.0, 1_900.0), 0.0, arc))
        assertFalse(viewport.fits(1_000.0, EN(0.0, 1_900.0), 0.0, arc, spread = 1.25))
        assertTrue(viewport.fits(2_000.0, EN(0.0, 1_900.0), 0.0, arc, spread = 1.25))
    }

    @Test
    fun separationOfVisibleNeighbours() {
        // 最小の間隔は 40dp = 68 px。前方 300m と 400m（100m 離れた2つ）
        val pair = listOf(EN(0.0, 300.0), EN(0.0, 400.0))
        // 1km の段: 36 px → 近すぎる。500m の段: 72 px → 区別できる。200m の段: 180 px
        assertFalse(viewport.separated(1_000.0, pair, 0.0, arc))
        assertTrue(viewport.separated(500.0, pair, 0.0, arc))
        assertTrue(viewport.separated(200.0, pair, 0.0, arc))
        // 描画の枠の外にある2つは数えない（前方 3km と 3.05km は 1km の段で上へ 1080 px、画面の外）
        assertTrue(viewport.separated(1_000.0, listOf(EN(0.0, 3_000.0), EN(0.0, 3_050.0)), 0.0, arc))
        // 1つ目が見えていて（上へ 986 px、y = 8）、2つ目が画面の外（y = −14）でも数えない
        assertTrue(viewport.separated(1_000.0, listOf(EN(0.0, 2_740.0), EN(0.0, 2_800.0)), 0.0, arc))
    }

    /** D02 までの既定の AUTO（詳細の限度 100m・広域の限度 1km の段）で、自機から見た東 m・北 m の WP（順に）を置いて t 秒まで1秒ごとに進める。 */
    private fun autoRange(seconds: Int, vararg wps: EN, initialKm: Double = 1.0): Double {
        val lat0 = TestGeo.LAT0
        val lon0 = TestGeo.LON0
        val e = NavEngine(TestSettings.BEFORE_D03.copy(initialRangeKm = initialKm), sourceKind = SourceKind.LIVE)
        e.setViewport(viewport)
        e.setWaypoints(wps.mapIndexed { i, p -> Waypoint("W$i", TestGeo.lat(p.n), TestGeo.lon(p.e)) })
        var r = e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0), 0).rangeM
        for (t in 1..seconds) r = e.onTick(t * 1_000L).rangeM
        return r
    }

    @Test
    fun autoRulesWithTheMeasuredScreen() {
        // 遠い次の WP（ほぼ真後ろ 4.19km）: 広域の限度の 1km の段（R1 500m）で止まり、矢印で示す
        assertEquals(1_000.0, autoRange(30, at(166.0, 4_190.0)), 0.0)
        assertEquals(1_000.0, autoRange(30, at(166.0, 4_190.0), initialKm = 0.1), 0.0)
        // 前方 1.9km: 1km の段に収まる。500m の段には 1.25 倍で収まらないので 1km の段のまま
        assertEquals(1_000.0, autoRange(30, EN(0.0, 1_900.0)), 0.0)
        // 前方 140m: 1段ずつ、それぞれ 5 秒待って詳細にする（5 秒で 500m、11 秒で 200m の段）。100m の段へは、
        // 次の WP が 1.3 × 200m の段の R1（100m）= 130m 以内になるまで詳細にしない
        assertEquals(500.0, autoRange(5, EN(0.0, 140.0)), 0.0)
        assertEquals(200.0, autoRange(16, EN(0.0, 140.0)), 0.0)
        assertEquals(200.0, autoRange(60, EN(0.0, 140.0)), 0.0)
        // 前方 120m（130m 以内）: 200m の段からさらに 5 秒で 100m の段
        assertEquals(200.0, autoRange(16, EN(0.0, 120.0)), 0.0)
        assertEquals(100.0, autoRange(60, EN(0.0, 120.0)), 0.0)
        // 近い2つ（前方 300m と、その 100m 先）: 1km の段では 36 px で近すぎるので、すぐ 500m の段（72 px）
        assertEquals(500.0, autoRange(0, EN(0.0, 300.0), EN(0.0, 400.0)), 0.0)
        // 近い2つが遠くにある（前方 800m と、その 50m 先）: 1km の段（18 px）では近すぎるので 500m の段へ詳細にする。
        // 500m の段（36 px）でもまだ近いが、200m の段では次の WP が枠に収まらないので、500m の段で止める
        assertEquals(500.0, autoRange(30, EN(0.0, 800.0), EN(0.0, 850.0)), 0.0)
        // 次の WP がない: 中央の段（100m / 200m / 500m / 1km のうち広域の方の 500m の段、R1 250m）
        assertEquals(500.0, autoRange(0), 0.0)
    }

    /**
     * 10 m/s で北へ走り、405m 北の A に到達（到達半径 100m なので 31 秒目）して、そのまま走り続ける。次は 5km 北の B。各秒の段を返す。
     * A を通り過ぎるのは 42 秒目（40〜41 秒目の間に A の横を通り、42 秒目に 15m 離れた）
     */
    private fun passRun(holdSec: Int): List<Double> {
        val lat0 = TestGeo.LAT0
        val lon0 = TestGeo.LON0
        val e = NavEngine(TestSettings.BEFORE_D03.copy(autoHoldAfterWpSec = holdSec, reachRadiusM = 100.0), sourceKind = SourceKind.LIVE)
        e.setViewport(viewport)
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(405.0), lon0), Waypoint("B", TestGeo.lat(5_000.0), lon0)))
        return (0..60L).map { t ->
            val s = e.onFix(Fix(timeMs = t * 1_000, lat = TestGeo.lat(t * 10.0), lon = lon0, speedMps = 10f, bearingDeg = 0f), t * 1_000)
            if (t == 31L) assertEquals(1, s.nextWpIndex)
            if (t == 30L) assertEquals(0, s.nextWpIndex)
            s.rangeM
        }
    }

    @Test
    fun autoHoldsAfterPassingAWaypoint() {
        // 既定（10 秒）: A に到達した 31 秒目から、通り過ぎる 42 秒目と、そこから 10 秒（51 秒目）までは段を動かさず、
        // 52 秒目から B へ向けて1段ずつ広域にする
        val r = passRun(10)
        val atReach = r[31]
        assertTrue(atReach < 1_000.0)
        for (t in 31..51) assertEquals("t=$t", atReach, r[t], 0.0)
        assertTrue(r[52] > atReach)
        // 0 秒: 通り過ぎたその刻み（42 秒目）から広域にする。通り過ぎるまでは変えない
        val z = passRun(0)
        for (t in 31..41) assertEquals("t=$t", z[31], z[t], 0.0)
        assertTrue(z[42] > z[41])
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
        val lat0 = TestGeo.LAT0
        val lon0 = TestGeo.LON0
        // AUTO の広域の限度は 20km の段（距離の判定と画面の判定の違いを見るため）
        val e = NavEngine(NavSettings(initialRangeKm = 1.0, autoMaxRangeKm = 20.0), sourceKind = SourceKind.LIVE)
        // 北へ 1.9km の WP。方位がないので北が上
        e.setWaypoints(listOf(Waypoint("A", TestGeo.lat(1_900.0), lon0)))
        // 画面が分からないうちは距離で判定（1.9km は 2km の段の 0.9 倍を超える）: 1段ずつ広域にして 5km
        assertEquals(2_000.0, e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0), 0).rangeM, 0.0)
        assertEquals(5_000.0, e.onTick(1_000).rangeM, 0.0)
        // 画面が分かれば、2km の段に 1.25 倍遠く（2375m、上へ 427.5 px）でも収まるので、5 秒待って1段詳細にする
        e.setViewport(viewport)
        assertEquals(5_000.0, e.onTick(5_999).rangeM, 0.0)
        assertEquals(2_000.0, e.onTick(6_000).rangeM, 0.0)
        // 1km の段には、1.9km は収まる（684 px）が 1.25 倍（855 px）は収まらないので、2km の段のまま
        assertEquals(2_000.0, e.onTick(60_000).rangeM, 0.0)
    }
}
