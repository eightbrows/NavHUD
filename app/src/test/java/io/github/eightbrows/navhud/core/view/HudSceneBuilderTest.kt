package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.hypot

class HudSceneBuilderTest {

    private val rect = HudRect(0f, 0f, 720f, 900f)
    private val m = HudMetrics()
    private val mPerDegLat = 111_195.0
    private val lat0 = 33.5
    private val lon0 = 133.0

    /** 自機から北へ northM、東へ eastM の地点。 */
    private fun wp(name: String, northM: Double, eastM: Double = 0.0, enabled: Boolean = true, reached: Boolean = false) =
        Waypoint(
            name,
            lat0 + northM / mPerDegLat,
            lon0 + eastM / (mPerDegLat * Math.cos(Math.toRadians(lat0))),
            enabled = enabled,
            reached = reached,
        )

    private fun state(
        headingDeg: Float? = 0f,
        wps: List<Waypoint> = emptyList(),
        next: Int? = null,
        mode: DisplayMode = DisplayMode.ARC,
        noFix: Boolean = false,
    ) = NavState(
        nowMs = 0,
        fix = Fix(timeMs = 0, lat = lat0, lon = lon0),
        noFix = noFix,
        heading = if (headingDeg == null) Heading.NONE else Heading(headingDeg, HeadingSrc.GPS),
        waypoints = wps,
        nextWpIndex = next,
        settings = NavSettings(displayMode = mode),
        // 2km 縮尺（ステップ4の既定）で座標を確かめる
        rangeM = 2_000.0,
    )

    private fun build(s: NavState) = HudSceneBuilder.build(s, rect, m)

