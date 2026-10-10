package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * AUTO 縮尺の規則（§6.1）。画面の代わりに、距離で答える RangeProbe を使う:
 * 次の WP が distM 先にあり、段 r で「distM × spread ≤ r」なら収まる。sepMaxM 以下の段でだけ WP を区別できる。
 * 段は 50m〜5km、AUTO の詳細の限度・広域の限度は D02 までの既定（100m〜1km の段 = R1 50m〜500m）。
 */
class RangeSelectorTest {

    private val stepsKm = listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0)
    private val stepsM = listOf(500.0, 1_000.0, 2_000.0, 5_000.0, 10_000.0)

    private fun probe(distM: Double, sepMaxM: Double = Double.MAX_VALUE) = object : RangeProbe {
        override fun fits(rangeM: Double, spread: Double) = distM * spread <= rangeM
        override fun separated(rangeM: Double) = rangeM <= sepMaxM
        override val distanceM = distM
    }

    // AUTO の広域の限度は 1km の段（R1 500m）で確かめる（初期値は D03 から 500m の段）
    private fun selector(initialKm: Double) = RangeSelector(stepsKm, initialKm, auto = true, autoMaxKm = 1.0)

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
        assertEquals(1_000.0, RangeSelector(listOf(0.5, 1.0, 2.0), 1.0, auto = true, autoMaxKm = 1.0).rangeM, 0.0)
        // 有効リストにない起動時の縮尺は近い段へ
        assertEquals(2_000.0, RangeSelector(listOf(0.5, 2.0, 5.0), 1.5, auto = true, autoMaxKm = 1.0).rangeM, 0.0)
    }

    @Test
    fun limitsAreSnappedToTheUsedSteps() {
        // D02 までの既定: 詳細の限度 100m の段（R1 50m）、広域の限度 1km の段（R1 500m）
        assertEquals(0.1 to 1.0, RangeAuto.limitsKm(NavSettings().rangeStepsKm, 0.1, 1.0))
        assertEquals(listOf(100.0, 200.0, 500.0, 1_000.0), selector(1.0).autoSteps)
        // 使う段にない詳細の限度・広域の限度は、詳細の限度は上の段へ、広域の限度は下の段へ寄せる
        assertEquals(0.2 to 0.5, RangeAuto.limitsKm(listOf(0.2, 0.5, 2.0), 0.1, 1.0))
        // 寄せる段がなければ使う段の端
        assertEquals(2.0 to 2.0, RangeAuto.limitsKm(listOf(2.0, 5.0), 0.1, 1.0))
        // 詳細の限度が広域の限度より広域なら、広域の限度にそろえる
        assertEquals(1.0 to 1.0, RangeAuto.limitsKm(stepsKm, 5.0, 1.0))
    }

    @Test
    fun stopsAtTheUpperLimit() {
        // 次の WP が 50km 先: 広域の限度（1km の段）で止まり、それ以上は広域にしない（画面の端の矢印で示す）
        val r = selector(0.1)
        assertEquals(200.0, r.update(probe(50_000.0), 0), 0.0)
        assertEquals(500.0, r.update(probe(50_000.0), 1_000), 0.0)
        assertEquals(1_000.0, r.update(probe(50_000.0), 2_000), 0.0)
        assertEquals(1_000.0, r.update(probe(50_000.0), 3_000), 0.0)
        assertEquals(1_000.0, r.update(probe(50_000.0), 60_000), 0.0)
    }

    @Test
    fun widensImmediatelyOneStepAtATime() {
        // 800m 先: 200m の段から、1回に1段ずつすぐ広域にする（200 → 500 → 1000）
        val r = selector(0.2)
        assertEquals(500.0, r.update(probe(800.0), 0), 0.0)
        assertEquals(1_000.0, r.update(probe(800.0), 0), 0.0)
        assertEquals(1_000.0, r.update(probe(800.0), 0), 0.0)
    }

    @Test
    fun narrowsOneStepAfterTheWait() {
        // 100m 先: 1km の段から、1つ詳細側の段（500m）で 1.25 倍遠く（125m）でも収まる状態が 5 秒続いたら1段詳細にする
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
        // 1km の段で 450m 先: 500m の段に 1.25 倍（562.5m）は収まらないので詳細にしない
        val r = selector(1.0)
        for (t in 0..30L) assertEquals(1_000.0, r.update(probe(450.0), t * 1_000), 0.0)
        // 390m（× 1.25 = 487.5m）なら詳細にする
        r.update(probe(390.0), 40_000)
        assertEquals(500.0, r.update(probe(390.0), 45_000), 0.0)
    }

    @Test
    fun neverNarrowerThanTheLowerLimit() {
        // 10m 先でも、詳細の限度（100m の段）より詳細にしない（50m の段は使う段にあっても使わない）
        val r = selector(0.2)
        r.update(probe(10.0), 0)
        assertEquals(100.0, r.update(probe(10.0), 5_000), 0.0)
        r.update(probe(10.0), 6_000)
        assertEquals(100.0, r.update(probe(10.0), 60_000), 0.0)
    }

    @Test
    fun narrowsImmediatelyWhenWaypointsAreTooClose() {
        // 次の WP（150m 先）は 1km の段に収まるが、隣り合う目標を区別できるのは 200m 以下の段だけ: 待たずに1段ずつ詳細にする
        val r = selector(1.0)
        assertEquals(500.0, r.update(probe(150.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(200.0, r.update(probe(150.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(200.0, r.update(probe(150.0, sepMaxM = 200.0), 1_000), 0.0)
        // どの段でも区別できなければ、詳細の限度で止まる（50m 先なので詳細の限度の 100m の段にも収まる）
        val s = selector(1.0)
        repeat(5) { s.update(probe(50.0, sepMaxM = 0.0), 0) }
        assertEquals(100.0, s.rangeM, 0.0)
    }

    @Test
    fun closeWaypointsDoNotHideTheNextOne() {
        // 次の WP は 300m 先、区別できるのは 200m 以下の段だけ。200m の段では次の WP が収まらないので、
        // 1km → 500m の段までで止める（近すぎても、次の WP が見えなくなるほどは詳細にしない）
        val r = selector(1.0)
        assertEquals(500.0, r.update(probe(300.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(500.0, r.update(probe(300.0, sepMaxM = 200.0), 1_000), 0.0)
        assertEquals(500.0, r.update(probe(300.0, sepMaxM = 200.0), 60_000), 0.0)
        // 200m の段で次の WP が収まらなければ、近すぎても 500m の段へ広域にする（詳細にすると見えなくなるので）
        val s = selector(0.2)
        assertEquals(500.0, s.update(probe(300.0, sepMaxM = 200.0), 0), 0.0)
        assertEquals(500.0, s.update(probe(300.0, sepMaxM = 200.0), 60_000), 0.0)
    }

    @Test
    fun noNextWaypointGoesToTheMiddleStep() {
        // 詳細の限度〜広域の限度は 100m / 200m / 500m / 1km の4段。中央の2つのうち広域の方（500m の段、R1 250m）へすぐ
        assertEquals(500.0, selector(5.0).update(null, 0), 0.0)
        assertEquals(500.0, selector(0.1).update(null, 0), 0.0)
        // 3段（広域の限度 500m の段）なら真ん中の 200m の段
        val three = RangeSelector(stepsKm, 1.0, auto = true, autoMinKm = 0.1, autoMaxKm = 0.5)
        assertEquals(200.0, three.update(null, 0), 0.0)
    }

    @Test
    fun outsideTheLimitsGoesToTheEndAtOnce() {
        // 5km の段で AUTO: 1段ずつではなく、すぐ広域の限度（1km の段）へ
        assertEquals(1_000.0, selector(5.0).update(probe(100.0), 0), 0.0)
        // 50m の段なら、すぐ詳細の限度（100m の段）へ
        assertEquals(100.0, selector(0.05).update(probe(10.0), 0), 0.0)
        // 設定で広域の限度を変えたときも
        val r = selector(1.0)
        r.setAutoLimits(0.1, 0.2)
        assertEquals(200.0, r.update(probe(100.0), 0), 0.0)
    }

    @Test
    fun decideNowGoesToTheTargetAtOnce() {
        // PAN から戻ったとき・シークのあと: 1段ずつではなく、収まるいちばん詳細な段へすぐ（区別できなければさらに詳細にする）
        val r = selector(0.1)
        r.decideNow()
        assertEquals(1_000.0, r.update(probe(800.0), 0), 0.0)
        r.decideNow()
        assertEquals(200.0, r.update(probe(150.0, sepMaxM = 200.0), 0), 0.0)
        // 詳細にすると次の WP（800m）が収まらないなら、区別できなくても収まる段のまま
        r.decideNow()
        assertEquals(1_000.0, r.update(probe(800.0, sepMaxM = 200.0), 0), 0.0)
    }

    @Test
    fun holdsAfterTheNextWaypointChanges() {
        // 次の WP が変わってから 10 秒（既定）は、広域にする・詳細にする・範囲の外・次の WP なし、どれも動かさない
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
        // 倍率 1.3（既定）: 650m 以内で詳細へ切り替え始め、5 秒続いたら詳細にする。倍率 1.0: 今の1つ目の円（500m）に入ってから
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
        // 一度でも条件が崩れたら（1つ詳細側の段に余裕をもって収まらない）、待ちはやり直し
        r.update(probe(450.0), 4_500)
        assertEquals(1_000.0, r.update(probe(100.0), 5_000), 0.0)
        assertEquals(1_000.0, r.update(probe(100.0), 9_999), 0.0)
        assertEquals(500.0, r.update(probe(100.0), 10_000), 0.0)
    }

    @Test
    fun timeGoingBackwardsRestartsTheWait() {
        val r = selector(1.0)
        r.update(probe(100.0), 100_000)
        // リプレイの巻き戻しなどで時刻が戻っても、すぐには詳細にしない
        assertEquals(1_000.0, r.update(probe(100.0), 1_000), 0.0)
        assertEquals(500.0, r.update(probe(100.0), 6_000), 0.0)
    }

    @Test
    fun manualZoomTurnsAutoOff() {
        val r = selector(1.0)
        r.zoomOut()
        assertFalse(r.auto)
        // 手動では広域の限度より広域の段にもできる。AUTO OFF では判定しない
        assertEquals(2_000.0, r.rangeM, 0.0)
        assertEquals(2_000.0, r.update(probe(8_000.0), 0), 0.0)
        repeat(5) { r.zoomIn() }
        assertEquals(50.0, r.rangeM, 0.0)
        // 端の段より先へは行かない
        r.zoomIn()
        assertEquals(50.0, r.rangeM, 0.0)
        // AUTO を ON にすると、範囲の外なのですぐ詳細の限度へ
        r.setAuto(true)
        assertTrue(r.auto)
        assertEquals(100.0, r.update(probe(20.0), 0), 0.0)
    }

    /** 次の WP（key）が distM 先にあり、段 r で「needM × spread ≤ r」なら枠に収まる（横に外れた WP は needM が大きい）。 */
    private fun keyed(distM: Double, needM: Double, key: Any? = "A") = object : RangeProbe {
        override fun fits(rangeM: Double, spread: Double) = needM * spread <= rangeM
        override val distanceM = distM
        override val targetKey = key
    }

    @Test
    fun afterNarrowingItDoesNotWidenUntilTheWaypointIsReached() {
        // 200m の段（R1 100m）で、80m 先の WP が1つ詳細側の段に余裕をもって収まる → 5 秒で 100m の段（R1 50m）まで詳細にする
        val r = selector(0.2)
        assertEquals(200.0, r.update(keyed(80.0, 80.0), 0), 0.0)
        assertEquals(100.0, r.update(keyed(80.0, 80.0), 5_000), 0.0)
        // カーブで WP が正面から外れ、100m の段の枠に収まらなくなった（200m の段なら収まる）: 前はすぐ広域にした。
        // 一度詳細にした WP なので、到達するまで広域にしない（62m・140m・162m）
        assertEquals(100.0, r.update(keyed(62.0, 150.0), 6_000), 0.0)
        assertEquals(100.0, r.update(keyed(140.0, 150.0), 7_000), 0.0)
        assertEquals(100.0, r.update(keyed(162.0, 150.0), 8_000), 0.0)
        // 逃げ道: WP から離れた（1つ広域側の段 200m で詳細へ切り替える距離 1.3 × 100m の 1.25 倍 = 162.5m より遠い）なら広域にする
        assertEquals(200.0, r.update(keyed(163.0, 150.0), 9_000), 0.0)
        // その先も同じ決まり: 500m の段へは 1.3 × 250m × 1.25 = 406.25m より遠いとき、1km の段へは 812.5m より遠いとき
        assertEquals(200.0, r.update(keyed(300.0, 900.0), 10_000), 0.0)
        assertEquals(200.0, r.update(keyed(406.0, 900.0), 11_000), 0.0)
        assertEquals(500.0, r.update(keyed(407.0, 900.0), 12_000), 0.0)
        assertEquals(500.0, r.update(keyed(812.0, 900.0), 13_000), 0.0)
        assertEquals(1_000.0, r.update(keyed(813.0, 900.0), 14_000), 0.0)
    }

    @Test
    fun wideningIsOnlyHeldForTheWaypointItNarrowedFor() {
        fun narrowedTo100(): RangeSelector = selector(0.2).also {
            it.update(keyed(80.0, 80.0), 0)
            assertEquals(100.0, it.update(keyed(80.0, 80.0), 5_000), 0.0)
        }
        // 次の WP が変わった（到達した）: 新しい WP が枠に収まらなければ、今まで通りすぐ広域にする（近くても）
        assertEquals(200.0, narrowedTo100().update(keyed(62.0, 150.0, key = "B"), 6_000), 0.0)
        // 同じ WP でも、枠に収まっている間は何もしない
        assertEquals(100.0, narrowedTo100().update(keyed(62.0, 60.0), 6_000), 0.0)
        // AUTO を入れ直したら、詳細にした記録は消える（今まで通り広域にする）
        narrowedTo100().let {
            it.setAuto(false)
            it.setAuto(true)
            assertEquals(200.0, it.update(keyed(62.0, 150.0), 6_000), 0.0)
        }
        // PAN から戻った・シークのあと（decideNow）: 目標の段にすぐして、記録も消える
        narrowedTo100().let {
            it.decideNow()
            assertEquals(200.0, it.update(keyed(62.0, 150.0), 6_000), 0.0)
            assertEquals(500.0, it.update(keyed(62.0, 400.0), 7_000), 0.0)
        }
        // WP を見分けられない問い（キーなし）は、今まで通り広域にする
        val plain = selector(0.2)
        plain.update(keyed(80.0, 80.0, key = null), 0)
        assertEquals(100.0, plain.update(keyed(80.0, 80.0, key = null), 5_000), 0.0)
        assertEquals(200.0, plain.update(keyed(62.0, 150.0, key = null), 6_000), 0.0)
        // AUTO が詳細にしていない WP（はじめから 100m の段）も、今まで通り広域にする
        assertEquals(200.0, selector(0.1).update(keyed(62.0, 150.0), 0), 0.0)
    }

    @Test
    fun allStepsList() {
        assertEquals(listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0), RangeAuto.ALL_STEPS_KM)
    }
}
