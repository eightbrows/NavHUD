package io.github.eightbrows.navhud.core.nav

import io.github.eightbrows.navhud.core.model.SourceMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun roundTripOfEveryValue() {
        // 既定値とすべて違う値にする
        val p1 = ReachProfile(50.0, false, 100.0, 15.0, false, 250.0, 40.0, 4)
        val p2 = ReachProfile(200.0, false, 200.0, 20.0, false, 450.0, 80.0, 8)
        val p3 = ReachProfile(100.0, false, 300.0, 30.0, false, 500.0, 60.0, 10)
        val s = NavSettings(
            sourceMode = SourceMode.HYBRID,
            holdEnterSpeedMps = 1.5f,
            holdExitSpeedMps = 4.2f,
            maxGpsAccM = 20f,
            maxGpsBearingAccDeg = 30f,
            rateWindowSec = 30,
            noFixTimeoutSec = 15,
            altOffsetM = 35.5,
            displayMode = DisplayMode.NORTH_UP,
            rangeStepsKm = listOf(0.05, 2.0, 50.0),
            initialRangeKm = 2.0,
            autoRange = false,
            autoRangeZoomInDelaySec = 8,
            wpButtonsMax = 6,
            hudWpCount = 4,
            uiTheme = ColorTheme.AMBER,
            mapTheme = ColorTheme.WHITE,
            ownshipPosition = OwnshipPosition.STANDARD,
            profileSize = ProfileSize.LARGE,
            panReturnSec = 45,
            autoOpenLastList = false,
            keepScreenOn = false,
            buttonOpacityPct = 40,
            numbersOpacityPct = 60,
            trackColor = TrackColor.CYAN,
            trackBrightnessPct = 25,
            ringLabelScalePct = 175,
            autoMinRangeKm = 0.2,
            autoMaxRangeKm = 5.0,
            autoHoldAfterWpSec = 20,
            autoZoomInDistRatio = 3.0,
            // 移動手段: カスタム2 を選んでいて、3つの枠はどれも自動車の値と違う
            travelMode = TravelMode.CUSTOM2,
            customReach = listOf(p1, p2, p3),
        ).withReachValues(p2)
        val d = NavSettings()
        // 念のため、ほんとうに全項目が既定値と違うことを確かめる（項目を足したらここも足す）
        assertEquals(SettingsCodec.encode(d).keys, SettingsCodec.encode(s).keys)
        SettingsCodec.encode(d).forEach { (k, v) -> assert(SettingsCodec.encode(s)[k] != v) { "既定値と同じ: $k" } }

        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun autoLimitsDefaultsAndInvalidValues() {
        // 既定: 下限 100m の段（R1 50m）、上限 500m の段（R1 250m。D03 から。前は 1km の段）
        assertEquals(0.1, NavSettings().autoMinRangeKm, 0.0)
        assertEquals(0.5, NavSettings().autoMaxRangeKm, 0.0)
        // 段の一覧にない値・読めない値は既定値
        val s = SettingsCodec.decode(mapOf("autoMinRangeKm" to "0.3", "autoMaxRangeKm" to "x"))
        assertEquals(0.1, s.autoMinRangeKm, 0.0)
        assertEquals(0.5, s.autoMaxRangeKm, 0.0)
        assertEquals(0.05, SettingsCodec.decode(mapOf("autoMinRangeKm" to "0.05")).autoMinRangeKm, 0.0)
        // WP 通過後の待機（既定 10 秒、0〜60）と、狭め始める距離（既定 1.3、1.0〜3.0 の 0.1 刻み。今の段の R1 が基準）
        assertEquals(10, NavSettings().autoHoldAfterWpSec)
        assertEquals(1.3, NavSettings().autoZoomInDistRatio, 0.0)
        assertEquals(0, SettingsCodec.decode(mapOf("autoHoldAfterWpSec" to "0")).autoHoldAfterWpSec)
        assertEquals(10, SettingsCodec.decode(mapOf("autoHoldAfterWpSec" to "61")).autoHoldAfterWpSec)
        assertEquals(1.7, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "1.7")).autoZoomInDistRatio, 0.0)
        assertEquals(1.3, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "1.25")).autoZoomInDistRatio, 0.0)
        assertEquals(1.3, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "3.5")).autoZoomInDistRatio, 0.0)
        val choices = NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES
        assertEquals(21, choices.size)
        assertEquals((10..30).map { it / 10.0 }, choices)
        // 保存は新しいキー
        assertEquals("1.3", SettingsCodec.encode(NavSettings())["autoZoomInR1Ratio"])
        assertEquals(null, SettingsCodec.encode(NavSettings())["autoZoomInDistRatio"])
    }

    @Test
    fun oldZoomInRatioIsNotUsed() {
        // 前の版の倍率（1段狭い段の R1 が基準）は意味が違うので読まず、初期値（1.3）にする。ほかの設定はそのまま読む
        val s = SettingsCodec.decode(mapOf("travelMode" to "CAR", "autoZoomInDistRatio" to "2.5", "autoHoldAfterWpSec" to "20"))
        assertEquals(1.3, s.autoZoomInDistRatio, 0.0)
        assertEquals(20, s.autoHoldAfterWpSec)
        // 前の版の倍率だけが保存されていても、前の版の設定として扱う（移動手段の移行と同じ）
        assertEquals(TravelMode.CUSTOM1, SettingsCodec.decode(mapOf("autoZoomInDistRatio" to "2.0")).travelMode)
    }

    @Test
    fun opacityDefaultsAndInvalidValues() {
        // 既定: ボタン 70%、数値 100%（Tuning.kt）
        val d = NavSettings()
        assertEquals(70, d.buttonOpacityPct)
        assertEquals(100, d.numbersOpacityPct)
        assertEquals(listOf(20, 30, 40, 50, 60, 70, 80, 90, 100), NavSettings.OPACITY_CHOICES_PCT)
        // 20〜100 の 10 刻みなら読む
        val ok = SettingsCodec.decode(mapOf("buttonOpacityPct" to "20", "numbersOpacityPct" to "50"))
        assertEquals(20, ok.buttonOpacityPct)
        assertEquals(50, ok.numbersOpacityPct)
        // 範囲外・刻みに合わない・読めない値は、その項目だけ既定値
        for (bad in listOf("10", "110", "35", "x", "")) {
            val s = SettingsCodec.decode(mapOf("buttonOpacityPct" to bad, "numbersOpacityPct" to bad, "rateWindowSec" to "30"))
            assertEquals(70, s.buttonOpacityPct)
            assertEquals(100, s.numbersOpacityPct)
            assertEquals(30, s.rateWindowSec)
        }
    }

    @Test
    fun trackBrightnessIsSavedAndInvalidValuesAreDefault() {
        // 読み込んだ軌跡の明るさ: 既定 75%（D03 から。前は 50%）、25 / 50 / 75 / 100 から選ぶ
        assertEquals(75, NavSettings().trackBrightnessPct)
        assertEquals(listOf(25, 50, 75, 100), NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT)
        // 保存して読み直すと同じ値
        for (v in NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT) {
            val s = NavSettings(trackBrightnessPct = v)
            assertEquals(v.toString(), SettingsCodec.encode(s)["trackBrightnessPct"])
            assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        }
        // 範囲外・選べない値・読めない値は、その項目だけ既定値（ほかの項目はそのまま）
        for (bad in listOf("0", "10", "60", "125", "x", "")) {
            val s = SettingsCodec.decode(mapOf("trackBrightnessPct" to bad, "buttonOpacityPct" to "40"))
            assertEquals(75, s.trackBrightnessPct)
            assertEquals(40, s.buttonOpacityPct)
        }
        // 保存がない（前の版）なら既定値
        assertEquals(75, SettingsCodec.decode(mapOf("travelMode" to "CAR")).trackBrightnessPct)
    }

    @Test
    fun ringLabelScaleIsSavedAndOtherNumbersGoToTheNearestStep() {
        // 距離環の数字の大きさ: 既定 125%（D03 から。前は 150%）、100 / 125 / 150 / 175 / 200 から選ぶ
        assertEquals(125, NavSettings().ringLabelScalePct)
        assertEquals(listOf(100, 125, 150, 175, 200), NavSettings.RING_LABEL_SCALE_CHOICES_PCT)
        // 保存して読み直すと同じ値
        for (v in NavSettings.RING_LABEL_SCALE_CHOICES_PCT) {
            val s = NavSettings(ringLabelScalePct = v)
            assertEquals(v.toString(), SettingsCodec.encode(s)["ringLabelScalePct"])
            assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        }
        // 選べる値にない数は一番近い段（前の版の 250 は 200。範囲の外は端の段）。ほかの項目はそのまま
        val nearest = mapOf(
            "250" to 200, "300" to 200, "1000" to 200, "190" to 200,
            "0" to 100, "50" to 100, "-20" to 100, "112" to 100,
            "113" to 125, "137" to 125, "138" to 150, "160" to 150, "163" to 175, " 175 " to 175,
        )
        for ((saved, expected) in nearest) {
            val s = SettingsCodec.decode(mapOf("ringLabelScalePct" to saved, "trackBrightnessPct" to "75"))
            assertEquals(saved, expected, s.ringLabelScalePct)
            assertEquals(75, s.trackBrightnessPct)
        }
        // 数でない値は既定値（125）
        for (bad in listOf("x", "", "150.5", "200.0")) {
            assertEquals(bad, 125, SettingsCodec.decode(mapOf("ringLabelScalePct" to bad)).ringLabelScalePct)
        }
        // 保存がない（前の版）なら既定値（125）。保存してある値（前の既定の 200 など）はそのまま
        assertEquals(125, SettingsCodec.decode(mapOf("travelMode" to "CAR")).ringLabelScalePct)
        assertEquals(200, SettingsCodec.decode(mapOf("travelMode" to "CAR", "ringLabelScalePct" to "200")).ringLabelScalePct)
    }

    @Test
    fun trackColorIsSavedAndOldSettingsKeepTheirColors() {
        // 読み込んだ軌跡の色: 既定は黄（D03 から。前は白）。6色
        assertEquals(TrackColor.YELLOW, NavSettings().trackColor)
        assertEquals(listOf("WHITE", "GREEN", "AMBER", "CYAN", "YELLOW", "BLUE"), TrackColor.entries.map { it.name })
        for (c in TrackColor.entries) {
            val s = NavSettings(trackColor = c)
            assertEquals(c.name, SettingsCodec.encode(s)["trackColor"])
            assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        }
        // 軌跡の色を保存していない設定: 既定の黄。保存してある UI の色・地図の色・明るさは今まで通り読む
        val old = SettingsCodec.decode(mapOf("travelMode" to "CAR", "uiTheme" to "AMBER", "mapTheme" to "WHITE", "trackBrightnessPct" to "25"))
        assertEquals(TrackColor.YELLOW, old.trackColor)
        assertEquals(ColorTheme.AMBER, old.uiTheme)
        assertEquals(ColorTheme.WHITE, old.mapTheme)
        assertEquals(25, old.trackBrightnessPct)
        // 読めない名前は既定の黄
        for (bad in listOf("PURPLE", "", "cyan")) {
            assertEquals(bad, TrackColor.YELLOW, SettingsCodec.decode(mapOf("trackColor" to bad)).trackColor)
        }
    }

    @Test
    fun ownshipPositionKeepsTheSavedNames() {
        // 前からの段の保存値（STANDARD / HIGH）は変えない。さらに高めは HIGHER。既定は D03 からさらに高め（前は高め）
        assertEquals(OwnshipPosition.HIGHER, NavSettings().ownshipPosition)
        assertEquals("HIGHER", SettingsCodec.encode(NavSettings())["ownshipPosition"])
        assertEquals(listOf("STANDARD", "HIGH", "HIGHER"), OwnshipPosition.entries.map { it.name })
        for (p in OwnshipPosition.entries) {
            assertEquals(p, SettingsCodec.decode(mapOf("ownshipPosition" to p.name)).ownshipPosition)
        }
        assertEquals(OwnshipPosition.HIGH, SettingsCodec.decode(mapOf("ownshipPosition" to "HIGH")).ownshipPosition)
        // 読めない名前・保存がないときは既定のさらに高め。保存してある標準はそのまま
        assertEquals(OwnshipPosition.HIGHER, SettingsCodec.decode(mapOf("ownshipPosition" to "TOP")).ownshipPosition)
        assertEquals(OwnshipPosition.HIGHER, SettingsCodec.decode(mapOf("travelMode" to "CAR")).ownshipPosition)
        assertEquals(OwnshipPosition.STANDARD, SettingsCodec.decode(mapOf("travelMode" to "CAR", "ownshipPosition" to "STANDARD")).ownshipPosition)
    }

    @Test
    fun missingValuesAreDefaults() {
        assertEquals(NavSettings(), SettingsCodec.decode(emptyMap()))
        // D06 より前に保存した設定（移動手段の項目がない）はカスタム1 として読む（値は保存してあったもの = ここでは既定値）
        assertEquals(NavSettings(uiTheme = ColorTheme.GREEN, travelMode = TravelMode.CUSTOM1), SettingsCodec.decode(mapOf("uiTheme" to "GREEN")))
    }

    @Test
    fun inputAloneIsNotOldSettings() {
        // 同じ保存先の INPUT（LIVE / REPLAY）だけが保存されている = 設定はまだ保存していない。自動車のまま
        assertEquals(NavSettings(), SettingsCodec.decode(mapOf("input" to "REPLAY")))
        assertEquals(NavSettings(), SettingsCodec.decode(mapOf("input" to "LIVE", "somethingElse" to "1")))
        // 設定の項目が1つでもあれば前の版の設定（カスタム1 に移す）
        val old = SettingsCodec.decode(mapOf("input" to "REPLAY", "reachRadiusM" to "100.0"))
        assertEquals(TravelMode.CUSTOM1, old.travelMode)
        assertEquals(100.0, old.customReach[0].reachRadiusM, 0.0)
    }

    @Test
    fun oldColorThemeIsCarriedToBothColors() {
        // 古い版の「色テーマ」（1つ）は、UI・地図の両方の初期値として引き継ぐ
        val old = SettingsCodec.decode(mapOf("colorTheme" to "AMBER"))
        assertEquals(ColorTheme.AMBER, old.uiTheme)
        assertEquals(ColorTheme.AMBER, old.mapTheme)
        // 新しい項目が保存されていれば、そちらを使う
        val both = SettingsCodec.decode(mapOf("colorTheme" to "AMBER", "uiTheme" to "WHITE", "mapTheme" to "GREEN"))
        assertEquals(ColorTheme.WHITE, both.uiTheme)
        assertEquals(ColorTheme.GREEN, both.mapTheme)
        // 既定は UI 白・地図 緑。HUD に描く WP の数は 10（D03 から。前は 15。1〜20）
        assertEquals(ColorTheme.WHITE, NavSettings().uiTheme)
        assertEquals(ColorTheme.GREEN, NavSettings().mapTheme)
        assertEquals(10, NavSettings().hudWpCount)
        assertEquals(20, SettingsCodec.decode(mapOf("hudWpCount" to "99")).hudWpCount)
        assertEquals(18, SettingsCodec.decode(mapOf("hudWpCount" to "18")).hudWpCount)
    }

    @Test
    fun brokenValuesAreDefaults() {
        val s = SettingsCodec.decode(
            mapOf(
                "sourceMode" to "RADAR",
                "holdEnterSpeedMps" to "abc",
                "rateWindowSec" to "45",
                "rangeStepsKm" to "3,7,x",
                "wpButtonsMax" to "99",
                "hudWpCount" to "0",
                "keepScreenOn" to "yes",
            ),
        )
        val d = NavSettings()
        assertEquals(d.sourceMode, s.sourceMode)
        assertEquals(d.holdEnterSpeedMps, s.holdEnterSpeedMps)
        assertEquals(d.rateWindowSec, s.rateWindowSec)
        assertEquals(d.rangeStepsKm, s.rangeStepsKm)
        assertEquals(6, s.wpButtonsMax)
        assertEquals(1, s.hudWpCount)
        assertEquals(d.keepScreenOn, s.keepScreenOn)
    }

    @Test
    fun rangeStepsAreFilteredAndSorted() {
        val s = SettingsCodec.decode(mapOf("rangeStepsKm" to "10, 0.5,3 ,0.5,1"))
        assertEquals(listOf(0.5, 1.0, 10.0), s.rangeStepsKm)
        // 7b までの段（0.25km）は、今の段にないので捨てる
        assertEquals(listOf(0.5, 2.0), SettingsCodec.decode(mapOf("rangeStepsKm" to "0.25,0.5,2")).rangeStepsKm)
    }

    @Test
    fun defaultsAreWithinTheRanges() {
        val d = NavSettings()
        assertTrue(d.holdEnterSpeedMps in NavSettings.HOLD_ENTER_SPEED_MPS_RANGE)
        assertTrue(d.holdExitSpeedMps > d.holdEnterSpeedMps && d.holdExitSpeedMps <= NavSettings.HOLD_EXIT_SPEED_MAX_MPS)
        assertTrue(d.maxGpsAccM in NavSettings.MAX_GPS_ACC_M_RANGE)
        assertTrue(d.maxGpsBearingAccDeg in NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)
        assertTrue(d.reachRadiusM in NavSettings.REACH_RADIUS_CHOICES_M)
        assertTrue(d.sidePassMaxM in NavSettings.SIDE_PASS_MAX_M_RANGE)
        assertTrue(d.sidePassDepartM in NavSettings.SIDE_PASS_DEPART_M_RANGE)
        assertTrue(d.passMaxApproachM in NavSettings.PASS_MAX_APPROACH_M_RANGE)
        assertTrue(d.passDepartM in NavSettings.PASS_DEPART_M_RANGE)
        assertTrue(d.passHoldSec in NavSettings.PASS_HOLD_SEC_RANGE)
        assertTrue(d.noFixTimeoutSec in NavSettings.NO_FIX_TIMEOUT_SEC_RANGE)
        assertTrue(d.altOffsetM in NavSettings.ALT_OFFSET_M_RANGE)
        assertTrue(d.autoRangeZoomInDelaySec in NavSettings.AUTO_RANGE_ZOOM_IN_DELAY_SEC_RANGE)
        assertTrue(d.initialRangeKm in RangeAuto.ALL_STEPS_KM)
    }

    @Test
    fun outOfRangeValuesAreDefaults() {
        // 設定画面で選べない値（範囲外・段にない値）は、その項目だけ初期値（§6.9）
        val d = NavSettings()
        val s = SettingsCodec.decode(
            mapOf(
                "travelMode" to "CAR",
                "maxGpsAccM" to "500",
                "maxGpsBearingAccDeg" to "1",
                "noFixTimeoutSec" to "0",
                "altOffsetM" to "-999",
                "autoRangeZoomInDelaySec" to "-1",
                "initialRangeKm" to "3.0",
            ),
        )
        assertEquals(d, s)
        // 範囲内なら使う（刻みに乗っていなくてもよい）
        val ok = SettingsCodec.decode(mapOf("maxGpsAccM" to "42", "noFixTimeoutSec" to "120", "altOffsetM" to "-200", "initialRangeKm" to "50.0"))
        assertEquals(42f, ok.maxGpsAccM, 0f)
        assertEquals(120, ok.noFixTimeoutSec)
        assertEquals(-200.0, ok.altOffsetM, 0.0)
        assertEquals(50.0, ok.initialRangeKm, 0.0)
    }

    @Test
    fun holdSpeedsMustBeInOrder() {
        val d = NavSettings()
        // 範囲外の入る速度は初期値
        assertEquals(d.holdEnterSpeedMps, SettingsCodec.decode(mapOf("holdEnterSpeedMps" to "0.1")).holdEnterSpeedMps)
        // 解く速度が入る速度以下・上限超えなら、初期値（入る速度 + 0.1 の方が大きければそちら）
        val a = SettingsCodec.decode(mapOf("holdEnterSpeedMps" to "2.5", "holdExitSpeedMps" to "2.5"))
        assertEquals(2.5f, a.holdEnterSpeedMps, 0f)
        assertEquals(d.holdExitSpeedMps, a.holdExitSpeedMps, 0f)
        val b = SettingsCodec.decode(mapOf("holdEnterSpeedMps" to "8.0", "holdExitSpeedMps" to "20"))
        assertEquals(8.1f, b.holdExitSpeedMps, 1e-5f)
        val c = SettingsCodec.decode(mapOf("holdEnterSpeedMps" to "4.0", "holdExitSpeedMps" to "6.0"))
        assertEquals(6f, c.holdExitSpeedMps, 0f)
    }

    @Test
    fun outOfRangeReachValuesFallBackPerSlot() {
        // カスタム枠の値も範囲を確かめる。範囲外の項目だけ、その枠の初期値（自動車の値）
        val car = ReachProfile.CAR
        val s = SettingsCodec.decode(
            mapOf(
                "travelMode" to "CUSTOM2",
                "custom2.reachRadiusM" to "75.0",
                "custom2.sidePassMaxM" to "5000",
                "custom2.sidePassDepartM" to "40.0",
                "custom2.passMaxApproachM" to "10",
                "custom2.passDepartM" to "1000",
                "custom2.passHoldSec" to "0",
            ),
        )
        assertEquals(car.copy(sidePassDepartM = 40.0), s.customReach[1])
        assertEquals(car.copy(sidePassDepartM = 40.0), s.reachProfile)
        assertEquals(40.0, s.sidePassDepartM, 0.0)
        // 範囲内の値（到着半径 200m、通過判定 1000m・+20m・10 秒）はそのまま
        val ok = SettingsCodec.decode(
            mapOf(
                "travelMode" to "CUSTOM3", "custom3.reachRadiusM" to "200.0", "custom3.passMaxApproachM" to "1000.0",
                "custom3.passDepartM" to "20.0", "custom3.passHoldSec" to "10",
            ),
        )
        assertEquals(car.copy(reachRadiusM = 200.0, passMaxApproachM = 1000.0, passDepartM = 20.0, passHoldSec = 10), ok.customReach[2])
    }
}
