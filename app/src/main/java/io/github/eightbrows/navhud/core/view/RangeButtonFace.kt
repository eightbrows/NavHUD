package io.github.eightbrows.navhud.core.view

/**
 * 縮尺のボタン（右の操作列の真ん中、§6.1）の中身。text はボタンに出す文字、filled は塗りつぶす（反転）か。
 * - AUTO: R1 の距離だけ（例「500m」）を塗りつぶしで
 * - 手動: R1 の距離だけを枠だけで
 * - PAN 中: 黄色で「PAN」と距離の2行（§6.10。塗らない）
 */
data class RangeButtonFace(val text: String, val filled: Boolean, val pan: Boolean) {
    companion object {
        fun of(rangeM: Double, auto: Boolean, pan: Boolean): RangeButtonFace {
            val r1 = HudFormat.rangeLabel(rangeM)
            return if (pan) RangeButtonFace("PAN\n$r1", filled = false, pan = true) else RangeButtonFace(r1, filled = auto, pan = false)
        }
    }
}
