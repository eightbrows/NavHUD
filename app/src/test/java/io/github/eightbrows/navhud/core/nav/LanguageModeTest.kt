package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

/** 表示言語の選択（§6.9・§6.11）: 保存と読み戻し、端末の言語タグからの読み取り。 */
class LanguageModeTest {

    @Test
    fun savedValueRoundTrips() {
        // 3 つの選択とも、保存した文字から同じ選択に戻る
        for (m in LanguageMode.entries) assertEquals(m, LanguageMode.decode(m.encode()))
    }

    @Test
    fun defaultIsSystemWhenNothingOrUnknownIsSaved() {
        // 保存がない・空・読めない値は「端末に合わせる」（初期値）
        assertEquals(LanguageMode.SYSTEM, LanguageMode.decode(null))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.decode(""))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.decode("FRENCH"))
        // 名前は大文字・小文字まで同じものだけを読む（言語タグ "ja" は保存の値ではない）
        assertEquals(LanguageMode.SYSTEM, LanguageMode.decode("japanese"))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.decode("ja"))
    }

    @Test
    fun tagsGivenToTheDevice() {
        // 端末に渡す言語タグ: 端末に合わせるは空、日本語は ja、English は en
        assertEquals("", LanguageMode.SYSTEM.tags)
        assertEquals("ja", LanguageMode.JAPANESE.tags)
        assertEquals("en", LanguageMode.ENGLISH.tags)
        // 渡したタグを読み戻すと、同じ選択になる
        for (m in LanguageMode.entries) assertEquals(m, LanguageMode.fromTags(m.tags))
    }

    @Test
    fun readsTheFirstLanguageOfDeviceTags() {
        // 端末が返す形（地域つき・複数）でも、先頭の言語で決まる
        assertEquals(LanguageMode.JAPANESE, LanguageMode.fromTags("ja-JP"))
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromTags("en-US"))
        assertEquals(LanguageMode.ENGLISH, LanguageMode.fromTags("en-GB,ja-JP"))
        assertEquals(LanguageMode.JAPANESE, LanguageMode.fromTags("ja_JP"))
        assertEquals(LanguageMode.JAPANESE, LanguageMode.fromTags("JA"))
        // 空（端末に合わせる）と、選択肢にない言語は「端末に合わせる」
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTags(null))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTags(""))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTags("fr-FR"))
        assertEquals(LanguageMode.SYSTEM, LanguageMode.fromTags("fr-FR,ja-JP"))
    }

    @Test
    fun languageIsKeptOutsideNavSettings() {
        // 言語は NavSettings の項目と同じ場所に、別のキーで保存する: 設定の保存（SettingsCodec）はこのキーを書かないので、
        // 設定を保存しても・初期値に戻しても、言語の保存は変わらない
        val saved = SettingsCodec.encode(NavSettings()) + SettingsCodec.encode(NavSettings().resetKeepingCustomReach())
        assertFalse(LanguageMode.PREF_KEY in saved.keys)
        // 言語の保存がいっしょに入っていても、設定はそのまま読める（知らないキーは無視する）
        val withLanguage = SettingsCodec.encode(NavSettings()) + (LanguageMode.PREF_KEY to LanguageMode.ENGLISH.encode())
        assertEquals(NavSettings(), SettingsCodec.decode(withLanguage))
    }
}
