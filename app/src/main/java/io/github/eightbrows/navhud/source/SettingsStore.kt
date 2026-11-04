package io.github.eightbrows.navhud.source

import android.content.Context
import androidx.core.content.edit
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.SettingsCodec
import io.github.eightbrows.navhud.core.nav.SourceKind

/** 設定の保存（SharedPreferences）。値の変換は SettingsCodec。 */
class SettingsStore(context: Context) {

    private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun load(): NavSettings {
        // もう使わない値は読まずに消す（20261104-D03: RATE の窓）
        if (SettingsCodec.OBSOLETE_KEYS.any(prefs::contains)) prefs.edit { SettingsCodec.OBSOLETE_KEYS.forEach { remove(it) } }
        return SettingsCodec.decode(prefs.all.mapValues { it.value?.toString() })
    }

    fun save(s: NavSettings) {
        prefs.edit {
            SettingsCodec.encode(s).forEach { (k, v) -> putString(k, v) }
            // もう使わない値は消す（20261104-D03: RATE の窓）
            SettingsCodec.OBSOLETE_KEYS.forEach { remove(it) }
        }
    }

    /** INPUT（LIVE / REPLAY）。既定は LIVE。 */
    var input: SourceKind
        get() = SourceKind.entries.firstOrNull { it.name == prefs.getString(KEY_INPUT, null) } ?: SourceKind.LIVE
        set(v) = prefs.edit { putString(KEY_INPUT, v.name) }

    /**
     * 設定画面のスクロール位置 [px]（20261023-D01）。閉じたときの位置を覚え、次に開いたときにその位置から出す。
     * NavSettings の外なので、「設定を初期値に戻す」では変わらない
     */
    var settingsScrollY: Int
        get() = runCatching { prefs.getInt(KEY_SETTINGS_SCROLL, 0) }.getOrDefault(0)
        set(v) = prefs.edit { putInt(KEY_SETTINGS_SCROLL, v.coerceAtLeast(0)) }

    /**
     * 設定画面の閉じたセクション（20261104-D02。SettingsOutline.encodeClosed の文字）。スクロール位置と同じ画面の状態で、
     * NavSettings の外なので「設定を初期値に戻す」では変わらない。覚えがなければ（初めて）全部開く
     */
    var settingsClosedSections: String
        get() = runCatching { prefs.getString(KEY_SETTINGS_CLOSED, null) }.getOrNull().orEmpty()
        set(v) = prefs.edit { putString(KEY_SETTINGS_CLOSED, v) }

    /**
     * 前回最後にいた位置（緯度, 経度。LIVE の Fix。20261024-D04）。次に起動したとき、位置が来るまでの地図の中心にする。
     * 覚えていなければ null。NavSettings の外なので、「設定を初期値に戻す」では変わらない
     */
    var lastPosition: Pair<Double, Double>?
        get() {
            val lat = prefs.getString(KEY_LAST_LAT, null)?.toDoubleOrNull() ?: return null
            val lon = prefs.getString(KEY_LAST_LON, null)?.toDoubleOrNull() ?: return null
            return if (lat in -90.0..90.0 && lon in -180.0..180.0) lat to lon else null
        }
        set(v) = prefs.edit {
            if (v == null) { remove(KEY_LAST_LAT); remove(KEY_LAST_LON) } else { putString(KEY_LAST_LAT, v.first.toString()); putString(KEY_LAST_LON, v.second.toString()) }
        }

    internal companion object {
        /** 設定のファイル名。表示言語の選択（AppLanguage。Android 12 以前）もここに置く */
        const val PREFS = "settings"
        private const val KEY_INPUT = "input"
        private const val KEY_SETTINGS_SCROLL = "settingsScrollY"
        private const val KEY_SETTINGS_CLOSED = "settingsClosedSections"
        private const val KEY_LAST_LAT = "lastLat"
        private const val KEY_LAST_LON = "lastLon"
    }
}
