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

/**
 * AUTO 縮尺: 次の WP を画面に投影して、表示枠（ボタン列を除き、余白付き）に収まる最小の段。
 * 画面はエミュレータ（720×790 px、密度 1.7、右にボタン列 84dp）と同じ寸法。
 */
class HudViewportTest {

    private val k = 1.7f
    private val m = HudMetrics().scaled(k)
    private val viewport = HudViewport(HudRect(0f, 0f, 720f, 790f), m, HudInsets(right = 84 * k))
    private val stepsM = RangeAuto.ALL_STEPS_KM.map { it * 1000 }
    private val arc = NavSettings(displayMode = DisplayMode.ARC)
    private val arcHigh = arc.copy(ownshipPosition = OwnshipPosition.HIGH)
    private val northUp = NavSettings(displayMode = DisplayMode.NORTH_UP)

    // ARC（標準）: 自機は (360, 637)、1 m = 360 / 縮尺 px。表示枠は x 64.6..512.4、y 64.6..725.4
    private fun auto(target: EN, headingDeg: Double?, s: NavSettings): Double =
        RangeAuto.desired(stepsM) { viewport.fits(it, target, headingDeg, s) }!!

    @Test
    fun arcAheadUsesTheTallUpperArea() {
        // 前方 3km: 上端まで 572 px あるので 2km で収まる（距離だけの判定なら 5km）
        assertEquals(2_000.0, auto(EN(0.0, 3_000.0), 0.0, arc), 0.0)
        assertEquals(5_000.0, RangeAuto.desired(stepsM, 3_000.0, null)!!, 0.0)
        // 機首方位で回る: 東を向いて東 3km も前方
        assertEquals(2_000.0, auto(EN(3_000.0, 0.0), 90.0, arc), 0.0)
    }

    @Test
    fun arcBehindNeedsAWiderStep() {
        // 真後ろ 1km: 自機の下は 88 px しかないので 5km まで広げる（距離だけの判定なら 2km で、画面外になる）
        assertEquals(5_000.0, auto(EN(0.0, -1_000.0), 0.0, arc), 0.0)
        assertEquals(2_000.0, RangeAuto.desired(stepsM, 1_000.0, null)!!, 0.0)
        // 自機の位置「高め」なら下に 190 px あるので 2km で収まる
        assertEquals(2_000.0, auto(EN(0.0, -1_000.0), 0.0, arcHigh), 0.0)
    }

    @Test
    fun arcSideExcludesTheButtonColumn() {
        // 真横 1km: 右はボタン列の分だけ狭いので 5km、左は 2km
        assertEquals(5_000.0, auto(EN(1_000.0, 0.0), 0.0, arc), 0.0)
        assertEquals(2_000.0, auto(EN(-1_000.0, 0.0), 0.0, arc), 0.0)
        // ボタン列がなければ右も 2km
        val noColumn = viewport.copy(reserved = HudInsets())
        assertEquals(2_000.0, RangeAuto.desired(stepsM) { noColumn.fits(it, EN(1_000.0, 0.0), 0.0, arc) }!!, 0.0)
    }

    @Test
    fun northUpUsesTheSameFunction() {
        // North Up: 自機は中央 (360, 395)、最外周の半径 278.4 px。機首方位には関係しない
        assertEquals(5_000.0, auto(EN(0.0, 3_000.0), 0.0, northUp), 0.0)
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 0.0, northUp), 0.0)
        assertEquals(1_000.0, auto(EN(0.0, -1_000.0), 123.0, northUp), 0.0)
        // 真横（右はボタン列の分だけ狭い）
        assertEquals(2_000.0, auto(EN(1_000.0, 0.0), 0.0, northUp), 0.0)
    }

    @Test
    fun farWaypointGetsTheLargestStep() {
        assertEquals(20_000.0, auto(EN(0.0, 100_000.0), 0.0, arc), 0.0)
        assertEquals(20_000.0, auto(EN(0.0, -100_000.0), 0.0, northUp), 0.0)
    }

    @Test
    fun engineUsesTheViewportWhenKnown() {
        val lat0 = 33.5
        val lon0 = 133.0
        val e = NavEngine(NavSettings(initialRangeKm = 1.0), sourceKind = SourceKind.LIVE)
        // 北へ 3km の WP。方位がないので北が上
        e.setWaypoints(listOf(Waypoint("A", lat0 + 3_000.0 / 111_195.0, lon0)))
        e.onFix(Fix(timeMs = 0, lat = lat0, lon = lon0), 0)
        // 画面が分からないうちは距離で判定（5km）
        assertEquals(5_000.0, e.state.rangeM, 0.0)
        // 画面が分かれば、前方の広さで 2km（狭める方向なので 5 秒待つ）
        e.setViewport(viewport)
        e.onTick(5_000)
        assertEquals(2_000.0, e.state.rangeM, 0.0)
    }
}
