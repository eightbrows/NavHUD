package io.github.eightbrows.navhud.core.view

/**
 * 上部バーの文字の倍率（20261025-D09）: ボタンの文字の幅の合計と、それ以外の決まった幅（余白・枠・すき間）から、
 * 入りきらないときだけ文字を全部同じ倍率で小さくする。入るときは 1（縮めない）。Android に依存しない。
 */
object TopBarFit {
    /** これより小さくはしない（それでも入らないときは、左のボタンの列が切れて、右の表示モードと ⚙ は残る） */
    const val MIN_SCALE = 0.6f

    /**
     * @param availDp 上部バーの幅 [dp]
     * @param textDp ボタンの文字の幅の合計 [dp]（倍率 1 のとき）
     * @param fixedDp 文字のほかの幅の合計 [dp]（ボタンの内側の余白と枠、ボタンの間のすき間、上部バーの左右の余白）
     */
    fun textScale(availDp: Float, textDp: Float, fixedDp: Float): Float {
        if (textDp <= 0f) return 1f
        return ((availDp - fixedDp) / textDp).coerceIn(MIN_SCALE, 1f)
    }
}
