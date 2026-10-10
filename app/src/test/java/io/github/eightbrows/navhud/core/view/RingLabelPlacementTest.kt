package io.github.eightbrows.navhud.core.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * 距離環の数字の位置（§6.1）: 左上・右上・左下・右下（前方と後方の 45°）の4か所。
 * 外なら距離環に沿って画面の中心に近い側へ寄せ、画面の中で止める。同じ点に寄ったものは1つにまとめる。
 * 並びは 左上・右上・左下・右下 の順。
 */
class RingLabelPlacementTest {

    private val screen = HudRect(0f, 0f, 720f, 900f)
    private val s45 = sqrt(0.5f)

    private fun assertP(expected: P, actual: P, tol: Float = 1e-2f) {
        assertEquals("x", expected.x, actual.x, tol)
        assertEquals("y", expected.y, actual.y, tol)
    }

    /** 点が距離環の上にある */
    private fun assertOnRing(c: P, r: Float, p: P) = assertEquals(r, HudGeometry.dist(c, p), 1e-2f)

    @Test
    fun usualViewIsUpperLeftAndUpperRight() {
        // 自機 (360, 876)、半径 180: 左上・右上 45°（画面の中なので、そのまま）
        val c = P(360f, 876f)
        val a = RingLabelPlacement.anchors(c, 180f, screen)
        assertEquals(4, a.size)
        assertP(P(360f - 180f * s45, 876f - 180f * s45), a[0])
        assertP(P(360f + 180f * s45, 876f - 180f * s45), a[1])
        // 角度は画面が基準: どの表示でも同じ（中心と半径だけで決まる）
        val nu = RingLabelPlacement.anchors(P(360f, 450f), 300f, screen)
        assertP(P(360f - 300f * s45, 450f - 300f * s45), nu[0])
        assertP(P(360f + 300f * s45, 450f - 300f * s45), nu[1])
    }

    @Test
    fun rearLabelsAreLowerLeftAndLowerRight() {
        // 後方: 中心から見て左下（225°）・右下（135°）。画面の中なら、そのまま（前方と合わせて4か所）
        val c = P(360f, 450f)
        val a = RingLabelPlacement.anchors(c, 300f, screen)
        assertEquals(4, a.size)
        assertP(P(360f - 300f * s45, 450f + 300f * s45), a[2])
        assertP(P(360f + 300f * s45, 450f + 300f * s45), a[3])
        // 前方の2つと、上下で対称
        assertP(P(a[0].x, 2 * c.y - a[0].y), a[2])
        assertP(P(a[1].x, 2 * c.y - a[1].y), a[3])
        assertEquals(225.0, RingLabelPlacement.BACK_LEFT_DEG, 0.0)
        assertEquals(135.0, RingLabelPlacement.BACK_RIGHT_DEG, 0.0)
    }

