package io.github.eightbrows.navhud.source

import android.content.Context
import androidx.core.content.edit
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SettingsCodec
import io.github.eightbrows.navhud.core.nav.SourceKind

/** 設定の保存（SharedPreferences）。値の変換は SettingsCodec。 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): NavSettings = SettingsCodec.decode(prefs.all.mapValues { it.value?.toString() })

    fun save(s: NavSettings) {
        prefs.edit { SettingsCodec.encode(s).forEach { (k, v) -> putString(k, v) } }
    }

    /** INPUT（LIVE / REPLAY）。既定は LIVE。 */
    var input: SourceKind
        get() = SourceKind.entries.firstOrNull { it.name == prefs.getString(KEY_INPUT, null) } ?: SourceKind.LIVE
        set(v) = prefs.edit { putString(KEY_INPUT, v.name) }

    internal companion object {
        /** 設定のファイル名。表示言語の選択（AppLanguage。Android 12 以前）もここに置く */
        const val PREFS = "settings"
        private const val KEY_INPUT = "input"
    }
}
