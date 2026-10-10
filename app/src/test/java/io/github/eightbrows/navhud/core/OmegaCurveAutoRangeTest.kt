package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.model.Waypoint
import io.github.eightbrows.navhud.core.nav.NavEngine
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SourceKind
import io.github.eightbrows.navhud.core.nav.TravelMode
import io.github.eightbrows.navhud.core.nav.editReach
import io.github.eightbrows.navhud.core.nav.selectTravelMode
import io.github.eightbrows.navhud.core.view.HudInsets
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudViewport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.time.ZoneId
import java.util.Locale

/**
 * Ω 形のカーブ（半径 50m ほど、カーブの中心に WP）での AUTO 縮尺（§6.1）。
 * 走行の記録は build/auto-range/omega_*.csv に書き出す（レポート用）。
 */
class OmegaCurveAutoRangeTest {

    private val jst = ZoneId.of("Asia/Tokyo")

    /** エミュレータで測った画面（幅 423dp、LIVE） */
    private val emulator = HudViewport(MeasuredScreen.RECT, HudMetrics().scaled(MeasuredScreen.DENSITY), MeasuredScreen.LIVE)

    /** 幅 360dp の端末（密度 3、1080 px 幅）。重ねた表示の dp はエミュレータと同じ（上 124dp、右 60dp、下 120dp、操作列は回避枠の縦中央 180dp） */
    private val phone360 = HudViewport(
        HudRect(0f, 0f, 1080f, 2187f),
        HudMetrics().scaled(3f),
        HudInsets(top = 372f, right = 180f, bottom = 360f, rightSpan = 829.5f..1369.5f),
    )

    /** 1 秒ごとの記録 */
    data class Row(
        val sec: Int,
        val rangeM: Double,
        val nextIndex: Int?,
        val distM: Double?,
        /** 次の WP の、進行方向から見た角度 [°]（右が +） */
        val relDeg: Double?,
        /** 今の段・1つ広域側の段に次の WP が収まるか（規則 4 の判定そのもの） */
        val fitsNow: Boolean?,
        val fitsWider: Boolean?,
    )

    private fun run(road: TestRoad, wps: List<Waypoint>, vp: HudViewport, settings: NavSettings = TestSettings.BEFORE_D03): List<Row> {
        val e = NavEngine(settings, jst, SourceKind.LIVE)
        e.setWaypoints(wps)
        e.setViewport(vp)
        val steps = settings.rangeStepsKm.map { it * 1000 }
        return road.fixes().mapIndexed { i, f ->
            // 判定の前の段で、収まるかを見ておく
            val before = e.state.rangeM
            val st = e.onFix(f, f.timeMs)
            val next = st.nextWpIndex
            val wp = next?.let { wps[it] }
            val target = wp?.let { Geo.toEN(f.lat, f.lon, it.lat, it.lon) }
            val heading = f.bearingDeg!!.toDouble()
            val wider = steps.firstOrNull { it > before + 1e-6 }
            Row(
                sec = i, rangeM = st.rangeM, nextIndex = next, distM = st.nextWpDistanceM,
                relDeg = wp?.let { Geo.angleDiff(heading, Geo.bearingDeg(f.lat, f.lon, it.lat, it.lon)) },
                fitsNow = target?.let { vp.fits(before, it, heading, settings) },
                fitsWider = if (target != null && wider != null) vp.fits(wider, target, heading, settings) else null,
            )
        }
    }

    private fun write(name: String, rows: List<Row>) {
        val dir = File("build/auto-range").apply { mkdirs() }
        File(dir, "$name.csv").writeText(
            "sec,r1_m,next_wp,dist_m,rel_deg,fits_now,fits_wider\n" + rows.joinToString("\n") {
                "${it.sec},${(it.rangeM / 2).toInt()},${it.nextIndex ?: ""},${it.distM?.let { d -> "%.0f".format(Locale.US, d) } ?: ""}," +
                    "${it.relDeg?.let { d -> "%.0f".format(Locale.US, d) } ?: ""},${it.fitsNow ?: ""},${it.fitsWider ?: ""}"
            } + "\n",
        )
    }

    /** 最初の WP が次の WP の間に、縮尺を広域にした記録（前の行・後の行） */
    private fun widenedBeforeReach(rows: List<Row>): List<Pair<Row, Row>> =
        rows.zipWithNext().filter { (a, b) -> a.nextIndex == 0 && b.nextIndex == 0 && b.rangeM > a.rangeM }

    private fun describe(name: String, rows: List<Row>) {
        println("== $name")
        var last = -1.0
        for (r in rows) {
            if (r.rangeM != last) {
                println("  t=${r.sec}s R1=${(r.rangeM / 2).toInt()}m next=${r.nextIndex} dist=${r.distM?.toInt()} rel=${r.relDeg?.toInt()} fitsNow=${r.fitsNow} fitsWider=${r.fitsWider}")
            }
            last = r.rangeM
        }
        println("  widened before reach: " + widenedBeforeReach(rows).map { (a, b) -> "t=${b.sec}s ${(a.rangeM / 2).toInt()}→${(b.rangeM / 2).toInt()} dist=${b.distM?.toInt()} rel=${b.relDeg?.toInt()}" })
    }

