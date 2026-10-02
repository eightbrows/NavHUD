package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.PanView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 地図に表示を重ねたときの枠（§6.1）: 矢印と AUTO の枠（TargetFrame）、矢印を出す条件、数値の表示・ボタン類に重なる WP の名前。
 * 寸法はエミュレータで測った値（LIVE）: 描画の枠 720×1239 px、上 211 px（上部バー 68 px・数値 143 px）、
 * 右の操作列 102 px（回避枠の縦中央 y 470..776）、下 204 px（WP 列 68 px・プロファイル・ナビゲーションバー）。描画の寸法は密度 1。
 */
class OverlayFrameTest {

    private val rect = HudRect(0f, 0f, 720f, 1239f)
    private val reserved = HudInsets(top = 211f, right = 102f, bottom = 204f, rightSpan = 470f..776f)
    private val m = HudMetrics()
    private val mPerDegLat = 111_195.0
    private val lat0 = 33.5
    private val lon0 = 133.0

    // 数値の表示（上部バーの下 68..211 px）と、ボタン類（上部バー・操作列・WP 列）
    private val bands = listOf(68f..211f)
    private val buttons = listOf(
        HudRect(0f, 0f, 720f, 68f),
        HudRect(618f, 470f, 720f, 776f),
        HudRect(0f, 1035f, 720f, 1103f),
    )

    private fun wp(name: String, northM: Double, eastM: Double = 0.0, reached: Boolean = false) = Waypoint(
        name, lat0 + northM / mPerDegLat, lon0 + eastM / (mPerDegLat * Math.cos(Math.toRadians(lat0))), reached = reached,
    )

