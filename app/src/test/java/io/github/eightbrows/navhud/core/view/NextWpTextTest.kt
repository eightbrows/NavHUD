package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.TestGeo
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.OwnshipPosition
import io.github.eightbrows.navhud.core.nav.PanView
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 次の WP の文字（距離・名前・方位）は、どんな場面でも消えない（§6.1）。
 * 画面内の WP の文字か、画面外の矢印の文字のどちらかが必ずあり、文字の塊は見える範囲（数値の下・WP ボタン列の上・操作列の外）に入る。
 * 重ねた表示はエミュレータの寸法に近い形（720×900、上 211、右の操作列 102、下 120）。
 */
class NextWpTextTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()
    private val sideTop = 330f
    private val sideBottom = 530f
    private val stripTop = 780f
    private val reserved = HudInsets(top = 211f, right = 102f, bottom = 120f, rightSpan = sideTop..sideBottom)
    private val numberBands = listOf(50f..211f)
    private val buttonBoxes = listOf(
        HudRect(0f, 0f, 720f, 50f),
        HudRect(618f, sideTop, 720f, sideBottom),
        HudRect(0f, stripTop, 720f, stripTop + 60f),
    )

    /** 文字を出してよい範囲: 数値の下（211）から WP ボタン列の上（780）まで、画面の左右の中。操作列の所は除く */
    private fun visible(b: HudSceneBuilder.Box): Boolean {
        val inside = b.c.x - b.hw >= rect.left - 0.5f && b.c.x + b.hw <= rect.right + 0.5f &&
            b.c.y - b.hh >= 211f - 0.5f && b.c.y + b.hh <= stripTop + 0.5f
        val underSide = b.c.x + b.hw > 618f + 0.5f && b.c.y + b.hh > sideTop && b.c.y - b.hh < sideBottom
        return inside && !underSide
    }

    private fun state(wps: List<Waypoint>, next: Int, mode: DisplayMode = DisplayMode.ARC, pan: PanView? = null, position: OwnshipPosition = OwnshipPosition.STANDARD) =
        NavState(
            nowMs = 0,
            fix = Fix(timeMs = 0, lat = TestGeo.LAT0, lon = TestGeo.LON0),
            noFix = false,
            heading = Heading(0f, HeadingSrc.GPS),
            waypoints = wps,
            nextWpIndex = next,
            settings = NavSettings(displayMode = mode, ownshipPosition = position),
            rangeM = 2_000.0,
            pan = pan,
        )

    private fun wp(name: String, northM: Double, eastM: Double) = Waypoint(name, TestGeo.lat(northM), TestGeo.lon(eastM))

    /** 次の WP の文字の塊（画面内の文字か矢印の文字）。なければ null */
    private fun nextText(s: NavState): HudSceneBuilder.Box? {
        val scene = HudSceneBuilder.build(s, rect, m, reserved, numberBands, buttonBoxes)
        val mark = scene.wpMarks.firstOrNull { it.ink == Ink.ACTIVE }
        mark?.nameAt?.let { return HudSceneBuilder.wpTextBox(mark.lines, it, m) }
        return scene.arrows.firstOrNull()?.let { HudSceneBuilder.arrowTextBox(it.lines, it.textAt, m) }
    }

    private fun assertShown(what: String, s: NavState) {
        val b = nextText(s)
        assertTrue("$what: 文字がない", b != null)
        assertTrue("$what: 見える範囲の外 $b", visible(b!!))
    }

    @Test
    fun underTheWpStrip() {
        // ARC 標準: 自機 (360, 756)。真後ろ・少し右後ろ 300m（WP ボタン列の下、見えている扱いの所）
        assertShown("真後ろ 300m", state(listOf(wp("峠の入口", -300.0, 0.0)), 0))
        assertShown("右後ろ 300m", state(listOf(wp("峠の入口", -300.0, 200.0)), 0))
    }

    @Test
    fun rightNextToTheOwnship() {
        // 自機のすぐ後ろ 10m・すぐ前 5m: 文字の上も下も自機の記号にかかる
        assertShown("後ろ 10m", state(listOf(wp("峠の入口", -10.0, 0.0)), 0))
        assertShown("前 5m", state(listOf(wp("峠の入口", 5.0, 0.0)), 0))
    }

    @Test
    fun justBelowTheNumbers() {
        // 数値の下端（211）のすぐ下: 文字を上に置くと数値にかかる
        assertShown("北 2900m", state(listOf(wp("ダム", 2_900.0, 0.0)), 0))
    }

    @Test
    fun besideTheSideColumn() {
        // 操作列（x 618〜）のすぐ左
        assertShown("右前", state(listOf(wp("展望台", 1_450.0, 1_330.0)), 0))
    }

    @Test
    fun nearTheLeftEdge() {
        // 画面の左端の近く（文字を印の真上に置くと左にはみ出す）
        assertShown("左前", state(listOf(wp("旧道（通行止め）", 1_000.0, -1_950.0)), 0))
    }

    @Test
    fun everywhereAroundInEveryMode() {
        // 自機のまわり 0〜14km を 15° ごと・いろいろな距離で。ARC（標準・高め・さらに高め）・North Up・PAN
        val distances = listOf(5.0, 20.0, 60.0, 150.0, 300.0, 600.0, 1_000.0, 1_500.0, 2_000.0, 2_500.0, 3_000.0, 4_000.0, 6_000.0, 14_000.0)
        val pan = PanView(TestGeo.lat(1_500.0), TestGeo.LON0, 0.0)
        val cases = listOf(
            "ARC 標準" to { w: Waypoint -> state(listOf(w), 0) },
            "ARC 高め" to { w: Waypoint -> state(listOf(w), 0, position = OwnshipPosition.HIGH) },
            "ARC さらに高め" to { w: Waypoint -> state(listOf(w), 0, position = OwnshipPosition.HIGHER) },
            "North Up" to { w: Waypoint -> state(listOf(w), 0, mode = DisplayMode.NORTH_UP) },
            "PAN" to { w: Waypoint -> state(listOf(w), 0, pan = pan) },
        )
        val failures = mutableListOf<String>()
        for ((name, make) in cases) for (d in distances) for (deg in 0 until 360 step 15) {
            val r = Math.toRadians(deg.toDouble())
            val w = wp("道の駅", d * kotlin.math.cos(r), d * kotlin.math.sin(r))
            val b = nextText(make(w))
            if (b == null) failures += "$name ${d}m ${deg}°: なし" else if (!visible(b)) failures += "$name ${d}m ${deg}°: 外 $b"
        }
        assertTrue("${failures.size} 件\n" + failures.take(40).joinToString("\n"), failures.isEmpty())
    }
}
