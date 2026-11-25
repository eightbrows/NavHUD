package io.github.eightbrows.navhud.core.io

import java.io.BufferedReader
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

/**
 * zip の中の1セッション（track.csv 1つ）。
 * @param entryName zip の中の track.csv の名前（例: session_20260814_075234/track.csv）。選んだセッションを覚えるのに使う
 * @param name セッションの名前（track.csv のあるフォルダの名前。zip の直下なら空）
 */
data class ZipSession(val entryName: String, val name: String, val summary: TrackSummary)

/** zip を読めなかった理由。 */
enum class SessionZipError {
    /** zip として読めない（壊れている・途中で切れている・zip ではない） */
    BROKEN,

    /** track.csv が1つも入っていない */
    NO_TRACK,
}

class SessionZipException(val error: SessionZipError, cause: Throwable? = null) : IOException(error.name, cause)

/** 1 セッションを読んだ結果（20261124-D01）: 軌跡と歩数（steps.csv。ない・使えないこともある） */
data class SessionData(val track: TrackParseResult, val steps: StepsLoad = StepsLoad.NONE)

/**
 * GpsLogger が書き出す zip（§6.7）を読む。中はセッションごとのフォルダで、その中に track.csv がある
 * （session_yyyyMMdd_HHmmss/track.csv。ほかに sats.csv・meta.json・track.gpx・track.kmz）。
 * 1つの zip に複数のセッションが入っていてもよい。track.csv 以外は読み飛ばす。
 * 中身をファイルに展開せず、流れ（ストリーム）のまま必要な track.csv だけを読む。
 */
object SessionZip {

    private const val TRACK_FILE = "track.csv"
    private const val STEPS_FILE = "steps.csv"
    private const val META_FILE = "meta.json"

    /** 読む steps.csv の名前（完全一致。GpsLogger エクスポートZIP 読み込み仕様（歩数 steps.csv）の 1 章。20261124-D01） */
    val STEPS_ENTRY = Regex("""^session_\d{8}_\d{6}/steps\.csv$""")

    /** ファイルの先頭の数バイトが zip の印（PK）か。 */
    fun isZip(head: ByteArray): Boolean =
        head.size >= 4 && head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte() &&
            ((head[2].toInt() == 3 && head[3].toInt() == 4) || (head[2].toInt() == 5 && head[3].toInt() == 6))

    /**
     * 中のセッションの一覧（開始時刻の新しい順。時刻がないものは最後。同じなら名前の順）。
     * 各 track.csv は1回だけ流し読みして、点の数と最初・最後の時刻を数える。
     * @throws SessionZipException 壊れた zip（BROKEN）、track.csv がない（NO_TRACK）
     */
    fun list(input: InputStream): List<ZipSession> {
        val out = mutableListOf<ZipSession>()
        scan(input) { zip, entryName ->
            out += ZipSession(entryName, sessionName(entryName), TrackCsv.summarize(lines(zip)))
            false
        }
        if (out.isEmpty()) throw SessionZipException(SessionZipError.NO_TRACK)
        return out.sortedWith(compareBy<ZipSession>({ it.summary.startMs == null }, { -(it.summary.startMs ?: 0L) }, { it.name }))
    }

    /**
     * entryName の track.csv を読む。その名前の track.csv がなければ null。
     * @throws SessionZipException 壊れた zip（BROKEN）
     */
    fun read(input: InputStream, entryName: String): TrackParseResult? = readSession(input, entryName)?.track

    /**
     * entryName の track.csv と、同じセッションの歩数（steps.csv・meta.json。20261124-D01）を読む。track.csv がなければ null。
     * steps.csv は STEPS_ENTRY（^session_\d{8}_\d{6}/steps\.csv$）に完全一致する名前で、track.csv と同じフォルダのものだけ
     * （zip の中の順は決まっていないので、最後まで見る）。sats.csv など、ほかのファイルは読み飛ばす。
     * @throws SessionZipException 壊れた zip（BROKEN）
     */
    fun readSession(input: InputStream, entryName: String): SessionData? {
        val folder = entryName.substringBeforeLast('/', "")
        val stepsName = "$folder/$STEPS_FILE".takeIf { STEPS_ENTRY.matches(it) }
        val metaName = "$folder/$META_FILE".takeIf { stepsName != null }
        var track: TrackParseResult? = null
        var steps: String? = null
        var meta: String? = null
        scan(input, extra = setOfNotNull(stepsName, metaName)) { zip, name ->
            when (name) {
                entryName -> track = TrackCsv.parse(lines(zip))
                stepsName -> steps = text(zip)
                metaName -> meta = text(zip)
            }
            false
        }
        val t = track ?: return null
        return SessionData(t, StepsLoad.of(steps, meta, t.fixes.map { it.elapsedNs }, t.resumeElapsedNs))
    }

    /**
     * フォルダの中のセッション（track.csv と同じフォルダの steps.csv・meta.json。20261124-D01）。zip と同じ決まりで、フォルダの名前が
     * session_yyyyMMdd_HHmmss でなければ歩数は読まない。stepsText・metaText はそのファイルの中身（なければ null）
     */
    fun stepsForFolder(folderName: String, stepsText: String?, metaText: String?, track: TrackParseResult): StepsLoad =
        if (STEPS_ENTRY.matches("$folderName/$STEPS_FILE")) StepsLoad.of(stepsText, metaText, track.fixes.map { it.elapsedNs }, track.resumeElapsedNs) else StepsLoad.NONE

    /** track.csv のあるフォルダの名前（いちばん下のフォルダ）。 */
    fun sessionName(entryName: String): String =
        entryName.substringBeforeLast('/', "").substringAfterLast('/')

    /** zip の中の track.csv（と extra の名前の項目）を順に見る。onTrack が true を返したら、そこでやめる。 */
    private fun scan(input: InputStream, extra: Set<String> = emptySet(), onTrack: (ZipInputStream, String) -> Boolean) {
        try {
            val buffered = input.buffered()
            // 項目が1つもない空の zip（PK 05 06 で始まる）は、壊れているとはみなさない
            buffered.mark(4)
            val head = ByteArray(4)
            val n = buffered.read(head)
            buffered.reset()
            val emptyZip = n == 4 && isZip(head) && head[2].toInt() == 5
            val zip = ZipInputStream(buffered)
            var any = emptyZip
            while (true) {
                val entry = zip.nextEntry ?: break
                any = true
                val name = entry.name.replace('\\', '/')
                if (!entry.isDirectory && (name == TRACK_FILE || name.endsWith("/$TRACK_FILE") || name in extra)) {
                    if (onTrack(zip, name)) return
                }
                zip.closeEntry()
            }
            // 1つも項目が読めない: zip ではないか、先頭から壊れている
            if (!any) throw SessionZipException(SessionZipError.BROKEN)
        } catch (e: SessionZipException) {
            throw e
        } catch (e: IOException) {
            // ZipException（中身が壊れている）・EOFException（途中で切れている）など
            throw SessionZipException(SessionZipError.BROKEN, e)
        } catch (e: IllegalArgumentException) {
            // 項目の名前が読めない zip
            throw SessionZipException(SessionZipError.BROKEN, e)
        }
    }

    /** 今の項目の文字（UTF-8。zip の流れは閉じない） */
    private fun text(zip: ZipInputStream): String = String(zip.readBytes(), Charsets.UTF_8)

    /** 今の項目の行（zip の流れは閉じない。項目の終わりで終わる）。 */
    private fun lines(zip: ZipInputStream): Sequence<String> =
        BufferedReader(InputStreamReader(zip, Charsets.UTF_8)).lineSequence()
}
