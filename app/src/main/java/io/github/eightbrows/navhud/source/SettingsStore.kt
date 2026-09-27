package io.github.eightbrows.navhud.source

import android.content.Context
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SettingsCodec
import io.github.eightbrows.navhud.core.nav.SourceKind

/** 設定の保存（SharedPreferences）。値の変換は SettingsCodec。 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): NavSettings = SettingsCodec.decode(prefs.all.mapValues { it.value?.toString() })

    fun save(s: NavSettings) {
        val e = prefs.edit()
        SettingsCodec.encode(s).forEach { (k, v) -> e.putString(k, v) }
        e.apply()
    }

    /** INPUT（LIVE / REPLAY）。既定は LIVE。 */
    var input: SourceKind
        get() = SourceKind.entries.firstOrNull { it.name == prefs.getString(KEY_INPUT, null) } ?: SourceKind.LIVE
        set(v) = prefs.edit().putString(KEY_INPUT, v.name).apply()

    fun clear() = prefs.edit().clear().apply()

    private companion object {
        const val PREFS = "settings"
        const val KEY_INPUT = "input"
    }
}
