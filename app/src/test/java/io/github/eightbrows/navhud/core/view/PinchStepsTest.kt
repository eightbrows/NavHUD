package io.github.eightbrows.navhud.core.view

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** ピンチの開き具合 → 変える段の数（§6.12。1.25 倍で詳細へ、0.8 倍で広域へ、1段ごとに基準を取り直す）。 */
class PinchStepsTest {

    @Test
    fun firstSpanIsTheBaseAndSmallChangesDoNothing() {
        val p = PinchSteps()
        assertEquals(0, p.update(200f))
        // 1.25 倍未満・0.8 倍より大きい間は変えない
        assertEquals(0, p.update(240f))
        assertEquals(0, p.update(170f))
        assertEquals(0, p.update(249f))
    }

    @Test
    fun spreadingZoomsInOneStepEachTimeAndRebases() {
        val p = PinchSteps()
        p.update(200f)
        // 1.25 倍（250）で1段詳細へ。基準は 250 になる
        assertEquals(1, p.update(250f))
        assertEquals(0, p.update(300f))
        // 250 × 1.25 = 312.5 で、もう1段
        assertEquals(1, p.update(313f))
        assertEquals(0, p.update(320f))
    }

    @Test
    fun pinchingZoomsOutOneStepEachTime() {
        val p = PinchSteps()
        p.update(400f)
        // 0.8 倍（320）で1段広域へ。基準は 320 → 次は 256
        assertEquals(-1, p.update(320f))
        assertEquals(0, p.update(270f))
        assertEquals(-1, p.update(255f))
    }

    @Test
    fun aLargeJumpGivesSeveralSteps() {
        val p = PinchSteps()
        p.update(100f)
        // 1.6 倍: 1.25（1段）・1.5625（2段）を越えた。1.953 は越えていない
        assertEquals(2, p.update(160f))
        // 基準 156.25 から 0.5 倍弱（75）: 0.8（125）・0.64（100）・0.512（80）を下回った
        assertEquals(-3, p.update(75f))
    }

    @Test
    fun goingBackUndoesTheStep() {
        val p = PinchSteps()
        p.update(200f)
        assertEquals(1, p.update(260f))
        // 基準 250 の 0.8 倍 = 200（はじめの開き具合）まで戻すと1段広域へ
        assertEquals(0, p.update(210f))
        assertEquals(-1, p.update(199f))
    }

    @Test
    fun resetStartsANewBaseAndBadSpansAreIgnored() {
        val p = PinchSteps()
        p.update(200f)
        p.reset()
        // reset のあとの最初の値が新しい基準
        assertEquals(0, p.update(400f))
        assertEquals(0, p.update(450f))
        assertEquals(1, p.update(500f))
        // 0・負・数でない値は無視（基準も変えない）
        assertEquals(0, p.update(0f))
        assertEquals(0, p.update(-10f))
        assertEquals(0, p.update(Float.NaN))
        assertEquals(0, p.update(Float.POSITIVE_INFINITY))
        assertEquals(1, p.update(625f))
    }

    @Test
    fun spanIsTheMeanDistanceFromTheCentroid() {
        // 2本: 指の間隔の半分。向きによらない
        assertEquals(200f, PinchSteps.span(listOf(P(160f, 500f), P(560f, 500f))), 1e-3f)
        assertEquals(50f, PinchSteps.span(listOf(P(0f, 0f), P(60f, 80f))), 1e-3f)
        // 3本（正三角形の頂点、重心から 100）: 100
        val tri = (0 until 3).map { val a = Math.toRadians(it * 120.0); P((100 * kotlin.math.sin(a)).toFloat(), (-100 * kotlin.math.cos(a)).toFloat()) }
        assertEquals(100f, PinchSteps.span(tri), 1e-3f)
        // 1本以下は 0（update で無視される）
        assertEquals(0f, PinchSteps.span(listOf(P(1f, 2f))), 0f)
        assertEquals(0f, PinchSteps.span(emptyList()), 0f)
    }

    @Test
    fun rejectsRatiosThatWouldNeverStop() {
        assertThrows(IllegalArgumentException::class.java) { PinchSteps(zoomInRatio = 1f) }
        assertThrows(IllegalArgumentException::class.java) { PinchSteps(zoomOutRatio = 1f) }
        assertThrows(IllegalArgumentException::class.java) { PinchSteps(zoomOutRatio = 0f) }
    }
}