    /** Ω 形の場面: エミュレータの画面（幅 423dp）では半径 60m、幅 360dp の端末では半径 50m で、直す前は到達前へ広域にしていた */
    private val cases = listOf(
        Triple("omega_r50_emulator", { OmegaCurve() }, emulator),
        Triple("omega_r60_emulator", { OmegaCurve(radiusM = 60.0) }, emulator),
        Triple("omega_r50_phone360", { OmegaCurve() }, phone360),
        Triple("omega_r50_t100_phone360", { OmegaCurve(turnDeg = 100.0) }, phone360),
    )

    @Test
    fun rangeIsKeptThroughTheCurveUntilTheWaypointIsReached() {
        for ((name, make, vp) in cases) {
            val curve = make()
            val wps = listOf(curve.waypoint(), curve.waypointAhead("先", 1_500.0))
            val rows = run(curve.road, wps, vp)
            write(name, rows)
            describe(name, rows)
            // 近づくにつれて R1 250m → 100m → 50m と詳細にする
            val toFirst = rows.filter { it.nextIndex == 0 }
            assertEquals(name, listOf(1_000.0, 500.0, 200.0, 100.0), toFirst.map { it.rangeM }.distinct())
            // カーブの中心の WP に到達するまで、一度も広域にしない（直す前は、WP が正面から外れた所で 50m → 100m へ広域にした）
            assertTrue("$name ${widenedBeforeReach(rows)}", widenedBeforeReach(rows).isEmpty())
            // 到達したあと、次の WP（遠い）に向けては今まで通り広域にする（広域の限度の 1km の段 = R1 500m まで）
            val toSecond = rows.filter { it.nextIndex == 1 }
            assertTrue(name, toSecond.isNotEmpty())
            assertEquals(name, 1_000.0, toSecond.last().rangeM, 0.0)
            assertEquals(name, listOf(100.0, 200.0, 500.0, 1_000.0), toSecond.map { it.rangeM }.distinct())
        }
    }

    @Test
    fun theWaypointLeavesTheFrameInTheCurveButTheRangeStays() {
        // 原因の確認（規則 4）: カーブの途中で、WP は R1 50m の段の枠に収まらなくなる（1つ広域側の段なら収まる）。
        // 直す前はこの瞬間へ広域にしていた。今は縮尺をそのままにする（画面外の次の WP の文字で示す）
        for ((name, make, vp) in cases.filter { it.first != "omega_r50_emulator" }) {
            val curve = make()
            val rows = run(curve.road, listOf(curve.waypoint(), curve.waypointAhead("先", 1_500.0)), vp)
            val outOfFrame = rows.filter { it.nextIndex == 0 && it.fitsNow == false && it.fitsWider == true }
            assertTrue(name, outOfFrame.isNotEmpty())
            // そのとき WP は右前（正面から 60° 以上）で、まだ到達前。段は R1 50m のまま
            assertTrue("$name $outOfFrame", outOfFrame.all { it.relDeg!! > 55.0 && it.rangeM == 100.0 })
        }
    }

    @Test
    fun leavingTheRouteLetsTheRangeWidenAgain() {
        // 逃げ道: WP に近づいて R1 50m まで詳細にしたあと、WP に行かずに左へ曲がって離れていく（到達の判定は切っておく）。
        // WP から離れたら、今まで通り1段ずつ広域にする: R1 100m へは 162.5m、250m へは 406m、500m へは 812m より遠くなってから
        // WP は道の右 30m。その 40m 手前（WP まで 50m。到着半径 30m の外）で左へ曲がる
        val road = TestRoad().straight(600.0)
        val wp = TestRoad.waypoint("寄らない", road.offset(rightM = 30.0, aheadM = 150.0))
        road.straight(110.0).left(20.0, 90.0).straight(1_500.0)
        val noReach = TestSettings.BEFORE_D03.selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(sidePass = false, passDetection = false) }
        val rows = run(road, listOf(wp), emulator, noReach)
        write("omega_leave_route", rows)
        describe("omega_leave_route", rows)
        assertTrue(rows.all { it.nextIndex == 0 })
        // 近づく間に R1 50m まで詳細にする
        val narrowest = rows.indexOfFirst { it.rangeM == 100.0 }
        assertTrue(narrowest >= 0)
        val after = rows.drop(narrowest)
        // 離れていく間、広域にするのは決まった距離を超えてから
        for ((a, b) in after.zipWithNext()) {
            if (b.rangeM > a.rangeM) {
                val limit = when (b.rangeM) {
                    200.0 -> 162.5
                    500.0 -> 406.25
                    else -> 812.5
                }
                assertTrue("t=${b.sec}s ${a.rangeM}→${b.rangeM} dist=${b.distM}", b.distM!! > limit)
            }
        }
        // 枠の外に出ても、162.5m までは R1 50m のまま
        assertTrue(after.any { it.fitsNow == false && it.distM!! < 162.5 && it.rangeM == 100.0 })
        // 最後は広域の限度（R1 500m）まで広域になる（ずっと詳細のままにならない）
        assertEquals(1_000.0, rows.last().rangeM, 0.0)
    }
}
