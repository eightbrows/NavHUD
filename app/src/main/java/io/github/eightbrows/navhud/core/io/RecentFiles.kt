package io.github.eightbrows.navhud.core.io

import io.github.eightbrows.navhud.core.Tuning

/** 最近のファイルの 1 件（20261104-D03）: ファイル（URI の文字）、名前、最後に開いた時刻 [epoch ms] */
data class RecentFile(val uri: String, val name: String, val openedAtMs: Long)

/**
 * 最近のファイル（20261104-D03。ファイルの画面の「最近のファイル」）。WP リストと軌跡で別々に持つ（保存の名前 key を分ける）。
 * 新しい順に max 件まで。同じファイルを開き直したら、いちばん上に移して時刻を新しくする。Android に依存しない。
 * 入れるのは、読み込めた（種類の合った）ファイルだけ（呼び出し側。違う種類のファイルは入れない）。
 * 読み取りの権限は呼び出し側が扱う: 履歴から外れたファイルは、shouldRelease で返してよいか確かめてから返す。
 */
class RecentFiles(private val kv: KeyValueStore, private val key: String, private val max: Int = Tuning.RECENT_FILES_MAX) {

    /** 新しい順 */
    fun list(): List<RecentFile> = decode(kv.get(key))

    /**
     * 開いたファイルを入れる（いちばん上。前からあれば移す）。
     * @return 入りきらずに外れたファイル（古い順の後ろから。権限を返すか確かめるため）
     */
    fun add(file: RecentFile): List<RecentFile> {
        val all = listOf(file) + list().filter { it.uri != file.uri }
        save(all.take(max))
        return all.drop(max)
    }

    /**
     * ファイルを開いた結果を反映する: 読み込めた（error が null）なら入れる。違う種類のファイル（FileKindException。20261103-D01）なら
     * 入れない（前から入っていれば消す）。ほかの理由で読めなかったときは変えない（次に画面を開いたとき「開けません」になる）。
     * @return この履歴から外れたファイル（URI の文字。入りきらなかった・消した）
     */
    fun recordOpen(file: RecentFile, error: Throwable?): List<String> = when {
        error == null -> add(file).map { it.uri }
        error is FileKindException -> listOfNotNull(remove(file.uri)?.uri)
        else -> emptyList()
    }

    /** ✕ で消す。消したものを返す（なければ null） */
    fun remove(uri: String): RecentFile? {
        val now = list()
        val hit = now.firstOrNull { it.uri == uri } ?: return null
        save(now.filter { it.uri != uri })
        return hit
    }

    private fun save(files: List<RecentFile>) {
        if (files.isEmpty()) kv.remove(key) else kv.put(key, encode(files))
    }

    companion object {
        /**
         * 保存の文字: 1 行に 1 件（新しい順）。「時刻 TAB URI TAB 名前」。名前の中のタブ・改行は空白にする
         * （URI はタブ・改行を含まない）。読めない行は飛ばす
         */
        fun encode(files: List<RecentFile>): String = files.joinToString("\n") {
            "${it.openedAtMs}\t${it.uri}\t${it.name.replace(Regex("[\\t\\r\\n]"), " ")}"
        }

        fun decode(s: String?): List<RecentFile> {
            if (s.isNullOrEmpty()) return emptyList()
            return s.split('\n').mapNotNull { line ->
                val p = line.split('\t', limit = 3)
                val t = p.getOrNull(0)?.toLongOrNull() ?: return@mapNotNull null
                val uri = p.getOrNull(1)?.takeIf { it.isNotEmpty() } ?: return@mapNotNull null
                RecentFile(uri, p.getOrNull(2).orEmpty(), t)
            }.distinctBy { it.uri }
        }

        /**
         * ファイルの読み取りの権限を返してよいか（20261104-D03。前回のファイルの決まり LastFileMemory.shouldRelease の、見るファイルを増やした）:
         * まだ使っているファイル inUse（WP リストと軌跡の、最近のファイル・前回のファイル（読み込み中のファイル））のどれでもなければ返す
         */
        fun shouldRelease(uri: String, inUse: Collection<String?>): Boolean = uri !in inUse
    }
}
