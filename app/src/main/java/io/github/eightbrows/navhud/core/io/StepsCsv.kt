package io.github.eightbrows.navhud.core.io

/** 歩数の記録の種類（steps.csv の type 列。20261124-D01） */
enum class StepType { DETECTOR, COUNTER }

/** steps.csv の 1 行（時刻は端末の起動からの経過時間 [ns]。epoch_ms は参考で、時刻合わせには使わない） */
data class StepEvent(
    val type: StepType,
    val eventElapsedNs: Long,
    val recvElapsedNs: Long? = null,
    val epochMs: Long? = null,
    /** counter の累計歩数（detector は null） */
    val count: Long? = null,
)

/**
 * 読んだ steps.csv（20261124-D01）。detector・counter とも event_elapsed_ns の順に並べ替えてある
 * （ファイルの行は届いた順で、時刻の順とは限らないため）。skippedLines は読めなかった行の数（知らない type も含む）
 */
data class StepsCsvResult(val detector: List<StepEvent>, val counter: List<StepEvent>, val skippedLines: Int) {
    val isEmpty: Boolean get() = detector.isEmpty() && counter.isEmpty()
}

/**
 * GpsLogger の歩数の記録（steps.csv。GpsLogger エクスポートZIP 読み込み仕様（歩数 steps.csv））を読む。Android に依存しない。
 * - 1 行目はヘッダー。列は名前で引く（列の順の違い・知らない列・末尾の欠けに強い）。区切りはカンマで、引用符は使わない
 * - 改行は LF・CRLF のどちらも受ける（行の端の空白・CR は取る）
 * - type が detector / counter でない行、event_elapsed_ns が読めない行、counter で count が読めない行は飛ばして数える
 * - 空行は数えない。ヘッダーだけ・空のファイルは、どちらも空の結果
 */
object StepsCsv {

    fun parse(text: String): StepsCsvResult = parse(text.lineSequence())

    fun parse(lines: Sequence<String>): StepsCsvResult {
        val iter = lines.iterator()
        if (!iter.hasNext()) return StepsCsvResult(emptyList(), emptyList(), 0)
        val header = iter.next().removePrefix("\uFEFF").split(',').map { it.trim() }
        val col = header.withIndex().associate { (i, name) -> name to i }
        val iType = col["type"]
        val iEvent = col["event_elapsed_ns"]
        val iRecv = col["recv_elapsed_ns"]
        val iEpoch = col["epoch_ms"]
        val iCount = col["count"]
        val det = ArrayList<StepEvent>()
        val cnt = ArrayList<StepEvent>()
        var skipped = 0
        while (iter.hasNext()) {
            val line = iter.next()
            if (line.isBlank()) continue
            val f = line.split(',')
            fun str(i: Int?): String? = i?.let { f.getOrNull(it) }?.trim()?.takeIf { it.isNotEmpty() }
            fun long(i: Int?): Long? = str(i)?.toLongOrNull()
            val type = when (str(iType)) {
                "detector" -> StepType.DETECTOR
                "counter" -> StepType.COUNTER
                else -> null
            }
            val event = long(iEvent)
            val count = long(iCount)
            if (type == null || event == null || (type == StepType.COUNTER && count == null)) {
                skipped++
                continue
            }
            val e = StepEvent(type, event, long(iRecv), long(iEpoch), if (type == StepType.COUNTER) count else null)
            if (type == StepType.DETECTOR) det += e else cnt += e
        }
        // 届いた順 → 時刻の順（同じ時刻は届いた順のまま）
        return StepsCsvResult(det.sortedBy { it.eventElapsedNs }, cnt.sortedBy { it.eventElapsedNs }, skipped)
    }
}

/**
 * meta.json の歩数の記録状態（トップレベルの steps。20261124-D01）。steps がない古い meta.json は status = null・異常 0。
 * timebaseAnomalies は detector・counter の合計（どちらかが 0 より大きければ、歩数の時刻合わせができない疑いがある）
 */
data class StepsMeta(val status: String?, val timebaseAnomalies: Long) {
    companion object {
        val NONE = StepsMeta(null, 0)

        /** meta.json の文字から読む。JSON として読めなければ NONE（歩数の記録の様子が分からないだけで、軌跡は読める） */
        fun parse(text: String): StepsMeta {
            val root = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *> ?: return NONE
            val steps = root["steps"] as? Map<*, *> ?: return NONE
            fun anomalies(key: String): Long = ((steps[key] as? Map<*, *>)?.get("timebaseAnomalies") as? Number)?.toLong() ?: 0L
            return StepsMeta(steps["status"] as? String, anomalies("detector") + anomalies("counter"))
        }
    }
}

/**
 * 歩数を読んだ結果（20261124-D01）。軌跡と一緒に持つ。
 * - NONE: steps.csv がない（古い記録・歩数の設定がオフ・権限なし・センサーなし）。エラーにしない
 * - EMPTY: steps.csv はあるが、歩数の行がない（ヘッダーだけ）
 * - TIMEBASE: meta.json の timebaseAnomalies が 0 より大きい（時刻合わせができない疑い）ので使わない
 * - NO_ELAPSED: 軌跡（track.csv）に elapsed_realtime_ns がない点があるので、時刻合わせができず使わない
 * - OK: 使う
 */
