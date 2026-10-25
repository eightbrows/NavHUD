package io.github.eightbrows.navhud.core.view

import kotlin.math.min

/** 数値の表示（§6.1）の欄。caption は値の左に小さく出す見出し（RATE は窓の秒数を付けて出す）。 */
enum class NumberField(val caption: String) {
    TIME("TIME"),
    ALT("ALT"),
    RATE("RATE"),
    HDG("HDG"),
    GS("GS"),
    ETA("ETA"),
    TGT("TGT"),
    DDL("DDL"),
    LAT_LON("LAT/LON"),

    /** 地上高（海抜高度 − 最高標高。20261025-D07） */
    AGL("AGL"),
}

/**
 * 欄のいちばん長くなる見出しと値（この幅を先に取る。等幅の文字）。small は値のあとに小さく（見出しと同じ大きさで）付ける文字
 * （HDG の方位ソースとコンパスの印）。
 */
data class NumberSample(val caption: String, val value: String, val small: String = "")

/**
 * 数値の表示の4行（20261025-D09 から。左の列 = 時刻と WP、右 = 自機の値）。各行の先頭が左の列の欄、そのあとが右の欄。
 * 画面（MainScreen の NumbersPanel）はこの順に並べる。
 */
object NumberRows {
    val ROWS: List<List<NumberField>> = listOf(
        listOf(NumberField.TIME, NumberField.LAT_LON),
        listOf(NumberField.ETA, NumberField.GS, NumberField.HDG),
        listOf(NumberField.TGT, NumberField.ALT, NumberField.AGL),
        listOf(NumberField.DDL, NumberField.RATE),
    )

    /**
     * 欄ごとの、いちばん長くなる見出しと値。TGT・DDL は ±12 時間まで（"+11:59:59"）、LAT/LON は日本の緯度経度、ALT は 4 桁、
     * AGL は負の 3 桁、RATE は 60 秒の窓で 10 km 近く・高低差 3 桁。HDG は方位と、方位ソースとコンパスの印 1 つ
     * （印が MAG と CAL の 2 つとも出るときは欄の端が切れる。今までと同じ）
     */
    val SAMPLES: Map<NumberField, NumberSample> = mapOf(
        NumberField.TIME to NumberSample("TIME", "88:88:88"),
        NumberField.ETA to NumberSample("ETA", "88:88:88"),
        NumberField.TGT to NumberSample("TGT", "+88:88:88"),
        NumberField.DDL to NumberSample("DDL", "+88:88:88"),
        NumberField.LAT_LON to NumberSample("LAT/LON", "88.88888, 888.88888"),
        NumberField.GS to NumberSample("GS", "888 km/h"),
        NumberField.HDG to NumberSample("HDG", "888°", " CMP CAL"),
        NumberField.ALT to NumberSample("ALT", "8888 m"),
        NumberField.AGL to NumberSample("AGL", "-888 m"),
        NumberField.RATE to NumberSample("RATE 60s", "88.8 km  +888 m"),
    )
}

/**
 * 数値の欄の幅の決め方（20261025-D09）。欄ごとの要る幅（見本の幅 [dp]）から、各行の欄の幅と文字の倍率を決める。Android に依存しない。
 * - 左の列（各行の先頭）の幅は、全部の行で同じ（いちばん幅のある欄に合わせる）
 * - 入りきらない行があるときは、全部の行の文字を同じ倍率で縮める（いちばん入りきらない行に合わせる。左の列がそろったまま）
 * - 余った幅は、その行の右の欄に同じだけ分ける
 */
object NumberLayout {
    /** scale: 文字の倍率（1 なら縮めない）。widths: 各行の欄の幅 [dp]（NumberRows.ROWS と同じ並び） */
    data class Result(val scale: Float, val widths: List<List<Float>>)

    /**
     * @param needs 各行の欄の要る幅 [dp]（文字の倍率 1 のとき）。各行の先頭が左の列
     * @param availDp 使える幅 [dp]
     * @param gapDp 欄の間のすき間 [dp]（縮めない）
     */
    fun fit(needs: List<List<Float>>, availDp: Float, gapDp: Float): Result {
        require(needs.isNotEmpty() && needs.all { it.isNotEmpty() })
        val col0 = needs.maxOf { it[0] }
        val scale = needs.minOf { row ->
            val text = col0 + row.drop(1).sum()
            val gaps = gapDp * (row.size - 1)
            (availDp - gaps) / text
        }.let { min(1f, it.coerceAtLeast(0f)) }
        val widths = needs.map { row ->
            val left = col0 * scale
            val right = row.drop(1).map { it * scale }
            if (right.isEmpty()) return@map listOf(availDp)
            val spare = (availDp - gapDp * (row.size - 1) - left - right.sum()).coerceAtLeast(0f) / right.size
            listOf(left) + right.map { it + spare }
        }
        return Result(scale, widths)
    }
}
