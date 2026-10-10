package io.github.eightbrows.navhud.source

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.annotation.ChecksSdkIntAtLeast
import androidx.annotation.RequiresApi
import androidx.core.content.edit
import io.github.eightbrows.navhud.core.nav.LanguageMode

/**
 * アプリの表示言語（§6.9・§6.11）。外部ライブラリ（AppCompat）は使わない。
 *
 * - Android 13 以降: 端末（LocaleManager）に任せる。端末が保存し、画面・サービスを含むすべての Context に反映する。
 *   端末の設定の「アプリの言語」で変えても同じ値になる。変えると、端末が画面を作り直す。
 * - Android 12 以前: アプリが保存し（設定と同じ SharedPreferences）、画面は [wrap]、画面以外は [localized] で
 *   その言語の Context にする。変えたら画面を作り直す（recreate）。
 *
 * どちらも画面が作り直されるので、アプリを再起動しなくてもすぐに切り替わる。
 */
object AppLanguage {

    /** 端末に任せられるか（Android 13 以降）。 */
    @get:ChecksSdkIntAtLeast(api = Build.VERSION_CODES.TIRAMISU)
    private val bySystem: Boolean get() = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    /** 今の選択。 */
    fun current(context: Context): LanguageMode =
        if (bySystem) LanguageMode.fromTags(localeManager(context).applicationLocales.toLanguageTags())
        else stored(context)

    /** 選択を変える。画面は作り直される（Android 13 以降は端末が、12 以前はここで）。 */
    fun set(activity: Activity, mode: LanguageMode) {
        if (mode == current(activity)) return
        if (bySystem) {
            localeManager(activity).applicationLocales = LocaleList.forLanguageTags(mode.tags)
        } else {
            prefs(activity).edit { putString(LanguageMode.PREF_KEY, mode.encode()) }
            activity.recreate()
        }
    }

    /**
     * Android 12 以前で選んだ言語を、端末を 13 以降に上げたあとも引き継ぐ: アプリが保存していた選択を端末に渡して、
     * アプリの側の保存を消す（端末の側でもう選んであれば、そちらを使う）。起動時に1回呼ぶ。
     */
    fun migrate(context: Context) {
        if (!bySystem) return
        val saved = stored(context)
        if (!prefs(context).contains(LanguageMode.PREF_KEY)) return
        prefs(context).edit { remove(LanguageMode.PREF_KEY) }
        val lm = localeManager(context)
        if (saved != LanguageMode.SYSTEM && lm.applicationLocales.isEmpty) lm.applicationLocales = LocaleList.forLanguageTags(saved.tags)
    }

    /** 画面（Activity）の attachBaseContext で使う: 選んだ言語の Context。端末に任せるとき・端末に合わせるときは base のまま。 */
    fun wrap(base: Context): Context {
        if (bySystem) return base
        val tag = stored(base).tag ?: return base
        val config = Configuration(base.resources.configuration)
        config.setLocales(LocaleList.forLanguageTags(tag))
        return base.createConfigurationContext(config)
    }

    /** 画面以外（サービス・ViewModel・ファイルの読み書き）で文字を取るための Context。中身は [wrap] と同じ。 */
    fun localized(context: Context): Context = wrap(context.applicationContext)

    private fun stored(context: Context): LanguageMode = LanguageMode.decode(prefs(context).getString(LanguageMode.PREF_KEY, null))

    private fun prefs(context: Context) = context.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun localeManager(context: Context): LocaleManager = context.getSystemService(LocaleManager::class.java)
}
