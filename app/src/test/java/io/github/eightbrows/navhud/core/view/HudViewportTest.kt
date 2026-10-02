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
 * AUTO 縮尺: 次の WP を画面に投影して、targetFrame（上・左・右は回避枠、下は描画の枠の下端）に余白付きで収まる最小の段。
 * 地図は画面いっぱい（描画の枠）。重ねた表示: 右の操作列 60dp、下の WP ボタン列 48dp（REPLAY ではリプレイの帯 40dp も）。
 * 寸法はエミュレータの地図の一部（720×690 px、密度 1.7）。上部バー・下部パネルは省いて、下に重ねたものだけで確かめる。
 * 自機（ARC）は回避枠の下端から 24dp（高め 84dp）、画面の横中央。
 */
class HudViewportTest {

    private val k = 1.7f
    private val m = HudMetrics().scaled(k)
    private val rect = HudRect(0f, 0f, 720f, 690f)
    private val viewport = HudViewport(rect, m, HudInsets(right = 60 * k, bottom = 48 * k))
    private val replayViewport = HudViewport(rect, m, HudInsets(right = 60 * k, bottom = (48 + 40) * k))
    private val stepsM = RangeAuto.ALL_STEPS_KM.map { it * 1000 }
    private val arc = NavSettings(displayMode = DisplayMode.ARC)
    private val arcHigh = arc.copy(ownshipPosition = OwnshipPosition.HIGH)
    private val northUp = NavSettings(displayMode = DisplayMode.NORTH_UP)

    // 回避枠は x 0..618、y 0..608.4。targetFrame は y が 690 まで。収まる枠はそこから 64.6 px 内側（x 64.6..553.4、y 64.6..625.4）
    // ARC（標準）: 自機は (360, 567.6)（回避枠の下端から 24dp = 40.8 px）、1 m = 360 / 縮尺 px（画面の半幅）
    private fun auto(target: EN, headingDeg: Double?, s: NavSettings, vp: HudViewport = viewport): Double =
        RangeAuto.desired(stepsM) { vp.fits(it, target, headingDeg, s) }!!

    @Test
    fun arcAheadUsesTheTallUpperArea() {
        // 前方 1.9km: 上まで 503 px あるので 2km で収まる（距離だけの判定なら 5km）
        assertEquals(2_000.0, auto(EN(0.0, 1_900.0), 0.0, arc), 0.0)
        assertEquals(5_000.0, RangeAuto.desired(stepsM, 1_900.0, null)!!, 0.0)
        // 機首方位で回る: 東を向いて東 1.9km も前方
        assertEquals(2_000.0, auto(EN(1_900.0, 0.0), 90.0, arc), 0.0)
    }

    @Test
    fun arcBehindUsesTheDrawingFrameBottom() {
        // 真後ろ 1km: 下は描画の枠の下端まで使う（自機の下に 57.8 px）→ 10km
        assertEquals(10_000.0, auto(EN(0.0, -1_000.0), 0.0, arc), 0.0)
        // 自機の位置「高め」（84dp）なら下に 159.8 px → 5km
        assertEquals(5_000.0, auto(EN(0.0, -1_000.0), 0.0, arcHigh), 0.0)
    }

    @Test
    fun arcSideExcludesTheSideColumn() {
        // 自機は画面の横中央。右は操作列の分だけ狭い: 真横 800m → 右 2km、左 1km
        assertEquals(2_000.0, auto(EN(800.0, 0.0), 0.0, arc), 0.0)
        assertEquals(1_000.0, auto(EN(-800.0, 0.0), 0.0, arc), 0.0)
        // 操作列がなければ右も 1km
        val noColumn = viewport.copy(reserved = HudInsets(bottom = 48 * k))
        assertEquals(1_000.0, auto(EN(800.0, 0.0), 0.0, arc, noColumn), 0.0)
    }

    @Test
    fun replayBandRaisesTheOwnShip() {
        // REPLAY の帯（40dp）の分、回避枠の下端と自機（帯の上端から 24dp）が上がる。下の判定は描画の枠の下端のまま
        // 真後ろ 1km: LIVE は 10km、REPLAY は自機の下に 125.8 px あるので 5km
        assertEquals(5_000.0, auto(EN(0.0, -1_000.0), 0.0, arc, replayViewport), 0.0)
        // 前方 1.9km は REPLAY でも 2km（上の余裕は 435 px）
        assertEquals(2_000.0, auto(EN(0.0, 1_900.0), 0.0, arc, replayViewport), 0.0)
    }

    @Test
    fun northUpUsesTheSameFunction() {
        // North Up: 自機は回避枠の中央 (309, 304.2)、最外周の半径 222.6 px。機首方位には関係しない
        assertEquals(5_000.0, auto(EN(0.0, 3_000.0), 0.0, northUp), 0.0)
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 0.0, northUp), 0.0)
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 123.0, northUp), 0.0)
        // 左右は同じ（中心が回避枠の中央なので）
        assertEquals(1_000.0, auto(EN(1_000.0, 0.0), 0.0, northUp), 0.0)
        assertEquals(1_000.0, auto(EN(-1_000.0, 0.0), 0.0, northUp), 0.0)
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
        // ARC 標準・機首 000: PAN の中心に置く回避枠の中央 (309, 304.2) は、自機 (360, 567.6) から左 51 px・前方 263.4 px。
        // 2km で 1 m = 0.18 px
        val px = viewport.pxPerM(2_000.0, arc)
        assertEquals(360.0 / 2_000.0, px, 1e-6)
        val c = viewport.frameCenterOffset(2_000.0, 0.0, arc)
        assertEquals(-51.0 / px, c.e, 0.5)
        assertEquals(263.4 / px, c.n, 0.5)
        // 機首 090 なら、前方は東、左は北
        val east = viewport.frameCenterOffset(2_000.0, 90.0, arc)
        assertEquals(263.4 / px, east.e, 0.5)
        assertEquals(51.0 / px, east.n, 0.5)
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
        // 画面が分かれば、前方の広さで 2km（狭める方向なので 5 秒待つ）
        e.setViewport(viewport)
        e.onTick(5_000)
        assertEquals(2_000.0, e.state.rangeM, 0.0)
    }
}
