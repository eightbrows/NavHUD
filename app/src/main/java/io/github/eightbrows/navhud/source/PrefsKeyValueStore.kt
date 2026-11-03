package io.github.eightbrows.navhud.source

import android.content.SharedPreferences
import androidx.core.content.edit
import io.github.eightbrows.navhud.core.io.KeyValueStore

/** SharedPreferences を KeyValueStore として使う（前回のファイルの覚え。20261102-D01）。 */
class PrefsKeyValueStore(private val prefs: SharedPreferences) : KeyValueStore {
    override fun get(key: String): String? = prefs.getString(key, null)
    override fun put(key: String, value: String) = prefs.edit { putString(key, value) }
    override fun remove(vararg keys: String) = prefs.edit { keys.forEach { remove(it) } }
}
