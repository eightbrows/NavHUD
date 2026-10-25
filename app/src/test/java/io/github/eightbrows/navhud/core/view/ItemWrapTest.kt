package io.github.eightbrows.navhud.core.view

import org.junit.Assert.assertEquals
import org.junit.Test

/** 項目の折り返し（20261025-D13）: 入るときは 1 行、入らないときは項目の切れ目で次の行へ。 */
class ItemWrapTest {

    /** 等幅の文字の幅（1 文字 = 1）で項目の幅を作る */
    private fun w(vararg items: String) = items.map { it.length.toFloat() }

    @Test
    fun staysOnOneLineWhenItFits() {
        // 「標高 1300 m」だけ・「目標 09:10  締切 09:20」は 1 行のまま
        assertEquals(listOf(listOf(0)), ItemWrap.lines(w("標高 1300 m"), gap = 2f, avail = 30f))
        assertEquals(listOf(listOf(0, 1)), ItemWrap.lines(w("目標 09:10", "締切 09:20"), gap = 2f, avail = 30f))
        // ちょうど入る（8 + 2 + 8 = 18）
        assertEquals(listOf(listOf(0, 1)), ItemWrap.lines(w("目標 09:10", "締切 09:20"), gap = 2f, avail = 18f))
        // 項目がなければ行もない
        assertEquals(emptyList<List<Int>>(), ItemWrap.lines(emptyList(), gap = 2f, avail = 30f))
    }

    @Test
    fun movesTheItemThatDoesNotFitToTheNextLine() {
        // 「標高 1180 m  目標 08:30  締切 08:45」（9 + 2 + 8 + 2 + 8 = 29）が幅 25 に入らない: 締切だけ次の行へ（途中では切らない）
        val items = w("標高 1180 m", "目標 08:30", "締切 08:45")
        assertEquals(listOf(listOf(0, 1), listOf(2)), ItemWrap.lines(items, gap = 2f, avail = 25f))
        // 幅 15 なら 1 つずつ
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), ItemWrap.lines(items, gap = 2f, avail = 15f))
        // 次の行でも、入るだけ並べる（半径も入る）
        val four = w("標高 1180 m", "目標 08:30", "締切 08:45", "半径 200 m")
        assertEquals(listOf(listOf(0, 1), listOf(2, 3)), ItemWrap.lines(four, gap = 2f, avail = 25f))
    }

    @Test
    fun anItemWiderThanTheLineGetsItsOwnLine() {
        // 1 つで幅を超える項目は、その項目だけの行（前後の項目とは並べない）
        assertEquals(listOf(listOf(0), listOf(1), listOf(2)), ItemWrap.lines(listOf(5f, 40f, 5f), gap = 1f, avail = 20f))
    }

    @Test
    fun reachLineWrapsBetweenItemsToo() {
        // 到達の理由「シーク・真横 07:53:02 最接近 70 m」（区切りは空白 1 つ）
        val items = w("シーク・真横", "07:53:02", "最接近 70 m")
        assertEquals(listOf(listOf(0, 1, 2)), ItemWrap.lines(items, gap = 1f, avail = 30f))
        assertEquals(listOf(listOf(0, 1), listOf(2)), ItemWrap.lines(items, gap = 1f, avail = 20f))
    }
}
