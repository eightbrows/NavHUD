package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTO 縮尺の規則（§6.1）。画面の代わりに、距離で答える RangeProbe を使う:
 * 次の WP が distM 先にあり、段 r で「distM × spread ≤ r」なら収まる。sepMaxM 以下の段でだけ WP を区別できる。
 * 段は 50m〜5km、AUTO の下限・上限は既定（100m〜1km の段 = R1 50m〜500m）。
 */
class RangeSelectorTest {

    private val stepsKm = listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0)
    private val stepsM = listOf(500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0)

    private fun probe(distM: Double, sepMaxM: Double = Double.MAX_VALUE) = object : RangeProbe {
        override fun fits(rangeM: Double, spread: Double) = distM * spread <= rangeM
        override fun separated(rangeM: Double) = rangeM <= sepMaxM
        override val distanceM = distM
    }

    private fun selector(initialKm: Double) = RangeSelector(stepsKm, initialKm, auto = true)

    @Test
    fun desiredIsSmallestStepThatFits() {
        // 距離だけの判定（画面が分からないとき）: 縮尺 × 0.9 以内に収まる最小の段
        assertEquals(500.0, RangeAuto.desired(stepsM, 450.0, null)!!, 0.0)
        assertEquals(1_000.0, RangeAuto.desired(stepsM, 451.0, null)!!, 0.0)
        assertEquals(10_000.0, RangeAuto.desired(stepsM, 50_000.0, 10f)!!, 0.0)
        assertNull(RangeAuto.desired(stepsM, null, 10f))
        // 対地速度は受け取るが使わない
        assertEquals(RangeAuto.desired(stepsM, 700.0, 0f), RangeAuto.desired(stepsM, 700.0, 40f))
    }

    @Test
    fun initialStepAndSnapping() {
        assertEquals(1_000.0, RangeSelector(listOf(0.5, 1.0, 2.0), 1.0, auto = true).rangeM, 0.0)
        // 有効リストにない起動時の縮尺は近い段へ
        assertEquals(2_000.0, RangeSelector(listOf(0.5, 2.0, 5.0), 1.5, auto = true).rangeM, 0.0)
    }

    @Test
    fun limitsAreSnappedToTheUsedSteps() {
        // 既定: 下限 100m の段（R1 50m）、上限 1km の段（R1 500m）
        assertEquals(0.1 to 1.0, RangeAuto.limitsKm(NavSettings().rangeStepsKm, 0.1, 1.0))
        assertEquals(listOf(100.0, 200.0, 500.0, 1_000.0), selector(1.0).autoSteps)
        // 使う段にない下限・上限は、下限は上の段へ、上限は下の段へ寄せる
        assertEquals(0.2 to 0.5, RangeAuto.limitsKm(listOf(0.2, 0.5, 2.0), 0.1, 1.0))
        // 寄せる段がなければ使う段の端
        assertEquals(2.0 to 2.0, RangeAuto.limitsKm(listOf(2.0, 5.0), 0.1, 1.0))
        // 下限が上限より広ければ、上限にそろえる
        assertEquals(1.0 to 1.0, RangeAuto.limitsKm(stepsKm, 5.0, 1.0))
    }

    @Test
    fun stopsAtTheUpperLimit() {
        // 次の WP が 50km 先: 上限（1km の段）で止まり、それ以上は広げない（画面の端の矢印で示す）
        val r = selector(0.1)
        assertEquals(200.0, r.update(probe(50_000.0), 0), 0.0)
        assertEquals(500.0, r.update(probe(50_000.0), 1_000), 0.0)
        assertEquals(1_000.0, r.update(probe(50_000.0), 2_000), 0.0)
        assertEquals(1_000.0, r.update(probe(50_000.0), 3_000), 0.0)
        assertEquals(1_000.0, r.update(probe(50_000.0), 60_000), 0.0)
    }

    @Test
    fun widensImmediatelyOneStepAtATime() {
        // 800m 先: 200m の段から、1回に1段ずつすぐ広げる（200 → 500 → 1000）
        val r = selector(0.2)
        assertEquals(500.0, r.update(probe(800.0), 0), 0.0)
        assertEquals(1_000.0, r.update(probe(800.0), 0), 0.0)
        assertEquals(1_000.0, r.update(probe(800.0), 0), 0.0)
    }

    @Test
    fun narrowsOneStepAfterTheWait() {
        // 100m 先: 1km の段から、1段狭い段（500m）で 1.25 倍遠く（125m）でも収まる状態が 5 秒続いたら1段狭める
        val r = selector(1.0)
        assertEquals(1_000.0, r.update(probe(100.0), 0), 0.0)
        assertEquals(1_000.0, r.update(probe(100.0), 4_999), 0.0)
        assertEquals(500.0, r.update(probe(100.0), 5_000), 0.0)
        // 次の1段も、また 5 秒待つ
        assertEquals(500.0, r.update(probe(100.0), 5_001), 0.0)
        assertEquals(500.0, r.update(probe(100.0), 10_000), 0.0)
        assertEquals(200.0, r.update(probe(100.0), 10_001), 0.0)
        // 100m の段では 125m が収まらないので、200m の段で止まる
        assertEquals(200.0, r.update(probe(100.0), 60_000), 0.0)
    }

    @Test
    fun zoomInNeedsTheMargin() {
        // 1km の段で 450m 先: 500m の段に 1.25 倍（562.5m）は収まらないので狭めない
        val r = selector(1.0)
        for (t in 0..30L) assertEquals(1_000.0, r.update(probe(450.0), t * 1_000), 0.0)
        // 390m（× 1.25 = 487.5m）なら狭める
        r.update(probe(390.0), 40_000)
        assertEquals(500.0, r.update(probe(390.0), 45_000), 0.0)
    }

    @Test
    fun neverNarrowerThanTheLowerLimit() {
        // 10m 先でも、下限（100m の段）より狭めない（50m の段は使う段にあっても使わない）
        val r = selector(0.2)
        r.update(probe(10.0), 0)
        assertEquals(100.0, r.update(probe(10.0), 5_000), 0.0)
        r.update(probe(10.0), 6_000)
        assertEquals(100.0, r.update(probe(10.0), 60_000), 0.0)
    }

    @Test
    fun narrowsImmediatelyWhenWaypointsAreTooClose() {
        // 次の WP（150m 先）は 1km の段に収まるが、隣り合う目標を区別できるのは 200m 以下の段だけ: 待たずに1段ずつ狭める
        val r = selector(1.0)
        assertEquals(500.0, r.update(probe(150.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(200.0, r.update(probe(150.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(200.0, r.update(probe(150.0, sepMaxM = 200.0), 1_000), 0.0)
        // どの段でも区別できなければ、下限で止まる（50m 先なので下限の 100m の段にも収まる）
        val s = selector(1.0)
        repeat(5) { s.update(probe(50.0, sepMaxM = 0.0), 0) }
        assertEquals(100.0, s.rangeM, 0.0)
    }

    @Test
    fun closeWaypointsDoNotHideTheNextOne() {
        // 次の WP は 300m 先、区別できるのは 200m 以下の段だけ。200m の段では次の WP が収まらないので、
        // 1km → 500m の段までで止める（近すぎても、次の WP が見えなくなるほどは狭めない）
        val r = selector(1.0)
        assertEquals(500.0, r.update(probe(300.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(500.0, r.update(probe(300.0, sepMaxM = 200.0), 1_000), 0.0)
        assertEquals(500.0, r.update(probe(300.0, sepMaxM = 200.0), 60_000), 0.0)
        // 200m の段で次の WP が収まらなければ、近すぎても 500m の段へ広げる（狭めると見えなくなるので）
        val s = selector(0.2)
        assertEquals(500.0, s.update(probe(300.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(500.0, s.update(probe(300.0, sepMaxM = 200.0), 60_000), 0.0)
    }

    @Test
    fun noNextWaypointGoesToTheMiddleStep() {
        // 下限〜上限は 100m / 200m / 500m / 1km の4段。中央の2つのうち広い方（500m の段、R1 250m）へすぐ
        assertEquals(500.0, selector(5.0).update(null, 0), 0.0)
        assertEquals(500.0, selector(0.1).update(null, 0), 0.0)
        // 3段（上限 500m の段）なら真ん中の 200m の段
        val three = RangeSelector(stepsKm, 1.0, auto = true, autoMinKm = 0.1, autoMaxKm = 0.5)
        assertEquals(200.0, three.update(null, 0), 0.0)
    }

    @Test
    fun outsideTheLimitsGoesToTheEndAtOnce() {
        // 5km の段で AUTO: 1段ずつではなく、すぐ上限（1km の段）へ
        assertEquals(1_000.0, selector(5.0).update(probe(100.0), 0), 0.0)
        // 50m の段なら、すぐ下限（100m の段）へ
        assertEquals(100.0, selector(0.05).update(probe(10.0), 0), 0.0)
        // 設定で上限を変えたときも
        val r = selector(1.0)
        r.setAutoLimits(0.1, 0.2)
        assertEquals(200.0, r.update(probe(100.0), 0), 0.0)
    }

    @Test
    fun decideNowGoesToTheTargetAtOnce() {
        // PAN から戻ったとき・シークのあと: 1段ずつではなく、収まるいちばん狭い段へすぐ（区別できなければさらに狭める）
        val r = selector(0.1)
        r.decideNow()
        assertEquals(1_000.0, r.update(probe(800.0), 0), 0.0)
        r.decideNow()
        assertEquals(200.0, r.update(probe(150.0, sepMaxM = 200.0), 0), 0.0)
        // 狭めると次の WP（800m）が収まらないなら、区別できなくても収まる段のまま
        r.decideNow()
        assertEquals(1_000.0, r.update(probe(800.0, sepMaxM = 200.0), 0), 0.0)
    }

    @Test
    fun holdsAfterTheNextWaypointChanges() {
        // 次の WP が変わってから 10 秒（既定）は、広げる・狭める・範囲の外・次の WP なし、どれも動かさない
        val r = selector(0.2)
        r.holdForWpChange(1_000)
        assertEquals(200.0, r.update(probe(5_000.0), 1_000), 0.0)
        assertEquals(200.0, r.update(probe(5_000.0), 10_999), 0.0)
        assertEquals(200.0, r.update(null, 10_999), 0.0)
        // 10 秒たったら動く（1段ずつ）
        assertEquals(500.0, r.update(probe(5_000.0), 11_000), 0.0)
        assertEquals(1_000.0, r.update(probe(5_000.0), 11_001), 0.0)
        // 0 秒なら待たない（今までどおり）
        val z = selector(0.2).apply { holdAfterWpMs = 0 }
        z.holdForWpChange(1_000)
        assertEquals(500.0, z.update(probe(5_000.0), 1_000), 0.0)
        // シーク・PAN から戻ったときは待たない
        val s = selector(0.2)
        s.holdForWpChange(1_000)
        s.decideNow()
        assertEquals(1_000.0, s.update(probe(5_000.0), 2_000), 0.0)
        // 時刻が戻ったら（巻き戻し）待機をやめる
        val b = selector(0.2)
        b.holdForWpChange(100_000)
        assertEquals(500.0, b.update(probe(5_000.0), 1_000), 0.0)
    }

    @Test
    fun zoomInDistanceRatioDecidesWhenToStartNarrowing() {
        // 1km の段（今の R1 は 500m）で次の WP に近づいていく（1 秒に 10m）。
        // 画面は ARC の前方のように「段の 2 倍」まで収まるものとする（500m の段に 1.25 倍で収まるのは 800m 以内）
        // 倍率 1.3（既定）: 650m 以内で狭め始め、5 秒続いたら狭める。倍率 1.0: 今の1つ目の円（500m）に入ってから
        fun wide(d: Double) = object : RangeProbe {
            override fun fits(rangeM: Double, spread: Double) = d * spread <= rangeM * 2
            override val distanceM = d
        }
        fun firstNarrowAt(ratio: Double): Long {
            val r = selector(1.0).apply { zoomInDistRatio = ratio }
            for (t in 0..100L) {
                val d = 1_000.0 - t * 10
                if (r.update(wide(d), t * 1_000) < 1_000.0) return t
            }
            return -1
        }
        // 1.3: 35 秒目に 650m 以内 → 40 秒目に 500m の段（WP は今の1つ目の円の外、600m）
        assertEquals(40L, firstNarrowAt(1.3))
        // 1.0: 50 秒目に 500m 以内 → 55 秒目
        assertEquals(55L, firstNarrowAt(1.0))
        // 3.0: 1500m 以内なので距離では止めない。1.25 倍の余裕で決まる（800m 以内 = 20 秒目 → 25 秒目）
        assertEquals(25L, firstNarrowAt(3.0))
    }

    @Test
    fun zoomInWaitIsCancelledWhenConditionBreaks() {
        val r = selector(1.0)
        r.update(probe(100.0), 0)
        r.update(probe(100.0), 4_000)
        // 一度でも条件が崩れたら（1段狭い段に余裕をもって収まらない）、待ちはやり直し
        r.update(probe(450.0), 4_500)
        assertEquals(1_000.0, r.update(probe(100.0), 5_000), 0.0)
        assertEquals(1_000.0, r.update(probe(100.0), 9_999), 0.0)
        assertEquals(500.0, r.update(probe(100.0), 10_000), 0.0)
    }

    @Test
    fun timeGoingBackwardsRestartsTheWait() {
        val r = selector(1.0)
        r.update(probe(100.0), 100_000)
        // リプレイの巻き戻しなどで時刻が戻っても、すぐには狭めない
        assertEquals(1_000.0, r.update(probe(100.0), 1_000), 0.0)
        assertEquals(500.0, r.update(probe(100.0), 6_000), 0.0)
    }

    @Test
    fun manualZoomTurnsAutoOff() {
        val r = selector(1.0)
        r.zoomOut()
        assertFalse(r.auto)
        // 手動では上限より広い段にもできる。AUTO OFF では判定しない
        assertEquals(2_000.0, r.rangeM, 0.0)
        assertEquals(2_000.0, r.update(probe(8_000.0), 0), 0.0)
        repeat(5) { r.zoomIn() }
        assertEquals(50.0, r.rangeM, 0.0)
        // 端の段より先へは行かない
        r.zoomIn()
        assertEquals(50.0, r.rangeM, 0.0)
        // AUTO を ON にすると、範囲の外なのですぐ下限へ
        r.setAuto(true)
        assertTrue(r.auto)
        assertEquals(100.0, r.update(probe(20.0), 0), 0.0)
    }

    @Test
    fun allStepsList() {
        assertEquals(listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0), RangeAuto.ALL_STEPS_KM)
    }
}
