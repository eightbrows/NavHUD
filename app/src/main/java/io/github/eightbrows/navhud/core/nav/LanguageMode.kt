package io.github.eightbrows.navhud.core.nav

import java.util.Locale

/**
 * アプリの表示言語の選択（§6.9・§6.11）。既定は SYSTEM（端末に合わせる: 日本語の端末は日本語、それ以外は英語）。
 * NavSettings には入れない（「設定を初期値に戻す」では変えない）。保存と切り替えは source/AppLanguage。
 *
 * @param tag 言語タグ（SYSTEM は null）
 */
enum class LanguageMode(val tag: String?) {
    SYSTEM(null),
    JAPANESE("ja"),
    ENGLISH("en");

    /** 保存する文字（名前）。 */
    fun encode(): String = name

    /** 端末に渡す言語タグの並び（SYSTEM は空 = 端末に合わせる）。 */
    val tags: String get() = tag ?: ""

    companion object {
        /** 保存先のキー。NavSettings の項目（SettingsCodec）と同じ場所に置くので、重ならない名前にする */
        const val PREF_KEY = "language"

        /** 保存した文字から。ない値・読めない値は SYSTEM。 */
        fun decode(saved: String?): LanguageMode = entries.firstOrNull { it.name == saved } ?: SYSTEM

        /**
         * 今使われている言語タグの並び（"ja-JP,en-US" など）から。先頭の言語だけを見る。
         * 空（端末に合わせる）と、選択肢にない言語は SYSTEM。
         */
        fun fromTags(tags: String?): LanguageMode {
            val language = tags.orEmpty().substringBefore(',').trim().substringBefore('-').substringBefore('_').lowercase(Locale.ROOT)
            return entries.firstOrNull { it.tag == language } ?: SYSTEM
        }
    }
}
