package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.io.BaseMap
import io.github.eightbrows.navhud.core.io.BaseMapFile
import io.github.eightbrows.navhud.core.io.MapLayer
import io.github.eightbrows.navhud.core.model.Fix
import io.github.eightbrows.navhud.core.model.HeadingSrc
import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.Heading
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.view.BaseMapBuilder
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.util.Locale
import kotlin.math.hypot

/**
 * 背景地図（§8）をサンプルの記録（track.csv）の場所で確かめる（レポート用）。結果は build/basemap/ に書き出す。
 * - 1 回の計算（画面に入る線・点を選び、画面の位置に直し、点を減らす）にかかる時間と、線・点の数:
 *   R1 100 km・25 km・2.5 km・250 m で、海岸線と県境だけ（初期値）と、全部のレイヤ
 * - 軌跡（実際に走った道）と海岸線・県境の近さ
 * track.csv は git 管理外なので、ファイルがなければスキップする。
 */
class BaseMapSampleTest {

    private val map: BaseMap = BaseMapFile.decode(File("src/main/assets/basemap.bin").readBytes()).also { it.preload(MapLayer.entries.toSet()) }
    private val metrics = HudMetrics().scaled(MeasuredScreen.DENSITY)

    private fun state(fix: Fix, rangeM: Double, mode: DisplayMode, heading: Float?) = NavState(
        nowMs = fix.timeMs,
        fix = fix,
        heading = Heading(heading, if (heading == null) HeadingSrc.NONE else HeadingSrc.GPS),
        settings = NavSettings(displayMode = mode),
        rangeM = rangeM,
    )

    @Test
    fun timeToBuildAtEachRangeForDefaultAndAllLayers() {
        val fixes = SampleTrack.fixes()
        // サンプルの記録の 10 か所（等間隔）で
        val at = (0 until 10).map { fixes[it * (fixes.size - 1) / 9] }
        val out = StringBuilder("layers,r1,mode,lines,points,marks,mean_ms,max_ms\n")
        val sets = listOf("default" to NavSettings.DEFAULT_MAP_ITEMS, "all" to MapItem.entries.associateWith { MapRange.ALL })
        // 表示の縮尺（R1）は rangeM の半分
        for ((name, layers) in sets) for ((label, r1) in listOf("100km" to 100_000.0, "25km" to 25_000.0, "2.5km" to 2_500.0, "250m" to 250.0)) {
            for (mode in listOf(DisplayMode.ARC, DisplayMode.NORTH_UP)) {
                var lines = 0
                var points = 0
                var marks = 0
                val times = mutableListOf<Double>()
                repeat(3) { round ->
                    for (f in at) {
                        val s = state(f, r1 * 2, mode, f.bearingDeg ?: 0f)
                        val avoid = HudSceneBuilder.avoidFrame(MeasuredScreen.RECT, MeasuredScreen.REPLAY)
                        val proj = HudSceneBuilder.projection(s.settings, MeasuredScreen.RECT, avoid, r1 * 2, s.heading.deg?.toDouble(), metrics)
                        val t0 = System.nanoTime()
                        val scene = BaseMapBuilder.build(map, layers, f.lat, f.lon, proj, MeasuredScreen.RECT, r1)
                        val ms = (System.nanoTime() - t0) / 1e6
                        if (round > 0) {   // 1 回目は JVM の準備の分が入るので数えない
                            times += ms
                            lines = maxOf(lines, scene.lines.size)
                            points = maxOf(points, scene.lines.sumOf { it.points.size })
                            marks = maxOf(marks, scene.marks.size)
                        }
                    }
                }
                out.append(String.format(Locale.US, "%s,%s,%s,%d,%d,%d,%.3f,%.3f\n", name, label, mode, lines, points, marks, times.average(), times.max()))
            }
        }
        val dir = File("build/basemap").apply { mkdirs() }
        File(dir, "timing.csv").writeText(out.toString())
        assertTrue(out.lines().size > 8)
    }

    /** 軌跡のうち、その種類の線にいちばん近い点と、その距離 [m]（線分までの距離。基準の地点からの東・北 [m] で測る） */
    private fun nearest(fixes: List<Fix>, layer: MapLayer): Pair<Double, Fix?> {
        val lines = map.lines.filter { it.layer == layer }
        var best = Double.MAX_VALUE
        var bestFix: Fix? = null
        for (f in fixes) {
            for (l in lines) {
                if (l.maxLat < f.lat - 0.2 || l.minLat > f.lat + 0.2 || l.maxLon < f.lon - 0.25 || l.minLon > f.lon + 0.25) continue
                for (i in 0 until l.size - 1) {
                    val a = Geo.toEN(f.lat, f.lon, l.lat(i), l.lon(i))
                    val b = Geo.toEN(f.lat, f.lon, l.lat(i + 1), l.lon(i + 1))
                    val dx = b.e - a.e
                    val dy = b.n - a.n
                    val len2 = dx * dx + dy * dy
                    val t = if (len2 == 0.0) 0.0 else (-(a.e * dx + a.n * dy) / len2).coerceIn(0.0, 1.0)
                    val d = hypot(a.e + t * dx, a.n + t * dy)
                    if (d < best) { best = d; bestFix = f }
                }
            }
        }
        return best to bestFix
    }

    @Test
    fun distanceFromTrackToCoastlineAndPrefectureBorder() {
        val fixes = SampleTrack.fixes()
        val out = StringBuilder()
        for (layer in listOf(MapLayer.COASTLINE, MapLayer.PREF_BOUNDARY)) {
            val (d, f) = nearest(fixes, layer)
            out.append(String.format(Locale.US, "%s nearest_m=%.0f lat=%.5f lon=%.5f time_ms=%d\n", layer, d, f?.lat, f?.lon, f?.timeMs))
        }
        // 海岸線の点の間隔（線分の長さ）[m]: 1/100万の地図なので、詳細ではまっすぐな線分のつながりに見える
        val seg = map.lines.filter { it.layer == MapLayer.COASTLINE }.flatMap { l ->
            (0 until l.size - 1).map { Geo.distanceM(l.lat(it), l.lon(it), l.lat(it + 1), l.lon(it + 1)) }
        }.sorted()
        out.append(String.format(Locale.US, "coast_segment_m median=%.0f p90=%.0f max=%.0f count=%d\n", seg[seg.size / 2], seg[seg.size * 9 / 10], seg.last(), seg.size))
        val dir = File("build/basemap").apply { mkdirs() }
        File(dir, "track_distance.txt").writeText(out.toString())
        assertTrue(out.isNotEmpty())
    }
}