    @Test
    fun rearLabelsSlideAlongTheRingIntoTheScreen() {
        // 普段の ARC（自機 (360, 876) が画面の下の方）、半径 180: 左下・右下 (233 / 487, 1003) は下の外。
        // 画面の中心（上）に近い側へ回り、下端 y = 900 と交わる所（262.3°・97.7°）で止まる
        val c = P(360f, 876f)
        val a = RingLabelPlacement.anchors(c, 180f, screen)
        assertEquals(4, a.size)
        val dx = sqrt(180f * 180f - 24f * 24f)
        assertP(P(360f - dx, 900f), a[2])
        assertP(P(360f + dx, 900f), a[3])
        assertOnRing(c, 180f, a[2])
        assertOnRing(c, 180f, a[3])
        // 片側だけ外: 中心 (100, 500)、半径 200 の左下 (−41, 641) は左の外 → 左端 x = 0 と交わる所（210°）。右下 (241, 641) はそのまま
        val side = RingLabelPlacement.anchors(P(100f, 500f), 200f, screen)
        assertEquals(4, side.size)
        assertP(P(0f, 500f + 200f * sqrt(3f) / 2), side[2])
        assertP(P(100f + 200f * s45, 500f + 200f * s45), side[3])
        // PAN で自機が左端 (0, 450): 左下は外 → 真下（180°、左端の上）まで回る。右下はそのまま
        val edge = RingLabelPlacement.anchors(P(0f, 450f), 200f, screen)
        assertEquals(4, edge.size)
        assertP(P(0f, 650f), edge[2])
        assertP(P(200f * s45, 450f + 200f * s45), edge[3])
        // 自機が画面の上の外 (360, −300)、半径 500: 左下 (6.4, 53.6)・右下 (713.6, 53.6) は中。前方の2つは上の外 → 左端・右端との交点
        val above = RingLabelPlacement.anchors(P(360f, -300f), 500f, screen)
        assertEquals(4, above.size)
        assertP(P(360f - 500f * s45, -300f + 500f * s45), above[2])
        assertP(P(360f + 500f * s45, -300f + 500f * s45), above[3])
        assertTrue(above.all { screen.contains(it) })
    }

    @Test
    fun labelsThatLandOnTheSamePointAreMerged() {
        // 自機が画面の左の外 (−500, 450)、半径 600: 距離環は左端 x = 0 と 56.4°・123.6° で交わる。
        // 前方の2つは 56.4° に、後方の2つは 123.6° に寄る → 前方で1つ、後方で1つ
        val c = P(-500f, 450f)
        val a = RingLabelPlacement.anchors(c, 600f, screen)
        val dy = sqrt(600f * 600f - 500f * 500f)
        assertEquals(2, a.size)
        assertP(P(0f, 450f - dy), a[0])
        assertP(P(0f, 450f + dy), a[1])
        // 自機が画面の下の外 (360, 1200)、半径 400: 前方も後方も下端 y = 900 との交点に寄る → 左右に1つずつ（4つにはしない）
        val low = RingLabelPlacement.anchors(P(360f, 1200f), 400f, screen)
        assertEquals(2, low.size)
        // 同じ点でなければ、近くても両方出す（減らす決まりは無い）: 半径 500 では、前方は (6.4, 846)、後方は左端との交点 (0, 853)
        val near = RingLabelPlacement.anchors(P(360f, 1200f), 500f, screen)
        assertEquals(4, near.size)
        assertP(P(0f, 1200f - sqrt(500f * 500f - 360f * 360f)), near[2])
        assertP(P(720f, 1200f - sqrt(500f * 500f - 360f * 360f)), near[3])
    }

    @Test
    fun aStartPointInsideTheFrameAlwaysGetsItsLabel() {
        // 隠す決まりは無い: 4か所のうち枠の中にある点は、必ずそのまま出る
        val frame = HudRect(20f, 15f, 700f, 885f)
        for (cx in listOf(-100f, 0f, 200f, 360f, 650f, 720f)) for (cy in listOf(0f, 300f, 450f, 876f, 900f)) {
            for (r in listOf(50f, 180f, 400f, 900f)) {
                val c = P(cx, cy)
                val a = RingLabelPlacement.anchors(c, r, frame)
                for (deg in RingLabelPlacement.START_DEGS) {
                    val p = HudGeometry.pointAt(c, deg, r)
                    if (frame.contains(p)) assertTrue("$c $r $deg", a.any { HudGeometry.dist(it, p) < 1e-2f })
                }
            }
        }
    }

    @Test
    fun onlyOneSideIsOutside() {
        // 中心 (100, 500)、半径 200: 左上 (−41, 359) は画面の外、右上 (241, 359) は中
        val c = P(100f, 500f)
        val a = RingLabelPlacement.anchors(c, 200f, screen)
        assertEquals(4, a.size)
        // 左は画面の中心に近い側（時計回り）へ回り、左端 x = 0 と交わる所（330°: y = 500 − 200 cos 30° = 326.8）で止まる
        assertP(P(0f, 500f - 200f * sqrt(3f) / 2), a[0])
        assertOnRing(c, 200f, a[0])
        // 右はそのまま
        assertP(P(100f + 200f * s45, 500f - 200f * s45), a[1])
    }

