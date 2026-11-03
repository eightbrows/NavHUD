package io.github.eightbrows.navhud.core.io

/** 文字の値を覚える所（端末では SharedPreferences。テストでは Map）。 */
interface KeyValueStore {
    fun get(key: String): String?
    fun put(key: String, value: String)
    fun remove(vararg keys: String)
}

/**
 * 前回のファイルの覚え（20261102-D01 にまとめた。WP リストと、リプレイの track.csv / GpsLogger の zip）。
 * 覚えているファイル（uri）は、次の起動で自動で開く（WP リストは設定「前回の WP リストを自動で開く」が ON のとき）。
 * zip のときは、選んだセッション（zip の中の track.csv の名前。entryKey）も覚える。
 * クリア（forget）すると両方を忘れ、次の起動では何も開かない。Android に依存しない。
 */
class LastFileMemory(private val kv: KeyValueStore, private val uriKey: String, private val entryKey: String? = null) {

    /** 覚えているファイル（URI の文字）。なければ null */
    val uri: String? get() = kv.get(uriKey)

    /** 覚えている zip のセッション。zip でない・選んでいない・entryKey がなければ null */
    val entry: String? get() = entryKey?.let(kv::get)

    /** ファイルを覚える。前のファイルで選んだセッションは忘れる */
    fun remember(uri: String) {
        kv.put(uriKey, uri)
        entryKey?.let { kv.remove(it) }
    }

    /** zip の中で選んだセッションを覚える（entryKey がなければ何もしない） */
    fun rememberEntry(entry: String) {
        entryKey?.let { kv.put(it, entry) }
    }

    /** 忘れる（クリア・読めなかったとき） */
    fun forget() {
        kv.remove(*listOfNotNull(uriKey, entryKey).toTypedArray())
    }
}
