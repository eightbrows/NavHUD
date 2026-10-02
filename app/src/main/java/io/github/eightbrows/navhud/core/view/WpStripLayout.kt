package io.github.eightbrows.navhud.core.view

/** 横並びの WP ボタン列（§6.4）の並べ方。Android に依存しない。 */
object WpStripLayout {

    /**
     * 左端（「WP設定」を除く）に来るボタンの番号。次の WP が左から2番目に来るようにする（1番目は1つ前の WP）。
     * 次の WP がリストの最初なら 0（左詰め）。次の WP がなければ null（動かさない）。
     */
    fun firstIndex(next: Int?): Int? = next?.let { (it - 1).coerceAtLeast(0) }

    /**
     * 右端に足す空き（ボタン何個分か）。リストの最後のほうでも、次の WP を2番目のまま置けるようにする
     * （右側は空いてよい）。visible は一度に見せる数。
     */
    fun trailingSlots(visible: Int): Int = (visible - 2).coerceAtLeast(0)
}
