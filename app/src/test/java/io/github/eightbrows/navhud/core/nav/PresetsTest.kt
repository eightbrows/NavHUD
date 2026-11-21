package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 移動のプリセットの決まり（20261121-D03）: どのプリセットも変えられる・変えた所だけを保存する・比べる初期値・初期値に戻す・コピーする・
 * 前の保存の読み替え・初期値が変わったときの付いていき方
 */
class PresetsTest {

    private val car = ReachProfile.CAR
    private val walk = ReachProfile.WALK

    @Test
    fun onlyChangedValuesAreSaved() {
        // 初期値のままなら、保存の形・移動のプリセット・カスタムの元だけ
        assertEquals(
            mapOf("settingsFormat" to "2", "travelMode" to "CAR", "custom1.base" to "CAR", "custom2.base" to "CAR", "custom3.base" to "CAR"),
            SettingsCodec.encode(NavSettings()),
        )
        // 自動車の到着半径・地図の表 1 行・共通の値（色）を変える → その 3 つだけ
        val s = NavSettings(uiTheme = ColorTheme.GREEN)
            .editReach { it.copy(reachRadiusM = 50.0, mapItems = it.mapItems + (MapItem.RAILWAY to MapRange.UPTO_10KM)) }
        val enc = SettingsCodec.encode(s) - setOf("settingsFormat", "travelMode", "custom1.base", "custom2.base", "custom3.base")
        assertEquals(mapOf("uiTheme" to "GREEN", "car.reachRadiusM" to "50.0", "car.mapItems" to "RAILWAY:UPTO_10KM"), enc)
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        assertEquals(2, s.changedCount(TravelMode.CAR))
        // 初期値と同じ値に戻すと、変えた所から外れる（保存しない）
        val back = s.editReach { it.copy(reachRadiusM = car.reachRadiusM) }
        assertEquals(1, back.changedCount(TravelMode.CAR))
        assertNull(SettingsCodec.encode(back)["car.reachRadiusM"])
    }

    @Test
    fun comparedDefaults() {
        // 自動車は自動車の初期値、歩行は歩行の初期値と比べる
        val s = NavSettings().selectTravelMode(TravelMode.WALK).editReach { it.copy(reachRadiusM = 30.0) }
        // 歩行の 30 m は歩行の初期値（15 m）と違う → 変えた所（自動車の初期値 30 m とは同じでも）
        assertEquals(1, s.changedCount(TravelMode.WALK))
        assertEquals("30.0", SettingsCodec.encode(s)["walk.reachRadiusM"])
        assertEquals(0, s.changedCount(TravelMode.CAR))
        // カスタムは元の初期値と比べる: 元が歩行なら、歩行の値は変えた所ではない
        val c = NavSettings().resetCustom(TravelMode.CUSTOM2, TravelMode.WALK)
        assertEquals(TravelMode.WALK, c.baseOf(TravelMode.CUSTOM2))
        assertEquals(walk, c.profileOf(TravelMode.CUSTOM2))
        assertEquals(0, c.changedCount(TravelMode.CUSTOM2))
        assertEquals("WALK", SettingsCodec.encode(c)["custom2.base"])
        assertTrue(SettingsCodec.encode(c).keys.none { it.startsWith("custom2.") && it != "custom2.base" })
        // 設定画面の初期値: プリセットの値は今のプリセットの元の初期値、共通の値はアプリの初期値
        val d = c.selectTravelMode(TravelMode.CUSTOM2).copy(uiTheme = ColorTheme.AMBER).defaultsForDisplay()
        assertEquals(15.0, d.reachRadiusM, 0.0)
        assertEquals(NavSettings().uiTheme, d.uiTheme)
        // 共通の値はアプリの初期値と比べる（プリセットの変えた所には数えない）
        assertEquals(0, NavSettings(uiTheme = ColorTheme.AMBER).changedCount(TravelMode.CAR))
    }

    @Test
    fun countsAreByItemAndTableRow() {
        val s = NavSettings().editReach {
            it.copy(
                holdEnterKmh = 2, holdExitKmh = 8, trackDistanceM = 12, profileSize = ProfileSize.SMALL,
                mapItems = it.mapItems + (MapItem.RAILWAY to MapRange.UPTO_10KM) + (MapItem.PREF_ROAD to MapRange.UPTO_5KM),
                landmarks = it.landmarks + (LandmarkItem.PEAK_LOW to MapRange.OFF),
            )
        }
        // 止める・戻す 2、距離 1、標高プロファイル 1、地図の表 2 行、ランドマークの表 1 行
        assertEquals(7, s.changedCount(TravelMode.CAR))
        assertEquals(
            setOf("holdEnterKmh", "holdExitKmh", "trackDistanceM", "profileSize", "mapItems.RAILWAY", "mapItems.PREF_ROAD", "landmarks.PEAK_LOW"),
            s.carProfile.changesFrom(car).toSet(),
        )
    }