enum class StepsStatus { NONE, EMPTY, TIMEBASE, NO_ELAPSED, OK }

data class StepsLoad(val status: StepsStatus, val csv: StepsCsvResult? = null, val meta: StepsMeta = StepsMeta.NONE) {
    /** 歩数（detector の数。使えるときだけ。知らせに出す） */
    val steps: Int? get() = if (status == StepsStatus.OK) csv?.detector?.size else null

    companion object {
        val NONE = StepsLoad(StepsStatus.NONE)

        /**
         * steps.csv の文字（なければ null）・meta.json の文字（なければ null）・軌跡の点の elapsed_realtime_ns から決める。
         * 軌跡に点がない・どれかの点に elapsed がないときは NO_ELAPSED（steps.csv があるときだけ）
         */
        fun of(stepsText: String?, metaText: String?, trackElapsedNs: List<Long?>): StepsLoad {
            if (stepsText == null) return NONE
            val csv = StepsCsv.parse(stepsText)
            val meta = metaText?.let { StepsMeta.parse(it) } ?: StepsMeta.NONE
            val status = when {
                csv.isEmpty -> StepsStatus.EMPTY
                meta.timebaseAnomalies > 0 -> StepsStatus.TIMEBASE
                trackElapsedNs.isEmpty() || trackElapsedNs.any { it == null } -> StepsStatus.NO_ELAPSED
                else -> StepsStatus.OK
            }
            return StepsLoad(status, csv, meta)
        }
    }
}

/**
 * 小さな JSON の読み取り（meta.json 用。20261124-D01）。オブジェクトは Map<String, Any?>、配列は List<Any?>、数は Long か Double、
 * 文字・true / false・null。Android の org.json は単体テストで使えないので、core ではこれを使う
 */
internal object MiniJson {
    fun parse(text: String): Any? {
        val p = P(text)
        p.ws()
        val v = p.value()
        p.ws()
        require(p.i == text.length) { "JSON の後ろに文字がある" }
        return v
    }

    private class P(val s: String) {
        var i = 0
        fun ws() { while (i < s.length && s[i].isWhitespace()) i++ }
        fun value(): Any? {
            require(i < s.length) { "JSON が途中で終わった" }
            return when (val c = s[i]) {
                '{' -> obj()
                '[' -> arr()
                '"' -> str()
                't' -> lit("true", true)
                'f' -> lit("false", false)
                'n' -> lit("null", null)
                else -> if (c == '-' || c.isDigit()) num() else throw IllegalArgumentException("JSON の値でない: $c")
            }
        }
        fun lit(w: String, v: Any?): Any? {
            require(s.startsWith(w, i)) { "JSON の値でない" }
            i += w.length
            return v
        }
        fun obj(): Map<String, Any?> {
            val m = LinkedHashMap<String, Any?>()
            i++
            ws()
            if (s[i] == '}') { i++; return m }
            while (true) {
                ws()
                val k = str()
                ws()
                require(s[i] == ':') { "JSON の : がない" }
                i++
                ws()
                m[k] = value()
                ws()
                when (s[i]) {
                    ',' -> i++
                    '}' -> { i++; return m }
                    else -> throw IllegalArgumentException("JSON の , か } がない")
                }
            }
        }
        fun arr(): List<Any?> {
            val a = ArrayList<Any?>()
            i++
            ws()
            if (s[i] == ']') { i++; return a }
            while (true) {
                ws()
                a += value()
                ws()
                when (s[i]) {
                    ',' -> i++
                    ']' -> { i++; return a }
                    else -> throw IllegalArgumentException("JSON の , か ] がない")
                }
            }
        }
        fun str(): String {
            require(s[i] == '"') { "JSON の文字でない" }
            i++
            val b = StringBuilder()
            while (true) {
                val c = s[i++]
                when (c) {
                    '"' -> return b.toString()
                    '\\' -> {
                        when (val e = s[i++]) {
                            '"', '\\', '/' -> b.append(e)
                            'b' -> b.append('\b')
                            'f' -> b.append('\u000C')
                            'n' -> b.append('\n')
                            'r' -> b.append('\r')
                            't' -> b.append('\t')
                            'u' -> { b.append(s.substring(i, i + 4).toInt(16).toChar()); i += 4 }
                            else -> throw IllegalArgumentException("JSON の \\ のあとが読めない")
                        }
                    }
                    else -> b.append(c)
                }
            }
        }
        fun num(): Any {
            val st = i
            if (s[i] == '-') i++
            while (i < s.length && (s[i].isDigit() || s[i] in ".eE+-")) i++
            val t = s.substring(st, i)
            return t.toLongOrNull() ?: t.toDouble()
        }
    }
}