    private fun assertP(expected: P, actual: P, tol: Float = 1e-3f) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
    }

    @Test
    fun arcOwnShipAtBottomCenterAndRingsTouchEdges() {
        val scene = build(state())
        assertEquals(P(360f, 900f - m.arcOriginFromBottom), scene.ownShip!!.at)
        assertEquals(0f, scene.ownShip!!.angleDeg)
        // 2km の距離環の半径 = 画面幅の半分
        assertTrue(scene.arcs.any { kotlin.math.abs(it.radius - 360f) < 1e-3 })
        // 全周の環（後ろ側も。下に重ねた表示の下も地図として見えるため）
        assertTrue(scene.arcs.all { it.startDeg == 0f && it.sweepDeg == 360f })
    }

    @Test
    fun arcCompassLabelsSitOnFrameEdge() {
        // 機首 000: N は上端の中央、E は右端、W は左端。S（後方）は出ない
        val labels = build(state(headingDeg = 0f)).labels.associateBy { it.text }
        val gap = m.tickMajor + m.labelGap
        assertP(P(360f, gap), labels.getValue("N").at)
        assertP(P(720f - gap, 876f), labels.getValue("E").at)
        assertP(P(gap, 876f), labels.getValue("W").at)
        assertFalse(labels.containsKey("S"))
    }

    @Test
    fun arcCompassLabelsRotateWithHeading() {
        // 機首 090: E が上端の中央、N は左端、S は右端
        val labels = build(state(headingDeg = 90f)).labels.associateBy { it.text }
        val gap = m.tickMajor + m.labelGap
        assertEquals(360f, labels.getValue("E").at.x, 1e-3f)
        assertEquals(gap, labels.getValue("E").at.y, 1e-3f)
        assertEquals(gap, labels.getValue("N").at.x, 1e-3f)
        assertEquals(720f - gap, labels.getValue("S").at.x, 1e-3f)
        assertFalse(labels.containsKey("W"))
    }

    @Test
    fun arcTicksAreOnTheFrameNotOnARing() {
        // 目盛りの外側の端はすべて表示枠の縁にある（同心円に沿わない）
        val o = P(360f, 876f)
        val ticks = build(state(headingDeg = 30f)).segments.filter { it.ink == Ink.SCALE }
        assertEquals(19, ticks.size) // -90°..+90° を 10° ごと
        for (t in ticks) {
            val e = 1e-2f
            val onEdge = abs(t.a.x) < e || abs(t.a.x - 720f) < e || abs(t.a.y) < e || abs(t.a.y - 900f) < e
            assertTrue("tick $t", onEdge)
        }
        val dists = ticks.map { hypot(it.a.x - o.x, it.a.y - o.y) }.toSet()
        assertTrue(dists.size > 5)
    }

    @Test
    fun bearingLinesEvery30Degrees() {
        val lines = build(state(headingDeg = 0f)).segments.filter { it.ink == Ink.BEARING_LINE }
        assertEquals(7, lines.size) // -90, -60, … , +90
        assertTrue(lines.all { it.a == P(360f, 876f) })
    }

    @Test
    fun northWaypointBecomesLeftEdgeArrowWhenHeadingEast() {
        // 機首 090 で北 5km の WP → 左端の矢印
        val scene = build(state(headingDeg = 90f, wps = listOf(wp("WP1", 5_000.0)), next = 0))
        assertTrue(scene.wpMarks.isEmpty())
        val arrow = scene.arrows.single()
        assertEquals(m.edgeInset, arrow.at.x, 1e-3f)
        // 自機と同じ高さの左端には方位目盛りの「N」があるので、三角は縁に沿って上下にずれる
        assertTrue(abs(arrow.at.y - 876f) >= m.pointerSize - 0.5f)
        assertTrue(abs(arrow.at.y - 876f) <= m.pointerSize * 4 + 0.5f)
        assertEquals(-90f, arrow.angleDeg, 0.1f)
        assertEquals(Ink.ACTIVE, arrow.ink)
        assertTrue(arrow.text, arrow.text.startsWith("WP1 5.00 km"))
        // 文字は矢印より内側
        assertTrue(arrow.textAt.x > arrow.at.x)
    }

    @Test
    fun nearbyWaypointIsDrawnOnScreen() {
        // 機首 000 で北 1km → 自機の真上、1km 環の上
        val scene = build(state(headingDeg = 0f, wps = listOf(wp("WP1", 1_000.0)), next = 0))
        val mark = scene.wpMarks.single()
        assertEquals(360f, mark.at.x, 0.5f)
        assertEquals(876f - 180f, mark.at.y, 0.5f)
        assertEquals(Ink.ACTIVE, mark.ink)
        assertTrue(scene.arrows.isEmpty())
        // 自機から次の WP への線（マゼンタ）
        val active = scene.segments.single { it.ink == Ink.ACTIVE }
        assertEquals(P(360f, 876f), active.a)
        assertEquals(mark.at, active.b)
    }

    @Test
    fun routeStyles() {
        val wps = listOf(
            wp("A", 300.0, reached = true),
            wp("B", 600.0),
            wp("C", 900.0, enabled = false),
            wp("D", 1_200.0),
        )
        val scene = build(state(wps = wps, next = 1))
        val route = scene.segments.filter { it.ink in setOf(Ink.WP, Ink.WP_DISABLED, Ink.WP_REACHED) }
        assertEquals(3, route.size)
        assertEquals(Ink.WP_REACHED, route[0].ink) // A → B（A は直前に到達した WP なので薄く）
        assertEquals(Ink.WP_DISABLED, route[1].ink)
        assertTrue(route[1].dashed)
        assertEquals(Ink.WP_DISABLED, route[2].ink)
        val inks = scene.wpMarks.associate { it.name to it.ink }
        assertEquals(Ink.WP_REACHED, inks["A"])
        assertEquals(Ink.ACTIVE, inks["B"])
        assertEquals(Ink.WP_DISABLED, inks["C"])
        assertEquals(Ink.WP, inks["D"])
        assertTrue(scene.wpMarks.single { it.name == "C" }.dashed)
    }

    @Test
    fun offScreenArrowsOnlyForEnabledUnreached() {
        val wps = listOf(
            wp("A", 20_000.0, reached = true),
            wp("B", 20_000.0, enabled = false),
            wp("C", -20_000.0),
        )
        val scene = build(state(headingDeg = 0f, wps = wps, next = 2))
        val arrow = scene.arrows.single()
        assertTrue(arrow.text.startsWith("C "))
        // 真後ろ → 下端
        assertEquals(900f - m.edgeInset, arrow.at.y, 1e-3f)
        assertEquals(180f, kotlin.math.abs(arrow.angleDeg), 0.1f)
    }

    @Test
    fun northUpCompassCardInsideOuterRing() {
        val scene = build(state(headingDeg = 45f, mode = DisplayMode.NORTH_UP))
        val o = P(360f, 450f)
        assertEquals(o, scene.ownShip!!.at)
        assertEquals(45f, scene.ownShip!!.angleDeg)
        // 方位サークル（縮尺）の半径 = min(画面の幅の半分 360, 高さの半分 450) − 余白
        val outer = 360f - m.northUpEdgeMargin
        // 全周の距離環（1km, 2km）。方位サークル（縮尺 2km = 352 px）の外も、描画の枠の四隅（576 px）に届くまで同じ間隔で描く（3km）
        assertEquals(3, scene.arcs.size)
        assertTrue(scene.arcs.all { it.sweepDeg == 360f })
        assertTrue(scene.arcs.any { abs(it.radius - outer) < 1e-3f })
        assertEquals(outer * 1.5f, scene.arcs.maxOf { it.radius }, 1e-3f)
        // 目盛りは 36 本、すべてサークルの内側（円から内側へ 長い目盛り 16 / 短い目盛り 8）
        val ticks = scene.segments.filter { it.ink == Ink.SCALE }
        assertEquals(36, ticks.size)
        for (t in ticks) {
            assertEquals(outer, hypot(t.a.x - o.x, t.a.y - o.y), 1e-2f)
            assertTrue(hypot(t.b.x - o.x, t.b.y - o.y) in (outer - m.tickMajor - 1e-2f)..(outer - m.tickMinor + 1e-2f))
        }
        // N は真上、長い目盛りの内側
        val n = scene.labels.single { it.text == "N" }
        assertEquals(360f, n.at.x, 1e-3f)
        assertEquals(450f - outer + m.tickMajor + m.labelGap, n.at.y, 1e-2f)
        // ラバーラインと機首方位の三角はサークルの縁まで
        val rubber = scene.segments.single { it.ink == Ink.OWNSHIP }
        assertEquals(outer, hypot(rubber.b.x - o.x, rubber.b.y - o.y), 1e-2f)
        assertEquals(outer, hypot(scene.pointers.single().tip.x - o.x, scene.pointers.single().tip.y - o.y), 1e-2f)
    }

    @Test
    fun noHeadingUsesNorthUpInArcAndRoundOwnShip() {
        val scene = build(state(headingDeg = null))
        assertNull(scene.ownShip!!.angleDeg)
        assertEquals(360f, scene.labels.single { it.text == "N" }.at.x, 1e-3f)
        // ラバーラインは引かない
        assertTrue(scene.segments.none { it.ink == Ink.OWNSHIP })
    }

    @Test
    fun noFixMakesPositionDependentPartsGray() {
        val scene = build(state(wps = listOf(wp("WP1", 1_000.0)), next = 0, noFix = true))
        assertEquals(Ink.STALE, scene.ownShip!!.ink)
        assertEquals(Ink.STALE, scene.wpMarks.single().ink)
        assertTrue(scene.segments.none { it.ink == Ink.ACTIVE || it.ink == Ink.OWNSHIP })
        // 目盛りはそのまま
        assertTrue(scene.segments.any { it.ink == Ink.SCALE })
    }

    @Test
    fun arrowsStayInsideReservedBands() {
        // 左にボタン列（84px）、下にリプレイ操作（46px）がある
        val reserved = HudInsets(left = 84f, bottom = 46f)
        fun arrow(w: Waypoint) = HudSceneBuilder.build(state(headingDeg = 90f, wps = listOf(w), next = 0), rect, m, reserved).arrows.single()
        // 機首 090 で北 → 左端。ボタン列の内側
        assertEquals(84f + m.edgeInset, arrow(wp("N", 5_000.0)).at.x, 1e-3f)
        // 機首 090 で南 → 右端（下の帯は関係ない）
        assertEquals(720f - m.edgeInset, arrow(wp("S", -5_000.0)).at.x, 1e-3f)
        // 真後ろ → 下端。下は描画の枠の下端（下に重ねた表示の下も地図として見えている扱い）
        val behind = HudSceneBuilder.build(state(headingDeg = 0f, wps = listOf(wp("B", -5_000.0)), next = 0), rect, m, reserved)
        assertEquals(900f - m.edgeInset, behind.arrows.single().at.y, 1e-3f)
    }

    @Test
    fun arrowTextIsInsideAndDoesNotOverlap() {
        // ほぼ同じ方向の画面外の WP 3つ: 矢印は次の WP（AAA）の分だけ
        val wps = listOf(wp("AAA", 5_000.0, 5_000.0), wp("BBB", 6_000.0, 6_100.0), wp("CCC", 7_000.0, 7_000.0))
        val arrows = build(state(headingDeg = 0f, wps = wps, next = 0)).arrows
        assertEquals(1, arrows.size)
        assertTrue(arrows.single().text.startsWith("AAA"))
        val frame = rect.inset(m.edgeInset)
        for (a in arrows) {
            // 文字は枠の内側
            val half = a.text.length * m.labelCharWidth / 2
            assertTrue(a.text, a.textAt.x - half >= frame.left - 1e-3f && a.textAt.x + half <= frame.right + 1e-3f)
            // 文字は矢印より自機側（自機は下にある）
            assertTrue(a.text, a.textAt.y > a.at.y)
        }
        for (i in arrows.indices) for (j in i + 1 until arrows.size) {
            val a = arrows[i]
            val b = arrows[j]
            val apart = abs(a.textAt.y - b.textAt.y) >= m.arrowLabelLine - 1e-3f ||
                abs(a.textAt.x - b.textAt.x) >= (a.text.length + b.text.length) * m.labelCharWidth / 2 - 1e-3f
            assertTrue("${a.text} / ${b.text}", apart)
        }
    }

    @Test
    fun visibleWaypointsAreNextNPlusLastReached() {
        val r = { n: String -> wp(n, 100.0, reached = true) }
        val u = { n: String -> wp(n, 100.0) }
        val wps = listOf(r("A"), r("B"), u("C"), u("D"), u("E"), u("F"))
        assertEquals(listOf(1, 2, 3, 4), HudSceneBuilder.visibleWaypoints(wps, 2, 3))
        assertEquals(listOf(1, 2), HudSceneBuilder.visibleWaypoints(wps, 2, 1))
        // 端を越えない
        assertEquals(listOf(1, 2, 3, 4, 5), HudSceneBuilder.visibleWaypoints(wps, 2, 10))
        // まだどれも到達していない
        assertEquals(listOf(0, 1, 2), HudSceneBuilder.visibleWaypoints(wps.map { it.copy(reached = false) }, 0, 3))
        // 全部到達したら、最後に到達した WP だけ
        assertEquals(listOf(5), HudSceneBuilder.visibleWaypoints(wps.map { it.copy(reached = true) }, null, 3))
        // 直前の WP が無効なら、その前の到達済みを探す
        val withDisabled = listOf(r("A"), wp("B", 100.0, enabled = false), u("C"))
        assertEquals(listOf(0, 2), HudSceneBuilder.visibleWaypoints(withDisabled, 2, 1))
        // 数えるのは目標（有効かつ未到達）だけ。間の無効 WP は数えずに描き、最後の目標の後ろの無効 WP は描かない
        val gap = listOf(u("C"), wp("X", 100.0, enabled = false), u("D"), wp("Y", 100.0, enabled = false), u("E"))
        assertEquals(listOf(0, 1, 2), HudSceneBuilder.visibleWaypoints(gap, 0, 2))
        assertEquals(listOf(0), HudSceneBuilder.visibleWaypoints(gap, 0, 1))
    }

    @Test
    fun sceneDrawsOnlyVisibleWaypoints() {
        val wps = listOf(
            wp("A", 100.0, reached = true), wp("B", 200.0, reached = true),
            wp("C", 300.0), wp("D", 400.0), wp("E", 500.0), wp("F", 600.0),
        )
        val s = state(wps = wps, next = 2).let { it.copy(settings = it.settings.copy(hudWpCount = 3)) }
        val scene = build(s)
        assertEquals(listOf("B", "C", "D", "E"), scene.wpMarks.map { it.name })
        assertEquals(Ink.WP_REACHED, scene.wpMarks.first().ink)
        // 線も描く範囲だけ（B→C, C→D, D→E）
        assertEquals(3, scene.segments.count { it.ink in setOf(Ink.WP, Ink.WP_REACHED, Ink.WP_DISABLED) })
    }

    @Test
    fun noFixNoWaypoints() {
        val s = state(wps = listOf(wp("WP1", 1_000.0)), next = 0).copy(fix = null)
        val scene = build(s)
        assertTrue(scene.wpMarks.isEmpty())
        assertTrue(scene.arrows.isEmpty())
    }

    @Test
    fun ringLabelsAreSmallAndUseK() {
        // 2km 縮尺: 1km ごとの距離環。文字は 1k / 2k で、方位目盛りより小さく薄い
        val rings = build(state()).labels.filter { it.small }
        assertTrue(rings.map { it.text }.containsAll(listOf("1k", "2k")))
        assertTrue(rings.all { it.ink == Ink.SCALE_DIM })
        assertTrue(build(state()).labels.filter { !it.small }.all { it.ink == Ink.SCALE })
    }

    @Test
    fun waypointNameAvoidsOwnShip() {
        val own = P(360f, 900f - m.arcOriginFromBottom)
        val ownBox = HudSceneBuilder.Box(own, m.ownShipClear, m.ownShipClear)
        // 離れた WP: 名前は印の上
        assertEquals(P(360f, 100f - m.wpNameOffset), HudSceneBuilder.placeWpName("A", P(360f, 100f), ownBox, m))
        // 自機のすぐ下の WP: 上に置くと自機に重なるので下
        val below = P(360f, own.y + 20f)
        assertEquals(P(360f, below.y + m.wpNameOffset), HudSceneBuilder.placeWpName("A", below, ownBox, m))
        // 自機とほぼ同じ位置（到達直後の WP）: 名前は描かない
        assertNull(HudSceneBuilder.placeWpName("峠の入口", P(own.x + 2f, own.y + 3f), ownBox, m))

        // シーン全体: 到達済みの WP が自機の 10m 後ろにあっても、名前は自機に重ならない
        val scene = build(state(wps = listOf(wp("峠の入口", -10.0, reached = true), wp("B", 5_000.0)), next = 1))
        val mark = scene.wpMarks.single { it.name == "峠の入口" }
        assertNull(mark.nameAt)
    }

    @Test
    fun arrowTextAvoidsCompassLabelsAndOwnShip() {
        // 西・東・後方・真上に遠い WP を1つずつ次の WP にする（画面外の矢印は次の WP の分だけ）:
        // 文字は方位目盛りの文字（W など）・自機・上部の三角・その矢印自身と重ならない
        val wps = listOf(
            wp("ダム", 200.0, -9_000.0),
            wp("道の駅", -9_000.0, 300.0),
            wp("終点", -3_000.0, -20_000.0),
            wp("東", -100.0, 9_000.0),
            // 真上の遠い WP: 文字は上部の三角（機首方位の印）にも重ならない
            wp("北", 30_000.0, 800.0),
        )
        for (mode in DisplayMode.entries) for (next in wps.indices) {
            val scene = build(state(wps = wps, next = next, mode = mode))
            assertEquals(1, scene.arrows.size)
            val obstacles = scene.labels.map { l ->
                HudSceneBuilder.Box(l.at, HudSceneBuilder.textHalfWidth(l.text, m) * (if (l.small) 1f else 13f / 11f) + 2f, m.compassLabelHalf)
            } + HudSceneBuilder.Box(scene.ownShip!!.at, m.ownShipClear, m.ownShipClear) +
                // 上部の三角（機首方位の印）
                scene.pointers.map { HudSceneBuilder.Box(P(it.tip.x, it.tip.y + it.sizePx / 2), it.sizePx * 0.7f, it.sizePx * 0.7f) }
            val texts = scene.arrows.map { HudSceneBuilder.Box(it.textAt, HudSceneBuilder.textHalfWidth(it.text, m), m.arrowLabelLine / 2) }
            for ((i, t) in texts.withIndex()) {
                assertTrue("$mode ${scene.arrows[i].text}", obstacles.none { it.overlaps(t) })
                // 自分の矢印にも重ならない
                assertFalse("$mode ${scene.arrows[i].text}", HudSceneBuilder.Box(scene.arrows[i].at, m.pointerSize, m.pointerSize).overlaps(t))
                assertTrue("$mode ${scene.arrows[i].text}", texts.filterIndexed { j, _ -> j != i }.none { it.overlaps(t) })
            }
        }
    }

    @Test
    fun arcDrawsFullWidthAndAvoidsOverlays() {
        // 地図 720×690、右の操作列 102 px、下の WP ボタン列 81.6 px → 回避枠は x 0..618、y 0..608.4
        val r = HudRect(0f, 0f, 720f, 690f)
        val reserved = HudInsets(right = 102f, bottom = 81.6f)
        // 東 20km の遠い WP（画面外の矢印）
        val scene = HudSceneBuilder.build(state(headingDeg = 0f, wps = listOf(wp("E", 0.0, 20_000.0)), next = 0), r, m, reserved)
        // 自機は画面の横中央、回避枠の下端（WP ボタン列の上端）から 24
        val own = P(360f, 608.4f - m.arcOriginFromBottom)
        assertP(own, scene.ownShip!!.at)
        // 基準の距離環（2km）は画面の左右端に接する
        assertTrue(scene.arcs.any { abs(it.radius - 360f) < 1e-3 })
        // 方位目盛りは、上端は回避枠（情報帯の下端）、左右は画面の縁（右は操作列の下に入ってよい）
        val labels = scene.labels.associateBy { it.text }
        val gap = m.tickMajor + m.labelGap
        assertP(P(360f, gap), labels.getValue("N").at)
        assertP(P(720f - gap, own.y), labels.getValue("E").at)
        // 画面外の矢印は回避枠の内側（操作列の左）
        assertEquals(618f - m.edgeInset, scene.arrows.single().at.x, 1e-3f)
        // 高め: 84
        val high = state(headingDeg = 0f).let { it.copy(settings = it.settings.copy(ownshipPosition = io.github.eightbrows.navhud.core.nav.OwnshipPosition.HIGH)) }
        assertEquals(608.4f - m.arcOriginFromBottomHigh, HudSceneBuilder.build(high, r, m, reserved).ownShip!!.at.y, 1e-3f)
    }

    @Test
    fun northUpIsCenteredOnScreenHorizontally() {
        // 回避枠は x 0..618、y 0..608.4。中心は横が画面の中央（360）、縦が回避枠の中央（304.2）
        // 方位サークル（縮尺）の半径 = min(画面の幅の半分 360, 回避枠の高さの半分 304.2) − 余白。右の操作列は見ない
        val r = HudRect(0f, 0f, 720f, 690f)
        val scene = HudSceneBuilder.build(state(mode = DisplayMode.NORTH_UP), r, m, HudInsets(right = 102f, bottom = 81.6f))
        assertP(P(360f, 304.2f), scene.ownShip!!.at)
        assertTrue(scene.arcs.any { abs(it.radius - (304.2f - m.northUpEdgeMargin)) < 1e-2 })
    }

    @Test
    fun panMovesTheViewAndFixesTheUpDirection() {
        // North Up・2km: PAN の中心は自機の北 1km。表示枠の中心 (360, 450) に置き、自機は 1km 南（1 m = 352 / 2000 = 0.176 px）
        val center = io.github.eightbrows.navhud.core.nav.PanView(lat0 + 1_000.0 / mPerDegLat, lon0, 0.0)
        val s = state(headingDeg = 0f, mode = DisplayMode.NORTH_UP, wps = listOf(wp("A", 1_500.0)), next = 0).copy(pan = center)
        val scene = build(s)
        val own = scene.ownShip!!.at
        assertP(P(360f, 450f + 176f), own, 0.2f)
        // WP（北 1.5km）は中心の 500 m 北
        assertP(P(360f, 450f - 88f), scene.wpMarks.single().at, 0.2f)
        // 自機から次の WP への線は自機から
        assertP(own, scene.segments.single { it.ink == Ink.ACTIVE }.a)
        // 距離環は自機を中心に全周。方位目盛りは表示枠の縁（中心から見た方向）。ラバーライン・三角はなし
        assertTrue(scene.arcs.all { it.center == own && it.sweepDeg == 360f })
        val gap = m.tickMajor + m.labelGap
        assertP(P(360f, gap), scene.labels.single { it.text == "N" }.at)
        assertTrue(scene.pointers.isEmpty())

        // ARC で東向きに固定した PAN: 機首が 180 になっても、自機の記号は PAN の上（090）から見て 90° 右
        val arcPan = state(headingDeg = 180f, wps = listOf(wp("A", 1_500.0)), next = 0)
            .copy(pan = io.github.eightbrows.navhud.core.nav.PanView(lat0, lon0, 90.0))
        val arcScene = build(arcPan)
        assertEquals(90f, arcScene.ownShip!!.angleDeg!!, 1e-3f)
        assertP(P(360f, 450f), arcScene.ownShip!!.at, 0.2f)
        // 北の WP は画面の左
        assertTrue(arcScene.wpMarks.single().at.x < 360f)
    }

    @Test
    fun panWithoutFixDrawsNoOwnShip() {
        val s = state().copy(fix = null, pan = io.github.eightbrows.navhud.core.nav.PanView(lat0, lon0, 0.0))
        assertNull(build(s).ownShip)
    }

    @Test
    fun ringLabelsGiveWayToCompassLabels() {
        // PAN で自機を表示枠の上の縁の近くに置く: 距離環の文字が方位目盛りの文字に重なるなら、距離環の文字を出さない
        val center = io.github.eightbrows.navhud.core.nav.PanView(lat0 - 2_400.0 / mPerDegLat, lon0 + 2_400.0 / (mPerDegLat * Math.cos(Math.toRadians(lat0))), 0.0)
        val scene = build(state(headingDeg = 0f, mode = DisplayMode.NORTH_UP).copy(pan = center))
        val compass = scene.labels.filter { !it.small }.map { HudSceneBuilder.Box(it.at, HudSceneBuilder.textHalfWidth(it.text, m) * 13f / 11f + 2f, m.compassLabelHalf) }
        val rings = scene.labels.filter { it.small }.map { HudSceneBuilder.Box(it.at, HudSceneBuilder.textHalfWidth(it.text, m) + 2f, m.compassLabelHalf) }
        assertTrue(rings.none { r -> compass.any { it.overlaps(r) } })
    }

    @Test
    fun arrowSlidesAlongTheEdgeAwayFromCompassLabels() {
        val frame = TargetFrame(HudRect(0f, 0f, 720f, 900f).inset(m.edgeInset), null)
        val label = HudSceneBuilder.Box(P(200f, m.edgeInset + 4f), 8f, m.compassLabelHalf)
        // 上の縁: 横にずらす
        val top = HudSceneBuilder.slideArrow(P(200f, m.edgeInset), frame, listOf(label), m)
        assertEquals(m.edgeInset, top.y, 1e-3f)
        assertTrue(abs(top.x - 200f) >= m.pointerSize - 1e-3f)
        assertFalse(label.overlaps(HudSceneBuilder.Box(top, m.pointerSize * 0.6f, m.pointerSize * 0.6f)))
        // 左の縁: 縦にずらす
        val side = HudSceneBuilder.Box(P(m.edgeInset + 4f, 400f), 8f, m.compassLabelHalf)
        val left = HudSceneBuilder.slideArrow(P(m.edgeInset, 400f), frame, listOf(side), m)
        assertEquals(m.edgeInset, left.x, 1e-3f)
        assertTrue(abs(left.y - 400f) >= m.pointerSize - 1e-3f)
        // 重ならなければそのまま
        assertEquals(P(500f, m.edgeInset), HudSceneBuilder.slideArrow(P(500f, m.edgeInset), frame, listOf(label), m))
    }

    @Test
    fun arrowTrianglesDoNotCoverCompassLabels() {
        // 西・北西・北・北東の遠い WP（ARC 機首 000 / North Up）: 三角が方位目盛りの文字に重ならない
        val wps = listOf(wp("W", 0.0, -20_000.0), wp("NW", 20_000.0, -20_000.0), wp("N", 30_000.0), wp("NE", 20_000.0, 20_000.0))
        for (mode in DisplayMode.entries) {
            val scene = build(state(wps = wps, next = 0, mode = mode).let { it.copy(settings = it.settings.copy(hudWpCount = 4)) })
            val compass = scene.labels.filter { !it.small }
                .map { HudSceneBuilder.Box(it.at, HudSceneBuilder.textHalfWidth(it.text, m) * 13f / 11f + 2f, m.compassLabelHalf) }
            for (a in scene.arrows) {
                val tri = HudSceneBuilder.Box(a.at, m.pointerSize * 0.6f, m.pointerSize * 0.6f)
                assertTrue("$mode ${a.text}", compass.none { it.overlaps(tri) })
            }
        }
    }

    @Test
    fun reachedWaypointNameIsHiddenInsteadOfMovedBelow() {
        val own = P(360f, 900f - m.arcOriginFromBottom)
        val ownBox = HudSceneBuilder.Box(own, m.ownShipClear, m.ownShipClear)
        val below = P(360f, own.y + 20f)
        // 未到達の WP は下へ、到達済みは描かない
        assertEquals(P(360f, below.y + m.wpNameOffset), HudSceneBuilder.placeWpName("A", below, ownBox, m))
        assertNull(HudSceneBuilder.placeWpName("A", below, ownBox, m, allowBelow = false))
        // PAN 中も同じ: 到達済みの WP が自機のすぐ後ろ
        val pan = io.github.eightbrows.navhud.core.nav.PanView(lat0 + 500.0 / mPerDegLat, lon0, 0.0)
        val s = state(headingDeg = 0f, wps = listOf(wp("峠", -40.0, reached = true), wp("B", 3_000.0)), next = 1).copy(pan = pan)
        assertNull(build(s).wpMarks.single { it.name == "峠" }.nameAt)
    }

    @Test
    fun trailsForReplayAndLive() {
        // トラック: 南 1000 m から自機の北 1000 m まで、100 m・10 秒ごと。今の Fix は自機の位置（時刻 100 秒）
        val pts = (0..20).map { io.github.eightbrows.navhud.core.nav.TrackPoint(lat0 + (it * 100.0 - 1_000.0) / mPerDegLat, lon0, it * 10_000L) }
        val base = state(headingDeg = 0f).let { it.copy(fix = it.fix!!.copy(timeMs = 100_000L)) }
        val replay = build(base.copy(sourceKind = io.github.eightbrows.navhud.core.nav.SourceKind.REPLAY, replayTrack = pts))
        val (all, done) = replay.trails
        assertEquals(Ink.TRACK, all.ink)
        assertEquals(21, all.points.size)
        // 再生済みは 100 秒まで（11 点）＋自機の位置。太い線
        assertEquals(Ink.TRACK_DONE, done.ink)
        assertEquals(12, done.points.size)
        assertP(replay.ownShip!!.at, done.points.last())
        assertTrue(done.widthDp > all.widthDp)
        // 1 km 南の点は自機の真下 180 px（2km 縮尺、1 m = 0.18 px）
        assertP(P(360f, 876f + 180f), all.points.first(), 0.2f)
        // LIVE: 起動してからの軌跡 ＋ 自機の位置。REPLAY のトラックは描かない
        val live = build(base.copy(sourceKind = io.github.eightbrows.navhud.core.nav.SourceKind.LIVE, replayTrack = pts, liveTrail = pts.take(5)))
        assertEquals(1, live.trails.size)
        assertEquals(6, live.trails.single().points.size)
        // 軌跡がなければ描かない
        assertTrue(build(base.copy(sourceKind = io.github.eightbrows.navhud.core.nav.SourceKind.LIVE)).trails.isEmpty())
    }

    @Test
    fun arrowAvoidsTheArcMarkerAndRubberLine() {
        // 機首 000 で真北 30km の WP: 矢印は上端の中央（方位マーカーの横）に来るので、マーカーとラバーラインの周りから横へずらす
        val scene = build(state(headingDeg = 0f, wps = listOf(wp("ダム", 30_000.0, 300.0)), next = 0))
        val arrow = scene.arrows.single()
        val marker = scene.pointers.single().tip
        assertEquals(m.edgeInset, arrow.at.y, 1e-3f)
        assertTrue("x=${arrow.at.x}", abs(arrow.at.x - marker.x) >= m.pointerSize * 2.6f - 1e-3f)
        // 横の縁の矢印は動かさない（方位マーカーから遠い）
        val west = build(state(headingDeg = 0f, wps = listOf(wp("W", 3_000.0, -30_000.0)), next = 0)).arrows.single()
        assertEquals(m.edgeInset, west.at.x, 1e-3f)
        // North Up・PAN では方位マーカーを避けない（ARC の上部中央のマーカーだけ）
        val nu = build(state(headingDeg = 0f, mode = DisplayMode.NORTH_UP, wps = listOf(wp("ダム", 30_000.0, 300.0)), next = 0))
        assertTrue(abs(nu.arrows.single().at.x - 360f) < m.pointerSize * 2.6f)
    }

    @Test
    fun arrowTextAvoidsOnScreenWaypointNames() {
        // 前方シーク後の場面に近い配置（機首 030、縮尺 5km）。道の駅（2.55km・方位 078、画面内）とダム（15.1km・方位 070、画面外）
        fun at(bearing: Double, d: Double) = Math.toRadians(bearing).let { d * Math.cos(it) to d * Math.sin(it) }
        val (n1, e1) = at(78.0, 2_550.0)
        val (n2, e2) = at(70.0, 15_100.0)
        // 道の駅は到達済み（直前の WP として薄く描く）、次の WP はダム（画面外の矢印）
        val s = state(headingDeg = 30f, wps = listOf(wp("道の駅", n1, e1, reached = true), wp("ダム", n2, e2)), next = 1).copy(rangeM = 5_000.0)
        val scene = build(s)
        val mark = scene.wpMarks.single()
        val name = HudSceneBuilder.Box(mark.nameAt!!, HudSceneBuilder.textHalfWidth(mark.name, m) * 13f / 11f, m.arrowLabelLine / 2)
        val markBox = HudSceneBuilder.Box(mark.at, m.pointerSize * 0.6f, m.pointerSize * 0.6f)
        val arrow = scene.arrows.single()
        val text = HudSceneBuilder.Box(arrow.textAt, HudSceneBuilder.textHalfWidth(arrow.text, m), m.arrowLabelLine / 2)
        assertFalse(name.overlaps(text))
        assertFalse(markBox.overlaps(text))
    }

    @Test
    fun onlyTheNextWaypointGetsAnArrow() {
        // 次の WP（1km 先）は画面内、ほかの WP は画面外: 矢印は1つも出ない（ARC・North Up・PAN とも）
        val wps = listOf(wp("A", 1_000.0), wp("B", 30_000.0), wp("C", -30_000.0, 5_000.0))
        val pan = io.github.eightbrows.navhud.core.nav.PanView(lat0 + 200.0 / mPerDegLat, lon0, 0.0)
        for (s in listOf(
            state(wps = wps, next = 0),
            state(wps = wps, next = 0, mode = DisplayMode.NORTH_UP),
            state(wps = wps, next = 0).copy(pan = pan),
        )) {
            val scene = build(s)
            assertTrue(scene.arrows.isEmpty())
            // 画面外の WP も、線は引く（画面の端で切れる）
            assertTrue(scene.segments.any { it.ink == Ink.WP })
        }
        // 次の WP が画面外なら、その分の矢印だけ（B の分）
        val far = build(state(wps = wps.map { it.copy() }.let { listOf(it[0].copy(reached = true), it[1], it[2]) }, next = 1))
        assertEquals(1, far.arrows.size)
        assertTrue(far.arrows.single().text.startsWith("B"))
        assertEquals(Ink.ACTIVE, far.arrows.single().ink)
    }

    @Test
    fun ringsReachTheCornersWithLabels() {
        // ARC・縮尺 1km（間隔 500 m、1 m = 0.36 px）: 自機 (360, 876) から一番遠い角（左上 / 右上、947 px）まで、全周に
        // 500 m ごとに描く → 500 m〜2.5 km の 5 本（3 km = 1080 px は角より外）
        val scene = build(state(headingDeg = 0f).copy(rangeM = 1_000.0))
        assertEquals(5, scene.arcs.size)
        assertTrue(scene.arcs.all { it.sweepDeg == 360f })
        assertEquals(listOf("500", "1k", "1.5k", "2k", "2.5k"), scene.labels.filter { it.small }.map { it.text })
        // North Up・縮尺 1km（最外周 312 px、間隔 156 px）: 角（576 px）まで 3 本 → 500 m / 1k / 1.5k
        val nu = build(state(mode = DisplayMode.NORTH_UP).copy(rangeM = 1_000.0))
        assertEquals(3, nu.arcs.size)
        assertTrue(nu.labels.filter { it.small }.map { it.text }.containsAll(listOf("500", "1k")))
    }
}