    @Test
    fun resetPresetAndResetCustom() {
        val s = NavSettings(uiTheme = ColorTheme.GREEN)
            .editReach { it.copy(reachRadiusM = 50.0, passHoldSec = 9, gsAverageSec = 3) }
            .selectTravelMode(TravelMode.WALK).editReach { it.copy(reachRadiusM = 20.0) }
            .selectTravelMode(TravelMode.CAR)
        assertEquals(3, s.changedCount(TravelMode.CAR))
        // 自動車の「初期値に戻す」: 自動車だけ。共通の値・歩行は変わらない。選んでいれば今の値も戻る
        val r = s.resetPreset(TravelMode.CAR)
        assertEquals(car, r.carProfile)
        assertEquals(car, r.reachProfile)
        assertEquals(ColorTheme.GREEN, r.uiTheme)
        assertEquals(20.0, r.walkProfile.reachRadiusM, 0.0)
        assertEquals(0, r.changedCount(TravelMode.CAR))
        // カスタムの「初期値に戻す…」: 自動車の初期値・歩行の初期値（元も替わる）
        val c = s.selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(passHoldSec = 20) }
        val cw = c.resetCustom(TravelMode.CUSTOM1, TravelMode.WALK)
        assertEquals(walk, cw.reachProfile)
        assertEquals(TravelMode.WALK, cw.baseOf(TravelMode.CUSTOM1))
        assertFalse(cw.customCopied[0])
        val cc = cw.resetCustom(TravelMode.CUSTOM1, TravelMode.CAR)
        assertEquals(car, cc.reachProfile)
        assertEquals(TravelMode.CAR, cc.baseOf(TravelMode.CUSTOM1))
        // 変えた所がなくても使える（元を替えるだけ）
        assertEquals(walk, NavSettings().resetCustom(TravelMode.CUSTOM3, TravelMode.WALK).profileOf(TravelMode.CUSTOM3))
        // 「設定を初期値に戻す」は全部
        assertEquals(NavSettings(), cw.resetAll())
    }

    @Test
    fun copyToCustom() {
        val s = NavSettings()
            .selectTravelMode(TravelMode.WALK).editReach { it.copy(reachRadiusM = 20.0, trackDistanceM = 10) }
            .selectTravelMode(TravelMode.CUSTOM2)
        // 「今の歩行」を入れる: 変えた所も含めて。元は歩行、コピーした印
        val c = s.copyToCustom(TravelMode.CUSTOM2, TravelMode.WALK)
        assertEquals(s.walkProfile, c.reachProfile)
        assertEquals(TravelMode.WALK, c.baseOf(TravelMode.CUSTOM2))
        assertTrue(c.customCopied[1])
        // 比べるのは歩行の初期値なので、変えた所の数は今の歩行と同じ
        assertEquals(2, c.changedCount(TravelMode.CUSTOM2))
        assertEquals(c, SettingsCodec.decode(SettingsCodec.encode(c)))
        assertEquals("true", SettingsCodec.encode(c)["custom2.copied"])
        // 「今の自動車」（初期値のまま）を入れる
        val cc = c.copyToCustom(TravelMode.CUSTOM2, TravelMode.CAR)
        assertEquals(car, cc.reachProfile)
        assertEquals(0, cc.changedCount(TravelMode.CUSTOM2))
        // 選んでいないカスタムに入れても、今の値は変わらない
        val other = s.copyToCustom(TravelMode.CUSTOM3, TravelMode.WALK)
        assertEquals(s.reachProfile, other.reachProfile)
        assertEquals(s.walkProfile, other.profileOf(TravelMode.CUSTOM3))
    }

    /** 初期値を差し替えて、変えていない項目が新しい初期値になり、変えた項目は残ることを確かめる */
    @Test
    fun unchangedValuesFollowNewDefaults() {
        val old = SettingsDefaults.CURRENT
        val s = NavSettings(noFixTimeoutSec = 30)
            .editReach { it.copy(reachRadiusM = 50.0) }
            .selectTravelMode(TravelMode.WALK).editReach { it.copy(sidePassMinKmh = 3) }
            .selectTravelMode(TravelMode.CAR)
        val saved = SettingsCodec.encode(s, old)
        // 新しい初期値: 自動車の止める / 戻す速度 5 / 8、真横通過の距離 120 m、歩行の到着半径 10 m、共通の位置精度の上限 10 m
        val newCar = car.copy(holdEnterKmh = 5, holdExitKmh = 8, sidePassMaxM = 120.0)
        val newWalk = walk.copy(reachRadiusM = 10.0)
        val new = SettingsDefaults(old.common.copy(maxGpsAccM = 10f), newCar, newWalk)
        val r = SettingsCodec.decode(saved, new)
        // 変えていない項目は新しい初期値
        assertEquals(5 to 8, r.carProfile.holdEnterKmh to r.carProfile.holdExitKmh)
        assertEquals(120.0, r.carProfile.sidePassMaxM, 0.0)
        assertEquals(10.0, r.walkProfile.reachRadiusM, 0.0)
        assertEquals(10f, r.maxGpsAccM)
        // 変えた項目は残る
        assertEquals(50.0, r.carProfile.reachRadiusM, 0.0)
        assertEquals(3, r.walkProfile.sidePassMinKmh)
        assertEquals(30, r.noFixTimeoutSec)
        // カスタム（元：自動車の初期値）も新しい自動車の初期値に付いていく
        assertEquals(newCar, r.profileOf(TravelMode.CUSTOM1))
        // 今の値は自動車（新しい初期値 + 変えた所）
        assertEquals(newCar.copy(reachRadiusM = 50.0), r.reachProfile)
        // 変えた所の数は新しい初期値と比べて同じ
        assertEquals(1, r.changedCount(TravelMode.CAR, new))
        assertEquals(1, r.changedCount(TravelMode.WALK, new))
    }

    /** 前の形（20261121-D02 まで）の保存の読み替え: 前と同じに見える */
    @Test
    fun legacySaveIsConvertedWithoutChangingBehavior() {
        val legacy = mapOf(
            // 前の版の全体の値: 縮尺・地図の表・ランドマークの表・標高プロファイル
            "rangeStepsKm" to "0.1,0.2,0.5,1.0,2.0", "initialRangeKm" to "1.0", "autoRange" to "false", "autoMinRangeKm" to "0.2", "autoMaxRangeKm" to "1.0",
            "autoRangeZoomInDelaySec" to "8", "autoHoldAfterWpSec" to "20", "autoZoomInR1Ratio" to "1.5",
            "mapItems" to "RAILWAY:UPTO_10KM", "landmarkItems" to "PEAK_LOW:OFF", "profileSize" to "SMALL",
            // 共通の値（初期値と同じものと違うもの）
            "uiTheme" to "WHITE", "mapTheme" to "AMBER", "maxGpsAccM" to "15.0",
            // カスタム1（全部の値を保存していた。初期値と違うのは到着半径だけ）
            "travelMode" to "CUSTOM1", "custom1.reachRadiusM" to "50.0", "custom1.sidePass" to "true", "custom1.holdEnterKmh" to "4", "custom1.holdExitKmh" to "6",
            // 前の版の全体の今の値（読まない）
            "reachRadiusM" to "50.0",
        )
        val s = SettingsCodec.decode(legacy)
        // 全体の値は全部のプリセットに入る
        for (m in TravelMode.entries) {
            val p = s.profileOf(m)
            assertEquals(m.name, listOf(0.1, 0.2, 0.5, 1.0, 2.0), p.rangeStepsKm)
            assertEquals(1.0, p.initialRangeKm, 0.0)
            assertFalse(p.autoRange)
            assertEquals(0.2, p.autoMinRangeKm, 0.0)
            assertEquals(1.0, p.autoMaxRangeKm, 0.0)
            assertEquals(8, p.autoRangeZoomInDelaySec)
            assertEquals(20, p.autoHoldAfterWpSec)
            assertEquals(1.5, p.autoZoomInDistRatio, 0.0)
            assertEquals(MapRange.UPTO_10KM, p.mapItems[MapItem.RAILWAY])
            assertEquals(MapRange.OFF, p.landmarks[LandmarkItem.PEAK_LOW])
            assertEquals(ProfileSize.SMALL, p.profileSize)
        }
        // 自動車・歩行の到達の判定などは初期値のまま（前は変えられなかった）
        assertEquals(30.0, s.carProfile.reachRadiusM, 0.0)
        assertEquals(walk.reachRadiusM, s.walkProfile.reachRadiusM, 0.0)
        // カスタム1 は元：自動車の初期値。保存の値で違う所（到着半径）が変えた所
        assertEquals(TravelMode.CAR, s.baseOf(TravelMode.CUSTOM1))
        assertEquals(50.0, s.reachRadiusM, 0.0)
        assertEquals(TravelMode.CUSTOM1, s.travelMode)
        // 変えた所の数: 全体の値（11 項目のうち 縮尺 8・表 2 行・標高プロファイル）＋ カスタム1 は到着半径も
        assertEquals(11, s.changedCount(TravelMode.CAR))
        assertEquals(12, s.changedCount(TravelMode.CUSTOM1))
        // 書き直すと新しい形になり、読み直して同じ。共通の値は初期値と違う所だけ
        val enc = SettingsCodec.encode(s)
        assertEquals("2", enc["settingsFormat"])
        assertEquals("AMBER", enc["mapTheme"])
        assertNull(enc["uiTheme"])
        assertNull(enc["maxGpsAccM"])
        assertNull(enc["rangeStepsKm"])
        assertNull(enc["reachRadiusM"])
        assertEquals("50.0", enc["custom1.reachRadiusM"])
        assertNull(enc["custom1.sidePass"])
        assertEquals("RAILWAY:UPTO_10KM", enc["walk.mapItems"])
        assertEquals(s, SettingsCodec.decode(enc))
        // 保存のときに消す名前（前の形の全体の値・初期値に戻した値）は設定の名前。INPUT などは設定の名前ではない
        assertTrue(SettingsCodec.isSettingsKey("rangeStepsKm"))
        assertTrue(SettingsCodec.isSettingsKey("car.reachRadiusM"))
        assertTrue(SettingsCodec.isSettingsKey("uiTheme"))
        assertFalse(SettingsCodec.isSettingsKey("input"))
        assertFalse(SettingsCodec.isSettingsKey("settingsScrollY"))
        assertFalse(SettingsCodec.isSettingsKey("settingsClosedSections"))
        assertFalse(SettingsCodec.isSettingsKey("lastLat"))
        assertFalse(SettingsCodec.isSettingsKey(LanguageMode.PREF_KEY))
    }

    /** 新しい形では、移動のプリセットが自動車でも前の版の設定と見なさない（前の形の決まり「移動手段の項目がない → カスタム1」に当たらない） */
    @Test
    fun newFormatIsNotMistakenForAnOldVersion() {
        val s = NavSettings(uiTheme = ColorTheme.GREEN)
        val r = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(TravelMode.CAR, r.travelMode)
        assertEquals(s, r)
    }

    /** 位置から求める向きの距離（20261121-D03）: 3〜20 m、1 m 刻み。初期値は自動車・歩行とも 8 m */
    @Test
    fun trackDistanceSetting() {
        assertEquals(3..20, NavSettings.TRACK_DISTANCE_M_RANGE)
        assertEquals(8, car.trackDistanceM)
        assertEquals(8, walk.trackDistanceM)
        assertEquals(8, NavSettings().trackDistanceM)
        val s = NavSettings().selectTravelMode(TravelMode.WALK).editReach { it.copy(trackDistanceM = 12) }
        assertEquals(12, s.trackDistanceM)
        assertEquals("12", SettingsCodec.encode(s)["walk.trackDistanceM"])
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        // 範囲の外は初期値
        assertEquals(8, SettingsCodec.decode(SettingsCodec.encode(s) + ("walk.trackDistanceM" to "25")).trackDistanceM)
        assertEquals(8, SettingsCodec.decode(SettingsCodec.encode(s) + ("walk.trackDistanceM" to "2")).trackDistanceM)
    }

    /** 今の値（縮尺・表など）を変えると、選んでいるプリセットに残る（storeActive。NavViewModel.updateSettings が呼ぶ） */
    @Test
    fun currentValuesAreStoredInTheActivePreset() {
        val s = NavSettings().selectTravelMode(TravelMode.WALK).copy(rangeStepsKm = listOf(0.1, 0.5), profileSize = ProfileSize.LARGE).storeActive()
        assertEquals(listOf(0.1, 0.5), s.walkProfile.rangeStepsKm)
        assertEquals(ProfileSize.LARGE, s.walkProfile.profileSize)
        assertEquals(car, s.carProfile)
        // 自動車に切り替えると自動車の縮尺、歩行に戻すと歩行の縮尺
        assertEquals(car.rangeStepsKm, s.selectTravelMode(TravelMode.CAR).rangeStepsKm)
        assertEquals(listOf(0.1, 0.5), s.selectTravelMode(TravelMode.CAR).selectTravelMode(TravelMode.WALK).rangeStepsKm)
    }
}
