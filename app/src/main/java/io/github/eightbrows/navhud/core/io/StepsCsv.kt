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
 * 時刻の基準の異常（timebaseAnomalies）は detector・counter ごとに持つ（20261125-D01）。
 * 使えないとみなすのは、detector が 1 以上・counter が 2 以上のとき（usable）。仕様の 5 章「この修正より前の版で記録したセッションでは、
 * counter の基準値が 1 件数えられていることがある。counter の値が 1 なら問題ないことが多い」により、counter の 1 は許す
 */
data class StepsMeta(val status: String?, val detectorAnomalies: Long, val counterAnomalies: Long = 0) {
    /** detector・counter の合計 */
    val timebaseAnomalies: Long get() = detectorAnomalies + counterAnomalies

    /** 時刻合わせに使えるか（detector は 0、counter は 1 まで） */
    val usable: Boolean get() = detectorAnomalies <= 0 && counterAnomalies <= 1

    companion object {
        val NONE = StepsMeta(null, 0)

        /** meta.json の文字から読む。JSON として読めなければ NONE（歩数の記録の様子が分からないだけで、軌跡は読める） */
        fun parse(text: String): StepsMeta {
            val root = runCatching { MiniJson.parse(text) }.getOrNull() as? Map<*, *> ?: return NONE
            val steps = root["steps"] as? Map<*, *> ?: return NONE
            fun anomalies(key: String): Long = ((steps[key] as? Map<*, *>)?.get("timebaseAnomalies") as? Number)?.toLong() ?: 0L
            return StepsMeta(steps["status"] as? String, anomalies("detector"), anomalies("counter"))
        }
    }
}

/**
 * 歩数を読んだ結果（20261124-D01）。軌跡と一緒に持つ。
 * - NONE: steps.csv がない（古い記録・歩数の設定がオフ・権限なし・センサーなし）。エラーにしない
 * - EMPTY: steps.csv はあるが、歩数の行がない（ヘッダーだけ）
 * - TIMEBASE: meta.json の timebaseAnomalies が、detector で 1 以上・counter で 2 以上（時刻合わせができない疑い）なので使わない（20261125-D01 から counter の 1 は許す）
 * - NO_ELAPSED: 軌跡（track.csv）に elapsed_realtime_ns がない点があるので、時刻合わせができず使わない
 * - OK: 使う
 */
enum class StepsStatus { NONE, EMPTY, TIMEBASE, NO_ELAPSED, OK }

data class StepsLoad(
    val status: StepsStatus,
    val csv: StepsCsvResult? = null,
    val meta: StepsMeta = StepsMeta.NONE,
    /** 一時停止からの再開点（track.csv の gap_before=true の点）の経過時間 [ns]。区間の境目 */
    val resumeElapsedNs: List<Long> = emptyList(),
) {
    /**
     * 合計の歩数（20261125-D01。使えるときだけ。知らせに出す）: counter があれば区間ごとの「最後の値 − 基準値」の合計（CounterSegments）、
     * counter がなければ detector の数
     */
    val steps: Int? get() = if (status == StepsStatus.OK) csv?.let { totalSteps(it, resumeElapsedNs) } else null

    companion object {
        val NONE = StepsLoad(StepsStatus.NONE)

        /**
         * steps.csv の文字（なければ null）・meta.json の文字（なければ null）・軌跡の点の elapsed_realtime_ns・再開点の経過時間から決める。
         * 軌跡に点がない・どれかの点に elapsed がないときは NO_ELAPSED（steps.csv があるときだけ）
         */
        fun of(stepsText: String?, metaText: String?, trackElapsedNs: List<Long?>, resumeElapsedNs: List<Long> = emptyList()): StepsLoad {
            if (stepsText == null) return NONE
            val csv = StepsCsv.parse(stepsText)
            val meta = metaText?.let { StepsMeta.parse(it) } ?: StepsMeta.NONE
            val status = when {
                csv.isEmpty -> StepsStatus.EMPTY
                !meta.usable -> StepsStatus.TIMEBASE
                trackElapsedNs.isEmpty() || trackElapsedNs.any { it == null } -> StepsStatus.NO_ELAPSED
                else -> StepsStatus.OK
            }
            return StepsLoad(status, csv, meta, resumeElapsedNs.sorted())
        }

        /** 合計の歩数: counter があれば区間ごとの合計、なければ detector の数 */
        fun totalSteps(csv: StepsCsvResult, resumeElapsedNs: List<Long>): Int =
            if (csv.counter.isEmpty()) csv.detector.size else CounterSegments.of(csv.counter, resumeElapsedNs).sumOf { it.steps }.toInt()
    }
}

/**
 * counter の区間（20261125-D01。仕様の 3 章「セッション全体の歩数」）。区間は一時停止で区切られた記録の範囲で、境目は track.csv の
 * gap_before=true の点（再開後の最初の測位点）の経過時間。counter の行は recv_elapsed_ns（届いた時刻。ない行は event_elapsed_ns）で区間に振り分ける
 * （基準値の行の時刻は一時停止より前のことがあるため）。区間の最初に届いた行が基準値で、区間の歩数は「最後の値 − 基準値」
 * （負になれば 0）。基準値の行の時刻は解析・REPLAY に使わない（rows には入れない）
 */
data class CounterSegments(
    /** 区間の番号（0 から。再開ごとに 1 つ増える） */
    val index: Int,
    val baseline: StepEvent,
    /** 基準値のあとの行（届いた順） */
    val rows: List<StepEvent>,
) {
    val steps: Long get() = maxOf(0L, (rows.lastOrNull()?.count ?: baseline.count ?: 0L) - (baseline.count ?: 0L))

    companion object {
        fun of(counter: List<StepEvent>, resumeElapsedNs: List<Long>): List<CounterSegments> {
            val bounds = resumeElapsedNs.sorted()
            fun recv(e: StepEvent) = e.recvElapsedNs ?: e.eventElapsedNs
            // 区間の番号 = 届いた時刻以前の再開点の数
            return counter.sortedBy { recv(it) }
                .groupBy { e -> bounds.count { it <= recv(e) } }
                .toSortedMap()
                .map { (k, rows) -> CounterSegments(k, rows.first(), rows.drop(1)) }
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