    @Test
    fun panWithTheOwnshipAtTheEdge() {
        // PAN で自機が左端 (0, 450): 左上は外 → 真上（0°、左端の上）まで回る。右上はそのまま
        val c = P(0f, 450f)
        val a = RingLabelPlacement.anchors(c, 200f, screen)
        assertEquals(4, a.size)
        assertP(P(0f, 250f), a[0])
        assertP(P(200f * s45, 450f - 200f * s45), a[1])
    }

    @Test
    fun panWithTheOwnshipOutsideTheScreen() {
        // 自機が画面の左の外 (−500, 450)、半径 600: 距離環は左端 x = 0 と 56.4° と 123.6° で交わる。
        // 左上（315°）も右上（45°、(−76, 26)）も外。どちらも画面の中心（右）の側へ回り、同じ点（56.4°）に寄るので1つにまとめる
        // （後方の2つも同じように 123.6° に寄って1つ）
        val c = P(-500f, 450f)
        val a = RingLabelPlacement.anchors(c, 600f, screen)
        assertEquals(2, a.size)
        assertEquals(0f, a[0].x, 1e-2f)
        assertEquals(450f - sqrt(600f * 600f - 500f * 500f), a[0].y, 1e-2f)
        assertOnRing(c, 600f, a[0])
        // 自機が画面の下の外 (360, 1200)、半径 500: 左上 (6.4, 846) と右上 (713.6, 846) は中
        val below = RingLabelPlacement.anchors(P(360f, 1200f), 500f, screen)
        assertP(P(360f - 500f * s45, 1200f - 500f * s45), below[0])
        assertP(P(360f + 500f * s45, 1200f - 500f * s45), below[1])
        assertTrue(below.all { screen.contains(it) })
        // 半径 400: 左上・右上 (77, 917) は下の外 → 下端 y = 900 との交点（中心側へ回る）
        val low = RingLabelPlacement.anchors(P(360f, 1200f), 400f, screen)
        assertEquals(2, low.size)
        val dx = sqrt(400f * 400f - 300f * 300f)
        assertP(P(360f - dx, 900f), low[0])
        assertP(P(360f + dx, 900f), low[1])
    }

    @Test
    fun ringsThatNeverEnterTheScreenHaveNoLabels() {
        // 画面に全く入らない: 画面の外の小さい距離環、画面を内側に含む大きな距離環
        assertTrue(RingLabelPlacement.anchors(P(-1000f, 450f), 300f, screen).isEmpty())
        assertTrue(RingLabelPlacement.anchors(P(360f, 450f), 2_000f, screen).isEmpty())
        assertTrue(RingLabelPlacement.anchors(P(360f, 450f), 0f, screen).isEmpty())
    }

    @Test
    fun anchorsAreAlwaysOnTheRingAndInsideTheFrame() {
        // いろいろな中心と半径で: 出す数字は必ず距離環の上で、枠の中
        val frame = HudRect(20f, 15f, 700f, 885f)
        for (cx in listOf(-800f, -100f, 0f, 200f, 360f, 650f, 720f, 1300f)) for (cy in listOf(-600f, 0f, 300f, 876f, 900f, 1500f)) {
            for (r in listOf(50f, 180f, 400f, 900f, 1500f, 2500f)) {
                val c = P(cx, cy)
                for (p in RingLabelPlacement.anchors(c, r, frame)) {
                    assertTrue("$c $r $p", frame.contains(p))
                    assertTrue("$c $r $p", abs(HudGeometry.dist(c, p) - r) < 0.05f)
                }
            }
        }
    }
}
