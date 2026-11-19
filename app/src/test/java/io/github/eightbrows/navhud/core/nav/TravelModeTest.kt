package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Test

/** 移動手段: 自動車（推奨値で固定）/ カスタム1〜3（枠ごとに保存）。§5.4・§6.9 */
class TravelModeTest {

    private val car = ReachProfile.CAR

    @Test
    fun carIsFixedAndCustomSlotsKeepTheirValues() {
        val s0 = NavSettings()
        assertEquals(TravelMode.CAR, s0.travelMode)
        assertEquals(car, s0.reachProfile)
        // 自動車では値を変えられない
        assertEquals(s0, s0.editReach { it.copy(reachRadiusM = 100.0) })
        // カスタム1 で到着半径 50m、カスタム2 で真横通過 OFF
        val s1 = s0.selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(reachRadiusM = 50.0) }
        assertEquals(50.0, s1.reachRadiusM, 0.0)
        val s2 = s1.selectTravelMode(TravelMode.CUSTOM2).editReach { it.copy(sidePass = false) }
        // カスタム2 は自動車の値で始まり、カスタム1 の変更は持ち込まない
        assertEquals(30.0, s2.reachRadiusM, 0.0)
        assertEquals(false, s2.sidePass)
        // 自動車に切り替えても、カスタムの値は消えない
        val s3 = s2.selectTravelMode(TravelMode.CAR)
        assertEquals(car, s3.reachProfile)
        assertEquals(50.0, s3.selectTravelMode(TravelMode.CUSTOM1).reachRadiusM, 0.0)
        assertEquals(false, s3.selectTravelMode(TravelMode.CUSTOM2).sidePass)
        assertEquals(car, s3.selectTravelMode(TravelMode.CUSTOM3).reachProfile)
        // 「自動車の値に戻す」: その枠だけ推奨値に
        val s4 = s3.selectTravelMode(TravelMode.CUSTOM1).editReach { car }
        assertEquals(car, s4.reachProfile)
        assertEquals(false, s4.selectTravelMode(TravelMode.CUSTOM2).sidePass)
    }

    @Test
    fun slotsAreSavedAndLoaded() {
        val s = NavSettings()
            .selectTravelMode(TravelMode.CUSTOM3).editReach { it.copy(passHoldSec = 9, sidePassMaxM = 200.0) }
            .selectTravelMode(TravelMode.CUSTOM2).editReach { it.copy(reachRadiusM = 100.0) }
        val r = SettingsCodec.decode(SettingsCodec.encode(s))
        assertEquals(s, r)
        assertEquals(TravelMode.CUSTOM2, r.travelMode)
        assertEquals(100.0, r.reachRadiusM, 0.0)
        assertEquals(9, r.selectTravelMode(TravelMode.CUSTOM3).passHoldSec)
        assertEquals(200.0, r.selectTravelMode(TravelMode.CUSTOM3).sidePassMaxM, 0.0)
    }

    @Test
    fun resetKeepsCustomSlots() {
        // カスタム2 を選んで値を変え、ほかの設定も変えてから「初期値に戻す」: 移動手段は自動車、カスタム1〜3 の値は残る
        val edited = NavSettings(uiTheme = ColorTheme.AMBER, noFixTimeoutSec = 30)
            .selectTravelMode(TravelMode.CUSTOM2)
            .editReach { it.copy(reachRadiusM = 100.0, passHoldSec = 9) }
        val r = edited.resetKeepingCustomReach()
        assertEquals(TravelMode.CAR, r.travelMode)
        assertEquals(car, r.reachProfile)
        assertEquals(edited.customReach, r.customReach)
        assertEquals(NavSettings(), r.copy(customReach = NavSettings().customReach))
        // カスタム2 に切り替えれば、変えた値が戻る
        assertEquals(100.0, r.selectTravelMode(TravelMode.CUSTOM2).reachRadiusM, 0.0)
        assertEquals(9, r.selectTravelMode(TravelMode.CUSTOM2).passHoldSec)
    }

    @Test
    fun migrationFromThePreviousVersion() {
        // D06・D07 の「カスタム」: 保存してあった値をカスタム1 に移し、カスタム1 を選ぶ。カスタム2・3 は自動車の値
        val old = mapOf(
            "travelMode" to "CUSTOM", "reachRadiusM" to "100.0", "sidePass" to "false", "sidePassMaxM" to "200.0",
            "sidePassDepartM" to "20.0", "passDetection" to "true", "passMaxApproachM" to "300.0", "passDepartM" to "50.0", "passHoldSec" to "5",
        )
        val m = SettingsCodec.decode(old)
        assertEquals(TravelMode.CUSTOM1, m.travelMode)
        val p1 = ReachProfile(100.0, false, 200.0, 20.0, true, 300.0, 50.0, 5)
        assertEquals(p1, m.reachProfile)
        assertEquals(listOf(p1, car, car), m.customReach)
        // D06・D07 の「自動車」はそのまま（保存してあった値に関わらず推奨値）
        val c = SettingsCodec.decode(old + ("travelMode" to "CAR"))
        assertEquals(TravelMode.CAR, c.travelMode)
        assertEquals(car, c.reachProfile)
        assertEquals(listOf(car, car, car), c.customReach)
        // D06 より前（移動手段の項目がない）: 到達半径 100m などをカスタム1 に
        val pre = SettingsCodec.decode(mapOf("reachRadiusM" to "100.0", "uiTheme" to "GREEN"))
        assertEquals(TravelMode.CUSTOM1, pre.travelMode)
        assertEquals(100.0, pre.reachRadiusM, 0.0)
        assertEquals(100.0, pre.customReach[0].reachRadiusM, 0.0)
        // 保存なし（新しく入れた端末）は自動車
        assertEquals(NavSettings(), SettingsCodec.decode(emptyMap()))
    }

    /** 20261119-D01: 真横通過の判定を始める速さは到達判定の値。自動車は 11 km/h で決まった値、カスタムの初期値も 11 km/h */
    @Test
    fun sidePassMinSpeedIsAReachValue() {
        assertEquals(11, ReachProfile.CAR.sidePassMinKmh)
        val s0 = NavSettings()
        assertEquals(11, s0.sidePassMinKmh)
        assertEquals(listOf(11, 11, 11), s0.customReach.map { it.sidePassMinKmh })
        // 自動車では変えられない
        assertEquals(s0, s0.editReach { it.copy(sidePassMinKmh = 20) })
        // カスタムでは変えられ、枠に残る。自動車に戻すと 11 km/h
        val c1 = s0.selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(sidePassMinKmh = 20) }
        assertEquals(20, c1.sidePassMinKmh)
        assertEquals(11, c1.selectTravelMode(TravelMode.CAR).sidePassMinKmh)
        assertEquals(20, c1.selectTravelMode(TravelMode.CAR).selectTravelMode(TravelMode.CUSTOM1).sidePassMinKmh)
        // 保存して読み直しても同じ
        val back = SettingsCodec.decode(SettingsCodec.encode(c1))
        assertEquals(c1, back)
        // 前の版の保存（この項目がない）: 自動車もカスタムも 11 km/h。範囲（1〜36）の外は 11 km/h
        val old = SettingsCodec.encode(c1).filterKeys { !it.endsWith("sidePassMinKmh") }
        val r = SettingsCodec.decode(old)
        assertEquals(11, r.sidePassMinKmh)
        assertEquals(listOf(11, 11, 11), r.customReach.map { it.sidePassMinKmh })
        assertEquals(11, SettingsCodec.decode(SettingsCodec.encode(c1) + ("custom1.sidePassMinKmh" to "0")).sidePassMinKmh)
        assertEquals(36, SettingsCodec.decode(SettingsCodec.encode(c1) + ("custom1.sidePassMinKmh" to "36")).sidePassMinKmh)
        // 方位を止める / 戻す速度を変えても、真横通過の速さは変わらない
        val h = c1.copy(holdEnterSpeedMps = NavSettings.kmhToMps(2), holdExitSpeedMps = NavSettings.kmhToMps(30))
        assertEquals(20, h.sidePassMinKmh)
    }
}
