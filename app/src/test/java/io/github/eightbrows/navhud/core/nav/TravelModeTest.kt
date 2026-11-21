package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertTrue
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

    /** 20261119-D03: 方位を止める / 戻す速度は到達判定の値。自動車は 4 / 6 km/h で決まった値、カスタムの初期値も 4 / 6 km/h */
    @Test
    fun holdSpeedsAreReachValues() {
        fun kmh(s: NavSettings) = NavSettings.holdSpeedKmh(s.holdEnterSpeedMps) to NavSettings.holdSpeedKmh(s.holdExitSpeedMps)
        val s0 = NavSettings()
        assertEquals(4 to 6, kmh(s0))
        assertEquals(listOf(4 to 6, 4 to 6, 4 to 6), s0.customReach.map { it.holdEnterKmh to it.holdExitKmh })
        // 自動車では変えられない
        assertEquals(s0, s0.editReach { it.copy(holdEnterKmh = 9, holdExitKmh = 20) })
        // カスタムでは変えられ、枠に残る。プリセットを切り替えると、すぐにその値になる
        val c1 = s0.selectTravelMode(TravelMode.CUSTOM1).editReach { it.copy(holdEnterKmh = 9, holdExitKmh = 20) }
        assertEquals(9 to 20, kmh(c1))
        assertEquals(4 to 6, kmh(c1.selectTravelMode(TravelMode.CAR)))
        assertEquals(4 to 6, kmh(c1.selectTravelMode(TravelMode.CUSTOM2)))
        assertEquals(9 to 20, kmh(c1.selectTravelMode(TravelMode.CAR).selectTravelMode(TravelMode.CUSTOM1)))
        // 今の値（reachProfile）にも入る
        assertEquals(9 to 20, c1.reachProfile.holdEnterKmh to c1.reachProfile.holdExitKmh)
        // 「設定を初期値に戻す」: 自動車の 4 / 6 km/h に戻り、カスタムの枠の値は残る
        val r = c1.resetKeepingCustomReach()
        assertEquals(4 to 6, kmh(r))
        assertEquals(9 to 20, kmh(r.selectTravelMode(TravelMode.CUSTOM1)))
        // 保存して読み直しても同じ
        assertEquals(c1, SettingsCodec.decode(SettingsCodec.encode(c1)))
        // NavEngine に渡ると、その値で保持する（HeadingSelector は NavSettings の m/s の値を使う）
        assertEquals(NavSettings.kmhToMps(9), c1.holdEnterSpeedMps, 1e-6f)
        assertEquals(NavSettings.kmhToMps(20), c1.holdExitSpeedMps, 1e-6f)
    }

    /** 20261121-D01: 移動のプリセットは 自動車 / 歩行 / カスタム1〜3 の並び（画面のボタンの並び） */
    @Test
    fun presetOrder() {
        assertEquals(listOf(TravelMode.CAR, TravelMode.WALK, TravelMode.CUSTOM1, TravelMode.CUSTOM2, TravelMode.CUSTOM3), TravelMode.entries)
    }

    /** 20261121-D01: 歩行のプリセット。値は決まっていて変えられない（自動車と同じ形） */
    @Test
    fun walkPresetIsFixed() {
        val w = ReachProfile.WALK
        assertEquals(15.0, w.reachRadiusM, 0.0)
        assertEquals(true, w.sidePass)
        assertEquals(2, w.sidePassMinKmh)
        assertEquals(50.0, w.sidePassMaxM, 0.0)
        assertEquals(5.0, w.sidePassDepartM, 0.0)
        assertEquals(true, w.passDetection)
        assertEquals(50.0, w.passMaxApproachM, 0.0)
        assertEquals(10.0, w.passDepartM, 0.0)
        assertEquals(3, w.passHoldSec)
        assertEquals(1 to 3, w.holdEnterKmh to w.holdExitKmh)
        assertEquals(HeadingMethod.GPS_TRACK, w.headingMethod)
        assertEquals(30f, w.maxBearingAccDeg)
        assertEquals(5, w.gsAverageSec)
        assertEquals(60, w.etaAverageSec)
        // 選ぶと今の値が全部歩行の値になる。変えられない
        val s = NavSettings().selectTravelMode(TravelMode.WALK)
        assertEquals(w, s.reachProfile)
        assertEquals(HeadingMethod.GPS_TRACK, s.headingMethod)
        assertEquals(30f, s.maxGpsBearingAccDeg)
        assertEquals(5, s.gsAverageSec)
        assertEquals(60, s.etaAverageSec)
        assertEquals(1 / 3.6f, s.holdEnterSpeedMps, 1e-6f)
        assertEquals(s, s.editReach { it.copy(reachRadiusM = 100.0, maxBearingAccDeg = 45f) })
        // 歩行の値は選べる値・範囲の中（設定画面・保存と同じ決まり）
        assertTrue(w.reachRadiusM in NavSettings.REACH_RADIUS_CHOICES_M)
        assertTrue(w.gsAverageSec in NavSettings.GS_AVERAGE_CHOICES_SEC)
        assertTrue(w.etaAverageSec in NavSettings.ETA_AVERAGE_CHOICES_SEC)
        assertTrue(w.maxBearingAccDeg in NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)
        assertTrue(w.sidePassMaxM in NavSettings.SIDE_PASS_MAX_M_RANGE)
        assertTrue(w.sidePassDepartM in NavSettings.SIDE_PASS_DEPART_M_RANGE)
        assertTrue(w.passMaxApproachM in NavSettings.PASS_MAX_APPROACH_M_RANGE)
        assertTrue(w.passDepartM in NavSettings.PASS_DEPART_M_RANGE)
        assertEquals(NavSettings.holdSpeedsKmh(1, 3), w.holdEnterKmh to w.holdExitKmh)
        // 保存して読み直しても歩行
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        // 自動車に戻すと自動車の値（方位の出し方 GPS・精度 20°・GS 1 秒・ETA 10 秒）
        val c = s.selectTravelMode(TravelMode.CAR)
        assertEquals(car, c.reachProfile)
        assertEquals(NavSettings(), c)
    }

    /** 20261121-D01: 方位の出し方・方位精度の上限・GS の平均・ETA の平均速度もプリセットの値。カスタムでは変えられる */
    @Test
    fun headingAndAveragingAreReachValues() {
        assertEquals(HeadingMethod.GPS, car.headingMethod)
        assertEquals(20f, car.maxBearingAccDeg)
        assertEquals(1, car.gsAverageSec)
        assertEquals(10, car.etaAverageSec)
        // カスタムの初期値は自動車と同じ
        assertEquals(List(3) { car }, NavSettings().customReach)
        val c2 = NavSettings().selectTravelMode(TravelMode.CUSTOM2)
            .editReach { it.copy(headingMethod = HeadingMethod.GPS_TRACK, maxBearingAccDeg = 45f, gsAverageSec = 3, etaAverageSec = 120) }
        assertEquals(HeadingMethod.GPS_TRACK, c2.headingMethod)
        assertEquals(45f, c2.maxGpsBearingAccDeg)
        assertEquals(3, c2.gsAverageSec)
        assertEquals(120, c2.etaAverageSec)
        // 切り替えるとすぐその値。戻すと枠の値
        assertEquals(20f, c2.selectTravelMode(TravelMode.CAR).maxGpsBearingAccDeg)
        assertEquals(30f, c2.selectTravelMode(TravelMode.WALK).maxGpsBearingAccDeg)
        assertEquals(45f, c2.selectTravelMode(TravelMode.WALK).selectTravelMode(TravelMode.CUSTOM2).maxGpsBearingAccDeg)
        assertEquals(c2, SettingsCodec.decode(SettingsCodec.encode(c2)))
    }

    /** 20261121-D01: カスタムの「歩行の値にする」・「自動車の値にする」（どちらも枠の値を全部入れ替える） */
    @Test
    fun customCanTakeWalkOrCarValues() {
        val c1 = NavSettings().selectTravelMode(TravelMode.CUSTOM1).editReach { ReachProfile.WALK }
        assertEquals(ReachProfile.WALK, c1.customReach[0])
        assertEquals(ReachProfile.WALK, c1.reachProfile)
        assertEquals(TravelMode.CUSTOM1, c1.travelMode)
        // そのあと変えられる（歩行のプリセットそのものは変わらない）
        val c1b = c1.editReach { it.copy(reachRadiusM = 20.0) }
        assertEquals(20.0, c1b.reachRadiusM, 0.0)
        assertEquals(15.0, c1b.selectTravelMode(TravelMode.WALK).reachRadiusM, 0.0)
        assertEquals(car, c1b.editReach { ReachProfile.CAR }.reachProfile)
    }
}
