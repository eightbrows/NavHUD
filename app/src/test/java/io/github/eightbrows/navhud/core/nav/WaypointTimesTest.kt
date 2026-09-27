package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.Waypoint
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test
import java.time.LocalTime

class WaypointTimesTest {

    private fun t(s: String) = LocalTime.parse(s)

    private fun wp(name: String, target: String?, deadline: String? = null, enabled: Boolean = true, reached: Boolean = false) =
        Waypoint(name, 34.0, 135.0, targetTime = target?.let(::t), deadlineTime = deadline?.let(::t), enabled = enabled, reached = reached)

    private fun targets(wps: List<Waypoint>) = wps.map { it.targetTime?.toString() }
    private fun deadlines(wps: List<Waypoint>) = wps.map { it.deadlineTime?.toString() }

    // 往路: 峠 8:00 → 展望台 8:30（締切 8:45）→ 道の駅 9:10（締切 9:20）→ 終点 11:00
    private val outbound = listOf(
        wp("峠", "08:00"),
        wp("展望台", "08:30", "08:45"),
        wp("道の駅", "09:10", "09:20"),
        wp("終点", "11:00"),
    )

    @Test
    fun shiftOutbound() {
        // 先頭を 8:15 に → 全部 15 分ずれる。締切は目標との差（15 分・10 分）を保つ
        val r = WaypointTimes.adjust(outbound, 0, t("08:15"))
        assertEquals(listOf("08:15", "08:45", "09:25", "11:15"), targets(r))
        assertEquals(listOf(null, "09:00", "09:35", null), deadlines(r))
        // 途中の WP を基準にしても、前後に同じ差で広がる
        val mid = WaypointTimes.adjust(outbound, 2, t("10:00"))
        assertEquals(listOf("08:50", "09:20", "10:00", "11:50"), targets(mid))
    }

    @Test
    fun reverseThenRecalculate() {
        // 復路: 逆順にすると 11:00, 9:10, 8:30, 8:00（到達済みは解除）。先頭（終点）を 13:00 にすると、差の絶対値を保って昇順に
        val rev = WaypointTimes.reverse(outbound.map { it.copy(reached = true) })
        assertEquals(listOf("終点", "道の駅", "展望台", "峠"), rev.map { it.name })
        assertEquals(List(4) { false }, rev.map { it.reached })
        val r = WaypointTimes.adjust(rev, 0, t("13:00"))
        // 差: 11:00→9:10 は 1:50、9:10→8:30 は 40 分、8:30→8:00 は 30 分
        assertEquals(listOf("13:00", "14:50", "15:30", "16:00"), targets(r))
        // 締切は各 WP の「目標 → 締切」の差のまま（道の駅 +10 分、展望台 +15 分）
        assertEquals(listOf(null, "15:00", "15:45", null), deadlines(r))
    }

    @Test
    fun blanksAreSkippedAndDisabledAreIncluded() {
        val wps = listOf(
            wp("A", "09:00"),
            wp("空欄", null),
            wp("無効", "09:20", enabled = false),
            wp("締切だけ", null, "10:00"),
            wp("B", "10:00"),
        )
        val r = WaypointTimes.adjust(wps, 0, t("12:00"))
        // 空欄の WP はそのまま（締切だけ入っていても変えない）。隣どうしの差は、時刻の入った WP どうし（A→無効→B）で数える
        assertEquals(listOf("12:00", null, "12:20", null, "13:00"), targets(r))
        assertEquals("10:00", r[3].deadlineTime.toString())
        // 基準の既定: 先頭の、有効で目標時刻の入った WP
        assertEquals(0, WaypointTimes.defaultBaseIndex(wps))
        assertEquals(2, WaypointTimes.defaultBaseIndex(listOf(wp("x", null), wp("y", null, enabled = true), wp("z", "08:00", enabled = true))))
        assertEquals(1, WaypointTimes.defaultBaseIndex(listOf(wp("無効", "07:00", enabled = false), wp("有効", "08:00"))))
        assertNull(WaypointTimes.defaultBaseIndex(listOf(wp("x", null))))
    }

    @Test
    fun acrossMidnight() {
        // 23:40 → 23:50 → 00:10（日付をまたいで 20 分）→ 00:30（締切 00:40）
        val wps = listOf(wp("A", "23:40"), wp("B", "23:50"), wp("C", "00:10"), wp("D", "00:30", "00:40"))
        val r = WaypointTimes.adjust(wps, 0, t("23:00"))
        assertEquals(listOf("23:00", "23:10", "23:30", "23:50"), targets(r))
        assertEquals("00:00", r[3].deadlineTime.toString())
        // 逆に、日付をまたぐように後ろへずらす
        val later = WaypointTimes.adjust(wps, 0, t("23:55"))
        assertEquals(listOf("23:55", "00:05", "00:25", "00:45"), targets(later))
        // 基準の前へさかのぼって日付をまたぐ
        val back = WaypointTimes.adjust(wps, 3, t("00:05"))
        assertEquals(listOf("23:15", "23:25", "23:45", "00:05"), targets(back))
    }

    @Test
    fun baseWithoutTimeKeepsTheList() {
        val wps = listOf(wp("A", null), wp("B", "09:00"))
        assertSame(wps, WaypointTimes.adjust(wps, 0, t("10:00")))
    }
}
