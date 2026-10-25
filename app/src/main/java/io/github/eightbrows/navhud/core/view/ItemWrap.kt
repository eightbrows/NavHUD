package io.github.eightbrows.navhud.core.view

/**
 * 項目の折り返し（20261025-D13。WP設定の一覧の「標高・目標・締切・半径」の行と、到達の理由の行）。
 * 項目を順に 1 行に並べ、次の項目が幅に入らないときは、その項目から次の行に送る（項目の途中では切らない）。
 * 全部が 1 行に入るときは 1 行のまま。1 つで幅に入らない項目は、その項目だけの行にする（画面の側で端を … にする）。
 * Android に依存しない。
 */
object ItemWrap {
    /** 丸めの分の余裕 [px] */
    private const val EPS = 0.5f

    /**
     * @param widths 項目ごとの幅（並びの順）
     * @param gap 項目の間の幅（区切りの空白）
     * @param avail 使える幅
     * @return 行ごとの項目の番号（widths の添字）。項目がなければ空
     */
    fun lines(widths: List<Float>, gap: Float, avail: Float): List<List<Int>> {
        val out = mutableListOf<MutableList<Int>>()
        var used = 0f
        widths.forEachIndexed { i, w ->
            val cur = out.lastOrNull()
            if (cur != null && used + gap + w <= avail + EPS) {
                cur += i
                used += gap + w
            } else {
                out += mutableListOf(i)
                used = w
            }
        }
        return out
    }
}