    private fun state(wps: List<Waypoint>, next: Int, rangeM: Double, mode: DisplayMode = DisplayMode.ARC) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = lat0, lon = lon0),
        noFix = false,
        heading = Heading(0f, HeadingSrc.GPS),
        waypoints = wps,
        nextWpIndex = next,
        settings = NavSettings(displayMode = mode),
        rangeM = rangeM,
    )

    private fun build(s: NavState) = HudSceneBuilder.build(s, rect, m, reserved, bands, buttons)

    @Test
    fun targetFrameHasANotchOnlyBesideTheColumn() {
        val f = HudSceneBuilder.targetFrame(rect, reserved)
        // 上は数値の下端、左・下は描画の枠
        assertEquals(HudRect(0f, 211f, 720f, 1239f), f.outer)
        // 操作列の高さの範囲（470..776）では右は 618 まで、それより上・下では画面の右端まで
        assertFalse(f.contains(P(650f, 600f)))
        assertTrue(f.contains(P(600f, 600f)))
        assertTrue(f.contains(P(650f, 300f)))
        assertTrue(f.contains(P(650f, 1100f)))
        // 数値の下は枠の外
        assertFalse(f.contains(P(300f, 150f)))
    }

    @Test
    fun rayHitsTheColumnEdgeOrTheScreenEdge() {
        val f = HudSceneBuilder.targetFrame(rect, reserved)
        // 操作列の高さから真右 → 操作列の左端
        val right = f.rayHit(P(360f, 600f), 90.0)
        assertEquals(618f, right.x, 1e-3f)
        assertEquals(600f, right.y, 1e-3f)
        // 自機の高さ（1011）は操作列より下 → 画面の右端
        assertEquals(720f, f.rayHit(P(360f, 1011f), 90.0).x, 1e-3f)
        // 右下 45° → 操作列の範囲（〜776）の下を通るので、画面の右端（または下端）まで届く
        assertTrue(f.rayHit(P(360f, 600f), 135.0).x > 618f)
        // 真上 → 数値の下端
        assertEquals(211f, f.rayHit(P(360f, 600f), 0.0).y, 1e-3f)
    }

    @Test
    fun nextWaypointUnderTheNumbersGetsAnArrowAndNoName() {
        // 縮尺 200m（1 m = 1.8 px）。自機は (360, 1011)（WP 列の上端 1035 から 24）。
        // 北 400m の WP は y = 291 … 数値（〜211）の下端より下で見えている → 印だけ
        val visible = build(state(listOf(wp("A", 400.0)), 0, 200.0))
        assertTrue(visible.arrows.isEmpty())
        assertEquals(1, visible.wpMarks.size)
        // 北 450m は y = 201（数値の下）→ 矢印を出す。印も描くが、名前は数値の表示に重なるので出さない
        val hidden = build(state(listOf(wp("A", 450.0)), 0, 200.0))
        assertEquals(1, hidden.arrows.size)
        assertEquals(211f + m.edgeInset, hidden.arrows.single().at.y, 1e-3f)
        assertNull(hidden.wpMarks.single().nameAt)
    }

    @Test
    fun nextWaypointUnderTheColumnGetsAnArrow() {
        // 東 155.6m・北 228.3m の WP は (640, 600) … 操作列の下 → 矢印を出す（印も描く、名前は操作列に重なるので出さない）
        val s = build(state(listOf(wp("C", 228.3, 155.6)), 0, 200.0))
        assertEquals(1, s.arrows.size)
        assertTrue(s.arrows.single().at.x <= 618f - m.edgeInset + 1e-3f)
        assertNull(s.wpMarks.single().nameAt)
    }

    @Test
    fun nextWaypointNearTheLeftEdgeIsOnlyAMark() {
        // 西 190m（x = 18、画面の縁から 22 以内だが見えている）→ 印だけで矢印なし
        val s = build(state(listOf(wp("W", 0.0, -190.0)), 0, 200.0))
        assertTrue(s.arrows.isEmpty())
        assertEquals(1, s.wpMarks.size)
    }

    @Test
    fun nextWaypointUnderTheBottomOverlaysIsOnlyAMark() {
        // 南 60m（y = 1119、プロファイルの下。矢印の枠の中）→ 下に重ねた表示の下も見えている扱いで、印だけ（D02 の確認 1-A）。
        // 名前（印の上 y = 1101）は WP 列に重なるので出さない（D02 の確認 2-A）
        val s = build(state(listOf(wp("S", -60.0)), 0, 200.0))
        assertTrue(s.arrows.isEmpty())
        assertNull(s.wpMarks.single().nameAt)
        // ボタン類を渡さなければ名前を出す
        val plain = HudSceneBuilder.build(state(listOf(wp("S", -60.0)), 0, 200.0), rect, m, reserved, bands)
        assertNotNull(plain.wpMarks.single().nameAt)
    }

    @Test
    fun namesOverTheNumbersAndButtonsAreHidden() {
        // 到達済みの WP（北 450m、印は y = 201、名前は y = 183 … 数値の範囲）: 印は描くが名前は出さない
        val wps = listOf(wp("R", 450.0, reached = true), wp("N", 300.0))
        val s = build(state(wps, 1, 200.0))
        assertNull(s.wpMarks.single { it.name == "R" }.nameAt)
        // 数値の範囲を渡さなければ名前を出す
        val plain = HudSceneBuilder.build(state(wps, 1, 200.0), rect, m, reserved)
        assertNotNull(plain.wpMarks.single { it.name == "R" }.nameAt)
        // 操作列に重なる名前（到達済みの WP を (640, 600) に）も出さない
        val col = listOf(wp("R", 228.3, 155.6, reached = true), wp("N", 300.0))
        assertNull(build(state(col, 1, 200.0)).wpMarks.single { it.name == "R" }.nameAt)
        assertNotNull(HudSceneBuilder.build(state(col, 1, 200.0), rect, m, reserved, bands).wpMarks.single { it.name == "R" }.nameAt)
    }

    @Test
    fun centersAreTheScreenCenterHorizontally() {
        // North Up: 中心は (360, 回避枠の縦中央 (211 + 1035) / 2 = 623)。PAN も同じ点
        val nu = build(state(listOf(wp("A", 100.0)), 0, 1_000.0, DisplayMode.NORTH_UP))
        assertEquals(P(360f, 623f), nu.ownShip!!.at)
        val pan = build(state(listOf(wp("A", 100.0)), 0, 1_000.0).copy(pan = PanView(lat0, lon0, 0.0)))
        assertEquals(360f, pan.ownShip!!.at.x, 1e-3f)
        assertEquals(623f, pan.ownShip!!.at.y, 1e-3f)
    }

    @Test
    fun northUpCircleReachesTheScreenEdge() {
        // 方位サークルの半径 = min(画面の幅の半分 360, 回避枠の高さの半分 412) − 8 = 352。操作列とは重なってよい
        val nu = build(state(listOf(wp("A", 100.0)), 0, 1_000.0, DisplayMode.NORTH_UP))
        assertTrue(nu.arcs.any { kotlin.math.abs(it.radius - 352f) < 1e-2f })
        // E の文字はサークルの内側（x = 360 + 352 − 目盛り − 間隔）
        assertEquals(360f + 352f - m.tickMajor - m.labelGap, nu.labels.single { it.text == "E" }.at.x, 1e-2f)
    }
}
