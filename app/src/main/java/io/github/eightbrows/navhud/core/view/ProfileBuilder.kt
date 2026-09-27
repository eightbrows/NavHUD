package io.github.eightbrows.navhud.core.view

import io.github.eightbrows.navhud.core.geo.Geo
import io.github.eightbrows.navhud.core.nav.NavState
import kotlin.math.max
import kotlin.math.roundToInt

/** 標高プロファイルの点（現在地、または標高が分かる WP）。 */
data class ProfilePoint(val at: P, val ink: Ink, val name: String? = null)

/** 標高プロファイル（§6.6）の描くもの。座標は計算済み。 */
data class ProfileScene(
    val rect: HudRect,
    val segments: List<Segment>,
    val points: List<ProfilePoint>,
    /** 縦軸の目盛りの文字（上端・下端の標高） */
    val labels: List<Label>,
)

/** 標高プロファイルの寸法 [px]。 */
data class ProfileMetrics(
    /** 左の余白（縦軸の数字の分） */
    val left: Float = 40f,
    val right: Float = 14f,
    val top: Float = 10f,
    val bottom: Float = 8f,
) {
    fun scaled(k: Float) = ProfileMetrics(left * k, right * k, top * k, bottom * k)
}

/**
 * 標高プロファイル（§6.6）: 横軸 = 現在地からの累積水平距離、縦軸 = 標高。
 * 現在地 → 次の目標 → その先の目標…（目標 = 有効かつ未到達、HUD に描く WP の数だけ）を直線で結ぶ（地形なし）。
 * - 現在地の標高は ALT。NO FIX 中は最後の値をグレーで。
 * - 標高のない WP は点を描かず、前後の標高が分かる点の間を破線でつなぐ（片側しかなければ線を描かない）。
 * - 縦軸は自動スケール（最小の幅 50 m）。次の WP はマゼンタ。
 */
object ProfileBuilder {

    /** 縦軸の最小の幅 [m] */
    const val MIN_SPAN_M = 50.0

    /** 横軸・縦軸の計算に使う点（x = 累積距離 [m]、ele = 標高 [m] / 不明なら null）。 */
    internal data class Node(val x: Double, val ele: Double?, val name: String?, val isCurrent: Boolean, val isNext: Boolean)

    fun build(state: NavState, rect: HudRect, m: ProfileMetrics = ProfileMetrics(), showNames: Boolean = false): ProfileScene {
        val empty = ProfileScene(rect, emptyList(), emptyList(), emptyList())
        val nodes = nodes(state)
        if (nodes.size < 2) return empty
        val known = nodes.filter { it.ele != null }
        if (known.isEmpty()) return empty

        // 縦軸: 分かっている標高の範囲。幅が 50 m 未満なら中心から 50 m に広げる
        val lo0 = known.minOf { it.ele!! }
        val hi0 = known.maxOf { it.ele!! }
        val span = max(hi0 - lo0, MIN_SPAN_M)
        val mid = (lo0 + hi0) / 2
        val lo = mid - span / 2
        val hi = mid + span / 2
        val plot = HudRect(rect.left + m.left, rect.top + m.top, rect.right - m.right, rect.bottom - m.bottom)
        val totalX = nodes.last().x.takeIf { it > 0 } ?: 1.0
        fun at(n: Node) = P(
            (plot.left + n.x / totalX * plot.width).toFloat(),
            (plot.bottom - (n.ele!! - lo) / (hi - lo) * plot.height).toFloat(),
        )

        val stale = state.noFix
        fun inkOf(n: Node) = when {
            n.isCurrent -> if (stale) Ink.STALE else Ink.OWNSHIP
            n.isNext -> Ink.ACTIVE
            else -> Ink.WP
        }

        // 線: 標高が分かる点どうし。間に標高のない WP があれば破線。次の WP へ向かう線はマゼンタ（NO FIX 中の現在地からはグレー）
        val segments = mutableListOf<Segment>()
        var prev: Int? = null
        for ((j, n) in nodes.withIndex()) {
            if (n.ele == null) continue
            val i = prev
            if (i != null) {
                val a = nodes[i]
                val towardNext = nodes.subList(i + 1, j + 1).any { it.isNext }
                val ink = when {
                    towardNext && a.isCurrent && stale -> Ink.STALE
                    towardNext -> Ink.ACTIVE
                    else -> Ink.WP
                }
                segments += Segment(at(a), at(n), ink, dashed = j > i + 1)
            }
            prev = j
        }

        val points = known.map { ProfilePoint(at(it), inkOf(it), if (showNames && !it.isCurrent) it.name else null) }
        val labels = listOf(
            Label(hi.roundToInt().toString(), P(rect.left + m.left / 2, plot.top), Ink.SCALE_DIM, small = true),
            Label(lo.roundToInt().toString(), P(rect.left + m.left / 2, plot.bottom), Ink.SCALE_DIM, small = true),
        )
        return ProfileScene(rect, segments, points, labels)
    }

    /** 現在地と、次の WP から先の目標（HUD に描く WP の数だけ）。Fix か次の WP がなければ空。 */
    internal fun nodes(state: NavState): List<Node> {
        val fix = state.fix ?: return emptyList()
        val next = state.nextWpIndex ?: return emptyList()
        val count = state.settings.hudWpCount.coerceAtLeast(1)
        val targets = (next until state.waypoints.size)
            .filter { state.waypoints[it].enabled && !state.waypoints[it].reached }
            .take(count)
        val nodes = mutableListOf(Node(0.0, state.altM, null, isCurrent = true, isNext = false))
        var x = 0.0
        var lat = fix.lat
        var lon = fix.lon
        for (i in targets) {
            val wp = state.waypoints[i]
            x += Geo.distanceM(lat, lon, wp.lat, wp.lon)
            lat = wp.lat
            lon = wp.lon
            nodes += Node(x, wp.eleM, wp.name, isCurrent = false, isNext = i == next)
        }
        return nodes
    }
}
