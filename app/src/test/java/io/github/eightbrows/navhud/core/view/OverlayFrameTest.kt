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
 * 地図に表示を重ねたときの枠（§6.1）: 矢印と AUTO の枠（TargetFrame）、矢印を出す条件、数値の表示に重なる WP の名前。
 * 寸法はエミュレータで測った値（描画の枠 720×1239 px、上 198 px、右の操作列 102 px、下 285 px）。描画の寸法は密度 1。
 */
class OverlayFrameTest {

    private val rect = HudRect(0f, 0f, 720f, 1239f)
    private val reserved = HudInsets(top = 198f, right = 102f, bottom = 285f)
    private val m = HudMetrics()
    private val mPerDegLat = 111_195.0
    private val lat0 = 33.5
    private val lon0 = 133.0

    // 情報帯（上部バーの下 68..198 px）と下部パネル（下から ナビゲーションバー 41 px の上の 81 px）
    private val bands = listOf(68f..198f, 1117f..1198f)

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

    private fun build(s: NavState) = HudSceneBuilder.build(s, rect, m, reserved, bands)

    @Test
    fun targetFrameHasANotchOnlyBesideTheColumn() {
        val f = HudSceneBuilder.targetFrame(rect, reserved)
        // 上は情報帯の下端、左・下は描画の枠
        assertEquals(HudRect(0f, 198f, 720f, 1239f), f.outer)
        // 操作列の高さの範囲（198..954）では右は 618 まで、それより下では画面の右端まで
        assertFalse(f.contains(P(650f, 500f)))
        assertTrue(f.contains(P(600f, 500f)))
        assertTrue(f.contains(P(650f, 1100f)))
        // 情報帯の下は枠の外
        assertFalse(f.contains(P(300f, 150f)))
    }

    @Test
    fun rayHitsTheColumnEdgeOrTheScreenEdge() {
        val f = HudSceneBuilder.targetFrame(rect, reserved)
        val o = P(360f, 900f)
        // 真右 → 操作列の左端
        val right = f.rayHit(o, 90.0)
        assertEquals(618f, right.x, 1e-3f)
        assertEquals(900f, right.y, 1e-3f)
        // 右下 45° → 操作列の範囲（〜954）の下を通るので、画面の右端（または下端）まで届く
        val rightDown = f.rayHit(o, 135.0)
        assertTrue(rightDown.x > 618f)
        // 真上 → 情報帯の下端
        assertEquals(198f, f.rayHit(o, 0.0).y, 1e-3f)
    }

    @Test
    fun nextWaypointUnderTheInfoStripGetsAnArrowAndNoName() {
        // 縮尺 200m（1 m = 1.8 px）。自機は (360, 930)。北 400m の WP は y = 210 … 情報帯（〜198）のすぐ下で見えている → 印だけ
        val visible = build(state(listOf(wp("A", 400.0)), 0, 200.0))
        assertTrue(visible.arrows.isEmpty())
        assertEquals(1, visible.wpMarks.size)
        // 北 450m は y = 120（情報帯の下）→ 矢印を出す。印も描くが、名前は数値の表示に重なるので出さない
        val hidden = build(state(listOf(wp("A", 450.0)), 0, 200.0))
        assertEquals(1, hidden.arrows.size)
        assertEquals(198f + m.edgeInset, hidden.arrows.single().at.y, 1e-3f)
        assertNull(hidden.wpMarks.single().nameAt)
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
        // 南 60m（y = 1038、WP 列・プロファイルの下。矢印の枠の中）→ 下に重ねた表示の下も見えている扱いで、印だけ
        val s = build(state(listOf(wp("S", -60.0)), 0, 200.0))
        assertTrue(s.arrows.isEmpty())
        assertNotNull(s.wpMarks.single().nameAt)
    }

    @Test
    fun namesOverTheBottomPanelAreHidden() {
        // 到達済みの WP（南 115m、印は y = 1137、名前は印の上 y = 1119 … 下部パネルの範囲）: 印は描くが名前は出さない
        val s = build(state(listOf(wp("R", -115.0, reached = true), wp("N", 300.0)), 1, 200.0))
        val r = s.wpMarks.single { it.name == "R" }
        assertNull(r.nameAt)
        // 数値の範囲を渡さなければ名前を出す
        val plain = HudSceneBuilder.build(state(listOf(wp("R", -115.0, reached = true), wp("N", 300.0)), 1, 200.0), rect, m, reserved)
        assertNotNull(plain.wpMarks.single { it.name == "R" }.nameAt)
    }

    @Test
    fun centersAreTheScreenCenterHorizontally() {
        // North Up: 中心は (360, 回避枠の縦中央 576)。PAN も同じ点
        val nu = build(state(listOf(wp("A", 100.0)), 0, 1_000.0, DisplayMode.NORTH_UP))
        assertEquals(P(360f, 576f), nu.ownShip!!.at)
        val pan = build(state(listOf(wp("A", 100.0)), 0, 1_000.0).copy(pan = PanView(lat0, lon0, 0.0)))
        assertEquals(360f, pan.ownShip!!.at.x, 1e-3f)
        assertEquals(576f, pan.ownShip!!.at.y, 1e-3f)
    }
}
