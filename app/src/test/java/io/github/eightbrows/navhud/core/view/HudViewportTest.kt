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
 * 枠: 上は情報帯の下端、左・下は描画の枠、右は操作列のある高さの範囲だけ操作列の左端（それより下は画面の右端）。
 * 寸法はエミュレータで測った値（密度 1.7）: 描画の枠 720×1239 px、上の重ねた表示 198 px、右の操作列 102 px、
 * 下の重ねた表示 LIVE 285 px / REPLAY 346 px（リプレイの帯 36dp・WP 列 40dp・プロファイル 小 56dp・下部パネル・ナビゲーションバー）。
 */
class HudViewportTest {

    private val k = 1.7f
    private val m = HudMetrics().scaled(k)
    private val rect = HudRect(0f, 0f, 720f, 1239f)
    private val viewport = HudViewport(rect, m, HudInsets(top = 198f, right = 102f, bottom = 285f))
    private val replayViewport = HudViewport(rect, m, HudInsets(top = 198f, right = 102f, bottom = 346f))
    private val stepsM = RangeAuto.ALL_STEPS_KM.map { it * 1000 }
    private val arc = NavSettings(displayMode = DisplayMode.ARC)
    private val arcHigh = arc.copy(ownshipPosition = OwnshipPosition.HIGH)
    private val northUp = NavSettings(displayMode = DisplayMode.NORTH_UP)

    // 余白は 22 + 16 dp = 64.6 px。収まる範囲: x 64.6..655.4、y 262.6..1174.4。ただし操作列の高さの範囲
    // （LIVE y 133.4..1018.6、REPLAY ..957.6）では x 553.4 まで
    // ARC（標準）: 自機は画面の横中央、回避枠の下端から 24dp。LIVE (360, 913.2)、REPLAY (360, 852.2)。1 m = 360 / 縮尺 px
    private fun auto(target: EN, headingDeg: Double?, s: NavSettings, vp: HudViewport = viewport): Double =
        RangeAuto.desired(stepsM) { vp.fits(it, target, headingDeg, s) }!!

    /** 自機から見て、機首方位から右回りに relDeg の向き・distM 先の点（機首 000 なので北が前）。 */
    private fun at(relDeg: Double, distM: Double) =
        Math.toRadians(relDeg).let { EN(distM * Math.sin(it), distM * Math.cos(it)) }

    @Test
    fun arcAhead() {
        // 前方 1.9km: 上まで 650.6 px（LIVE）/ 589.6 px（REPLAY）あるので 2km（距離だけの判定なら 5km）
        assertEquals(2_000.0, auto(EN(0.0, 1_900.0), 0.0, arc), 0.0)
        assertEquals(2_000.0, auto(EN(0.0, 1_900.0), 0.0, arc, replayViewport), 0.0)
        assertEquals(5_000.0, RangeAuto.desired(stepsM, 1_900.0, null)!!, 0.0)
        // 機首方位で回る: 東を向いて東 1.9km も前方
        assertEquals(2_000.0, auto(EN(1_900.0, 0.0), 90.0, arc), 0.0)
    }

    @Test
    fun arcBehindUsesTheSpaceUnderTheBottomOverlays() {
        // 真後ろ 1km: 下は描画の枠の下端まで使う。自機の下に 261.2 px（LIVE）/ 322.2 px（REPLAY）→ 2km
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arc), 0.0)
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arc, replayViewport), 0.0)
        // 自機の位置「高め」（84dp）なら自機の下に 363.2 px → 1km（1km の段で 360 px）
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 0.0, arcHigh), 0.0)
    }

    @Test
    fun arcRightBehindIsNotLimitedByTheColumnBelowIt() {
        // 右後ろ（右回り 126°）4.19km: 5km の段で (604, 1090.5)（LIVE）。操作列の高さの範囲より下なので、右は画面の右端まで使える → 5km
        // （直す前は右を全体で操作列の左端にしていたので 10km になっていた）
        assertEquals(5_000.0, auto(at(126.0, 4_190.0), 0.0, arc), 0.0)
        assertEquals(5_000.0, auto(at(126.0, 4_190.0), 0.0, arc, replayViewport), 0.0)
    }

    @Test
    fun arcSideExcludesTheSideColumn() {
        // 真横 800m: 自機と同じ高さは操作列の範囲。右は操作列の左端まで（193.4 px）→ 2km、左は 295.4 px → 1km
        assertEquals(2_000.0, auto(EN(800.0, 0.0), 0.0, arc), 0.0)
        assertEquals(1_000.0, auto(EN(-800.0, 0.0), 0.0, arc), 0.0)
        // 操作列がなければ右も 1km
        val noColumn = viewport.copy(reserved = HudInsets(top = 198f, bottom = 285f))
        assertEquals(1_000.0, auto(EN(800.0, 0.0), 0.0, arc, noColumn), 0.0)
    }

    @Test
    fun northUpUsesTheSameFunction() {
        // North Up: 中心は (画面の横中央 360, 回避枠の縦中央 576)。最外周の半径 = 右の縁まで 258 − 81.6 = 176.4 px
        assertEquals(2_000.0, auto(EN(0.0, 3_000.0), 0.0, northUp), 0.0)
        assertEquals(500.0, auto(EN(0.0, -1_000.0), 0.0, northUp), 0.0)
        assertEquals(500.0, auto(EN(0.0, -1_000.0), 123.0, northUp), 0.0)
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
        // ARC 標準・機首 000: PAN の中心に置く点 (360, 576) は、自機 (360, 913.2) の前方 337.2 px。2km で 1 m = 0.18 px
        val px = viewport.pxPerM(2_000.0, arc)
        assertEquals(360.0 / 2_000.0, px, 1e-6)
        val c = viewport.frameCenterOffset(2_000.0, 0.0, arc)
        assertEquals(0.0, c.e, 0.5)
        assertEquals(337.2 / px, c.n, 0.5)
        // 機首 090 なら、前方は東
        val east = viewport.frameCenterOffset(2_000.0, 90.0, arc)
        assertEquals(337.2 / px, east.e, 0.5)
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
        // 画面が分かれば、前方の広さで 2km（狭める方向なので 5 秒待つ）
        e.setViewport(viewport)
        e.onTick(5_000)
        assertEquals(2_000.0, e.state.rangeM, 0.0)
    }
}
