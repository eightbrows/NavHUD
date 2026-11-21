package io.github.eightbrows.navhud.core.nav

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsCodecTest {

    @Test
    fun roundTripOfEveryValue() {
        // 既定値とすべて違う値にする
        val p1 = ReachProfile(
            50.0, false, 100.0, 15.0, false, 250.0, 40.0, 4, sidePassMinKmh = 8, holdEnterKmh = 5, holdExitKmh = 15,
            headingMethod = HeadingMethod.GPS_TRACK, maxBearingAccDeg = 25f, gsAverageSec = 3, etaAverageSec = 20,
        )
        val p2 = ReachProfile(
            200.0, false, 200.0, 20.0, false, 450.0, 80.0, 8, sidePassMinKmh = 15, holdEnterKmh = 3, holdExitKmh = 8,
            headingMethod = HeadingMethod.GPS_TRACK, maxBearingAccDeg = 35f, gsAverageSec = 5, etaAverageSec = 60,
        )
        val p3 = ReachProfile(
            15.0, false, 300.0, 30.0, false, 500.0, 60.0, 10, sidePassMinKmh = 20, holdEnterKmh = 7, holdExitKmh = 11,
            headingMethod = HeadingMethod.GPS_TRACK, maxBearingAccDeg = 40f, gsAverageSec = 10, etaAverageSec = 120,
        )
        val s = NavSettings(
            maxGpsAccM = 20f,
            noFixTimeoutSec = 15,
            altOffsetM = 35.5,
            displayMode = DisplayMode.NORTH_UP,
            aglNegative = AglNegative.SHOW,
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
            baseMap = false,
            mapItems = NavSettings.DEFAULT_MAP_ITEMS + mapOf(MapItem.MOTORWAY to MapRange.UPTO_100KM, MapItem.WATER to MapRange.UPTO_25KM),
            landmarks = NavSettings.DEFAULT_LANDMARKS + mapOf(LandmarkItem.PEAK_LOW to MapRange.OFF, LandmarkItem.AIRPORT_MIL_LARGE to MapRange.UPTO_50KM),
            landmarkBrightness = 2,
            mapLineWidth = 3,
            mapLineBrightness = 2,
            buttonOpacityPct = 40,
            numbersOpacityPct = 60,
            trackColor = TrackColor.CYAN,
            trackBrightnessPct = 40,
            ringLabelScalePct = 175,
            ringBrightnessPct = 40,
            autoMinRangeKm = 0.2,
            autoMaxRangeKm = 5.0,
            autoHoldAfterWpSec = 20,
            autoZoomInDistRatio = 2.0,
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
        // 既定: 詳細の限度 100m の段（R1 50m）、広域の限度 500m の段（R1 250m。D03 から。前は 1km の段）
        assertEquals(0.1, NavSettings().autoMinRangeKm, 0.0)
        assertEquals(0.5, NavSettings().autoMaxRangeKm, 0.0)
        // 段の一覧にない値・読めない値は既定値
        val s = SettingsCodec.decode(mapOf("autoMinRangeKm" to "0.3", "autoMaxRangeKm" to "x"))
        assertEquals(0.1, s.autoMinRangeKm, 0.0)
        assertEquals(0.5, s.autoMaxRangeKm, 0.0)
        assertEquals(0.05, SettingsCodec.decode(mapOf("autoMinRangeKm" to "0.05")).autoMinRangeKm, 0.0)
        // WP 通過後の待機（既定 10 秒）と、詳細へ切り替える距離（既定 1.3、1.0〜2.0 の 0.1 刻み。今の段の R1 が基準）
        assertEquals(10, NavSettings().autoHoldAfterWpSec)
        assertEquals(1.3, NavSettings().autoZoomInDistRatio, 0.0)
        assertEquals(0, SettingsCodec.decode(mapOf("autoHoldAfterWpSec" to "0")).autoHoldAfterWpSec)
        assertEquals(1.7, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "1.7")).autoZoomInDistRatio, 0.0)
        val choices = NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES
        assertEquals(11, choices.size)
        assertEquals((10..20).map { it / 10.0 }, choices)
        // 保存は新しいキー
        assertEquals("1.3", SettingsCodec.encode(NavSettings())["autoZoomInR1Ratio"])
        assertEquals(null, SettingsCodec.encode(NavSettings())["autoZoomInDistRatio"])
    }

    @Test
    fun oldZoomInRatioIsNotUsed() {
        // 前の版の倍率（1つ詳細側の段の R1 が基準）は意味が違うので読まず、初期値（1.3）にする。ほかの設定はそのまま読む
        val s = SettingsCodec.decode(mapOf("travelMode" to "CAR", "autoZoomInDistRatio" to "2.5", "autoHoldAfterWpSec" to "20"))
        assertEquals(1.3, s.autoZoomInDistRatio, 0.0)
        assertEquals(20, s.autoHoldAfterWpSec)
        // 前の版の倍率だけが保存されていても、前の版の設定として扱う（移動手段の移行と同じ）
        assertEquals(TravelMode.CUSTOM1, SettingsCodec.decode(mapOf("autoZoomInDistRatio" to "2.0")).travelMode)
    }

    @Test
    fun opacityDefaultsAndInvalidValues() {
        // 既定: ボタン 80%（20261104-D01 から。前は 70%）、数値 100%（Tuning.kt）
        val d = NavSettings()
        assertEquals(80, d.buttonOpacityPct)
        assertEquals(100, d.numbersOpacityPct)
        // 選ぶボタンの 5 つ（20261104-D01。20% 刻み）。初期値を含む
        assertEquals(listOf(20, 40, 60, 80, 100), NavSettings.OPACITY_CHOICES_PCT)
        for (v in NavSettings.OPACITY_CHOICES_PCT) {
            val s = SettingsCodec.decode(mapOf("buttonOpacityPct" to v.toString(), "numbersOpacityPct" to v.toString()))
            assertEquals(v, s.buttonOpacityPct)
            assertEquals(v, s.numbersOpacityPct)
        }
        // 前の値（D02 の 30 / 50 / 70 / 90、その前の 10 刻み）は一番近い値。ちょうど真ん中なら大きい方
        val old = mapOf(30 to 40, 50 to 60, 70 to 80, 90 to 100, 25 to 20, 35 to 40, 85 to 80, 95 to 100, 20 to 20, 100 to 100)
        for ((saved, expected) in old) {
            val s = SettingsCodec.decode(mapOf("buttonOpacityPct" to saved.toString(), "numbersOpacityPct" to saved.toString()))
            assertEquals("button $saved", expected, s.buttonOpacityPct)
            assertEquals("numbers $saved", expected, s.numbersOpacityPct)
        }
        // 20〜100 の外・読めない値は、その項目だけ既定値
        for (bad in listOf("10", "110", "19", "x", "")) {
            val s = SettingsCodec.decode(mapOf("buttonOpacityPct" to bad, "numbersOpacityPct" to bad))
            assertEquals(80, s.buttonOpacityPct)
            assertEquals(100, s.numbersOpacityPct)
        }
    }

    @Test
    fun trackBrightnessIsSavedAndInvalidValuesAreDefault() {
        // 読み込んだ軌跡の明るさ: 既定 100%、20 / 40 / 60 / 80 / 100 から選ぶ（20261104-D01。前は 25 / 50 / 75 / 100）
        assertEquals(100, NavSettings().trackBrightnessPct)
        assertEquals(listOf(20, 40, 60, 80, 100), NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT)
        // 前の値は一番近い値（真ん中なら大きい方）: 25 → 20、50 → 60、75 → 80
        for ((saved, expected) in mapOf(25 to 20, 50 to 60, 75 to 80, 100 to 100, 30 to 40, 70 to 80)) {
            assertEquals("$saved", expected, SettingsCodec.decode(mapOf("trackBrightnessPct" to saved.toString())).trackBrightnessPct)
        }
        // 保存して読み直すと同じ値
        for (v in NavSettings.TRACK_BRIGHTNESS_CHOICES_PCT) {
            val s = NavSettings(trackBrightnessPct = v)
            assertEquals(v.toString(), SettingsCodec.encode(s)["trackBrightnessPct"])
            assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
        }
        // 20〜100 の外・読めない値は、その項目だけ既定値（ほかの項目はそのまま）
        for (bad in listOf("0", "10", "19", "125", "x", "")) {
            val s = SettingsCodec.decode(mapOf("trackBrightnessPct" to bad, "buttonOpacityPct" to "40"))
            assertEquals(100, s.trackBrightnessPct)
            assertEquals(40, s.buttonOpacityPct)
        }
        // 保存がない（前の版）なら既定値
        assertEquals(100, SettingsCodec.decode(mapOf("travelMode" to "CAR")).trackBrightnessPct)
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
            val s = SettingsCodec.decode(mapOf("ringLabelScalePct" to saved, "trackBrightnessPct" to "80"))
            assertEquals(saved, expected, s.ringLabelScalePct)
            assertEquals(80, s.trackBrightnessPct)
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
        val old = SettingsCodec.decode(mapOf("travelMode" to "CAR", "uiTheme" to "AMBER", "mapTheme" to "WHITE", "trackBrightnessPct" to "20"))
        assertEquals(TrackColor.YELLOW, old.trackColor)
        assertEquals(ColorTheme.AMBER, old.uiTheme)
        assertEquals(ColorTheme.WHITE, old.mapTheme)
        assertEquals(20, old.trackBrightnessPct)
        // 読めない名前は既定の黄
        for (bad in listOf("PURPLE", "", "cyan")) {
            assertEquals(bad, TrackColor.YELLOW, SettingsCodec.decode(mapOf("trackColor" to bad)).trackColor)
        }
    }

    @Test
    fun ownshipPositionKeepsTheSavedNames() {
        // 5段（D04 から）。前からの3段の保存値（STANDARD / HIGH / HIGHER）は、同じ段のまま読む。足した2段は NEAR_CENTER / CENTER。
        // 既定は下から4段目（NEAR_CENTER。20261025-D01 で実機の設定に合わせた。前は 3段目の HIGHER）
        assertEquals(OwnshipPosition.NEAR_CENTER, NavSettings().ownshipPosition)
        assertEquals("NEAR_CENTER", SettingsCodec.encode(NavSettings())["ownshipPosition"])
        assertEquals(listOf("STANDARD", "HIGH", "HIGHER", "NEAR_CENTER", "CENTER"), OwnshipPosition.entries.map { it.name })
        assertEquals(OwnshipPosition.NEAR_CENTER, SettingsCodec.decode(mapOf("ownshipPosition" to "NEAR_CENTER")).ownshipPosition)
        assertEquals(OwnshipPosition.CENTER, SettingsCodec.decode(mapOf("ownshipPosition" to "CENTER")).ownshipPosition)
        for (p in OwnshipPosition.entries) {
            assertEquals(p, SettingsCodec.decode(mapOf("ownshipPosition" to p.name)).ownshipPosition)
        }
        assertEquals(OwnshipPosition.HIGH, SettingsCodec.decode(mapOf("ownshipPosition" to "HIGH")).ownshipPosition)
        // 読めない名前・保存がないときは既定の4段目。保存してある1段目（標準）・3段目（HIGHER）はそのまま
        assertEquals(OwnshipPosition.NEAR_CENTER, SettingsCodec.decode(mapOf("ownshipPosition" to "TOP")).ownshipPosition)
        assertEquals(OwnshipPosition.NEAR_CENTER, SettingsCodec.decode(mapOf("travelMode" to "CAR")).ownshipPosition)
        assertEquals(OwnshipPosition.HIGHER, SettingsCodec.decode(mapOf("travelMode" to "CAR", "ownshipPosition" to "HIGHER")).ownshipPosition)
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
    fun zoomInRatioAboveTheUpperLimitIsReadAsTwo() {
        // 詳細へ切り替える距離の上限は 2.0 倍（D04 から。前は 3.0）。保存されていた値が 2.0 を超えていたら 2.0 として読む
        for (saved in listOf("2.1", "2.5", "3.0", "3.5", "99")) {
            assertEquals(saved, 2.0, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to saved)).autoZoomInDistRatio, 0.0)
        }
        // 2.0 以下はそのまま。下限より小さければ下限。0.1 刻みに乗っていない値は一番近い値
        assertEquals(2.0, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "2.0")).autoZoomInDistRatio, 0.0)
        assertEquals(1.0, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "1.0")).autoZoomInDistRatio, 0.0)
        assertEquals(1.0, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "0.4")).autoZoomInDistRatio, 0.0)
        assertEquals(1.3, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "1.26")).autoZoomInDistRatio, 0.0)
        assertEquals(1.2, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "1.24")).autoZoomInDistRatio, 0.0)
        // 数でない値・保存がないときは既定値
        assertEquals(1.3, SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "x")).autoZoomInDistRatio, 0.0)
        assertEquals(1.3, SettingsCodec.decode(mapOf("travelMode" to "CAR")).autoZoomInDistRatio, 0.0)
        // 読み替えた値は、保存して読み直しても同じ
        val s = SettingsCodec.decode(mapOf("autoZoomInR1Ratio" to "3.0"))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun valuesNotInTheChoicesGoToTheNearestChoice() {
        // 決まった値から選ぶ3項目（D04 から）: 保存されていた値が選べる値にないときは、一番近い値に読み替える
        assertEquals(listOf(3, 5, 10, 15, 20, 30, 60, 120), NavSettings.NO_FIX_TIMEOUT_CHOICES_SEC)
        assertEquals(listOf(3f, 5f, 10f, 15f, 20f, 30f, 50f, 100f), NavSettings.MAX_GPS_ACC_CHOICES_M)
        assertEquals(listOf(0, 5, 10, 15, 20, 30, 60), NavSettings.AUTO_HOLD_AFTER_WP_CHOICES_SEC)
        // 初期値は今のまま（10 秒 / 15 m / 10 秒）で、どれも選べる値
        val d = NavSettings()
        assertEquals(10, d.noFixTimeoutSec)
        assertEquals(15f, d.maxGpsAccM, 0f)
        assertEquals(10, d.autoHoldAfterWpSec)
        // NO FIX とみなす時間（前は 3〜120 秒の 1 秒刻み）
        val noFix = mapOf("3" to 3, "4" to 3, "7" to 5, "8" to 10, "12" to 10, "13" to 15, "25" to 20, "26" to 30, "45" to 30, "46" to 60, "90" to 60, "91" to 120, "120" to 120, "0" to 3, "999" to 120)
        for ((saved, expected) in noFix) assertEquals(saved, expected, SettingsCodec.decode(mapOf("noFixTimeoutSec" to saved)).noFixTimeoutSec)
        // GPS の水平精度の上限（前は 3〜100 m の 1 m 刻み）
        val acc = mapOf("3" to 3f, "4" to 3f, "8" to 10f, "12" to 10f, "13" to 15f, "17" to 15f, "18" to 20f, "42" to 50f, "75" to 50f, "76" to 100f, "500" to 100f, "0.5" to 3f, "15.0" to 15f)
        for ((saved, expected) in acc) assertEquals(saved, expected, SettingsCodec.decode(mapOf("maxGpsAccM" to saved)).maxGpsAccM, 0f)
        // WP を通り過ぎてから縮尺を変えるまで（前は 0〜60 秒の 1 秒刻み）
        val hold = mapOf("0" to 0, "2" to 0, "3" to 5, "7" to 5, "8" to 10, "17" to 15, "18" to 20, "26" to 30, "45" to 30, "46" to 60, "61" to 60, "-5" to 0)
        for ((saved, expected) in hold) assertEquals(saved, expected, SettingsCodec.decode(mapOf("autoHoldAfterWpSec" to saved)).autoHoldAfterWpSec)
        // 数でない値・保存がないときは既定値。ほかの項目はそのまま
        val bad = SettingsCodec.decode(mapOf("noFixTimeoutSec" to "x", "maxGpsAccM" to "", "autoHoldAfterWpSec" to "1.5", "buttonOpacityPct" to "40"))
        assertEquals(10, bad.noFixTimeoutSec)
        assertEquals(15f, bad.maxGpsAccM, 0f)
        assertEquals(10, bad.autoHoldAfterWpSec)
        assertEquals(40, bad.buttonOpacityPct)
        // 読み替えた値は、保存して読み直しても同じ
        val s = SettingsCodec.decode(mapOf("noFixTimeoutSec" to "45", "maxGpsAccM" to "42", "autoHoldAfterWpSec" to "7"))
        assertEquals(s, SettingsCodec.decode(SettingsCodec.encode(s)))
    }

    @Test
    fun brokenValuesAreDefaults() {
        val s = SettingsCodec.decode(
            mapOf(
                "holdEnterSpeedMps" to "abc",
                "rangeStepsKm" to "3,7,x",
                "wpButtonsMax" to "99",
                "hudWpCount" to "0",
                "keepScreenOn" to "yes",
            ),
        )
        val d = NavSettings()
        assertEquals(d.holdEnterSpeedMps, s.holdEnterSpeedMps)
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
        assertTrue(NavSettings.holdSpeedKmh(d.holdEnterSpeedMps) in NavSettings.HOLD_SPEED_KMH_RANGE)
        assertTrue(NavSettings.holdSpeedKmh(d.holdExitSpeedMps) in NavSettings.HOLD_SPEED_KMH_RANGE)
        assertTrue(d.holdExitSpeedMps > d.holdEnterSpeedMps)
        assertTrue(d.maxGpsAccM in NavSettings.MAX_GPS_ACC_CHOICES_M)
        assertTrue(d.autoHoldAfterWpSec in NavSettings.AUTO_HOLD_AFTER_WP_CHOICES_SEC)
        assertTrue(d.autoZoomInDistRatio in NavSettings.AUTO_ZOOM_IN_DIST_RATIO_CHOICES)
        assertTrue(d.maxGpsBearingAccDeg in NavSettings.MAX_GPS_BEARING_ACC_DEG_RANGE)
        assertTrue(d.reachRadiusM in NavSettings.REACH_RADIUS_CHOICES_M)
        assertTrue(d.sidePassMaxM in NavSettings.SIDE_PASS_MAX_M_RANGE)
        assertTrue(d.sidePassDepartM in NavSettings.SIDE_PASS_DEPART_M_RANGE)
        assertTrue(d.passMaxApproachM in NavSettings.PASS_MAX_APPROACH_M_RANGE)
        assertTrue(d.passDepartM in NavSettings.PASS_DEPART_M_RANGE)
        assertTrue(d.passHoldSec in NavSettings.PASS_HOLD_SEC_RANGE)
        assertTrue(d.noFixTimeoutSec in NavSettings.NO_FIX_TIMEOUT_CHOICES_SEC)
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
                "custom1.maxBearingAccDeg" to "1",
                "altOffsetM" to "-999",
                "autoRangeZoomInDelaySec" to "-1",
                "initialRangeKm" to "3.0",
            ),
        )
        assertEquals(d, s)
        // 範囲内なら使う（決まった値から選ぶ項目は valuesNotInTheChoicesGoToTheNearestChoice）
        val ok = SettingsCodec.decode(mapOf("travelMode" to "CUSTOM1", "custom1.maxBearingAccDeg" to "42", "altOffsetM" to "-200", "initialRangeKm" to "50.0"))
        assertEquals(42f, ok.maxGpsBearingAccDeg, 0f)
        assertEquals(42f, ok.customReach[0].maxBearingAccDeg, 0f)
        assertEquals(-200.0, ok.altOffsetM, 0.0)
        assertEquals(50.0, ok.initialRangeKm, 0.0)
    }

    @Test
    fun holdSpeedsAreWholeKmhAndInOrder() {
        // 方位を止める / 戻す速度: 1〜36 km/h の整数、1 km/h 刻み。初期値は 4 / 6 km/h（20261119-D01。前は 7 / 11 km/h）。
        // 20261119-D03 から到達判定のプリセットの値（保存は custom1.〜custom3. の holdEnterKmh / holdExitKmh。自動車は 4 / 6 km/h で決まった値）
        fun kmh(s: NavSettings) = NavSettings.holdSpeedKmh(s.holdEnterSpeedMps) to NavSettings.holdSpeedKmh(s.holdExitSpeedMps)
        fun custom1(enter: String?, exit: String?) =
            SettingsCodec.decode(listOfNotNull(enter?.let { "custom1.holdEnterKmh" to it }, exit?.let { "custom1.holdExitKmh" to it }, "travelMode" to "CUSTOM1").toMap())
        val d = NavSettings()
        assertEquals(4 to 6, kmh(d))
        assertEquals(4 / 3.6f, d.holdEnterSpeedMps, 1e-6f)
        assertEquals(6 / 3.6f, d.holdExitSpeedMps, 1e-6f)
        assertEquals(1..36, NavSettings.HOLD_SPEED_KMH_RANGE)
        assertEquals(4 to 6, ReachProfile.CAR.holdEnterKmh to ReachProfile.CAR.holdExitKmh)
        // カスタムの値
        assertEquals(5 to 15, kmh(custom1("5", "15")))
        assertEquals(5 / 3.6f, custom1("5", "15").holdEnterSpeedMps, 1e-6f)
        // 戻す速度は止める速度より 2 km/h 以上大きい（20261119-D01。前は 1 km/h）: 足りなければ 止める速度 + 2。止める速度は 1〜34
        assertEquals(2, NavSettings.HOLD_SPEED_MIN_GAP_KMH)
        assertEquals(9 to 11, kmh(custom1("9", "9")))
        assertEquals(6 to 8, kmh(custom1("6", "7")))
        assertEquals(34 to 36, kmh(custom1("35", "36")))
        assertEquals(1 to 3, kmh(custom1("0", "0")))
        assertEquals(6 to 8, NavSettings.holdSpeedsKmh(6, 7))
        assertEquals(6 to 8, NavSettings.holdSpeedsKmh(6, 8))
        assertEquals(34 to 36, NavSettings.holdSpeedsKmh(35, 36))
        assertEquals(1 to 3, NavSettings.holdSpeedsKmh(0, 0))
        // 数でない値・保存がないときは初期値
        assertEquals(4 to 6, kmh(custom1("abc", "")))
        assertEquals(4 to 6, kmh(custom1(null, null)))
        // 自動車では、保存に何があっても 4 / 6 km/h
        val car = SettingsCodec.decode(mapOf("custom1.holdEnterKmh" to "9", "custom1.holdExitKmh" to "20", "travelMode" to "CAR"))
        assertEquals(4 to 6, kmh(car))
        assertEquals(9 to 20, car.customReach[0].holdEnterKmh to car.customReach[0].holdExitKmh)
        // 保存して読み直しても同じ（差が 2 km/h 以上のどの組でも）
        for (enter in 1..34) for (exit in listOf(enter + 2, 36)) {
            val s = NavSettings().selectTravelMode(TravelMode.CUSTOM2).editReach { it.copy(holdEnterKmh = enter, holdExitKmh = exit) }
            val back = SettingsCodec.decode(SettingsCodec.encode(s))
            assertEquals(enter to exit, kmh(back))
            assertEquals(s, back)
        }
    }

    /** 20261119-D03: 前の全体の方位を止める / 戻す速度の保存は使わない（消す）。D02 の移し替えの印も消す */
    @Test
    fun oldGlobalHoldSpeedsAreDropped() {
        assertTrue("holdEnterSpeedMps" in SettingsCodec.OBSOLETE_KEYS)
        assertTrue("holdExitSpeedMps" in SettingsCodec.OBSOLETE_KEYS)
        assertTrue("holdDefaultsMigrated" in SettingsCodec.OBSOLETE_KEYS)
        // 保存には書かない
        val keys = SettingsCodec.encode(NavSettings()).keys
        assertTrue(SettingsCodec.OBSOLETE_KEYS.none { it in keys })
        // 前の版の保存（全体の 7 / 11 km/h・9 / 20 km/h・印）があっても、自動車は 4 / 6 km/h、カスタムは初期値 4 / 6 km/h から
        for ((enter, exit) in listOf(7 to 11, 9 to 20)) {
            val old = SettingsCodec.encode(NavSettings()).filterKeys { !it.contains("hold") } + mapOf(
                "holdEnterSpeedMps" to NavSettings.kmhToMps(enter).toString(),
                "holdExitSpeedMps" to NavSettings.kmhToMps(exit).toString(),
                "holdDefaultsMigrated" to "20261119-D02",
            )
            for (mode in TravelMode.entries) {
                val s = SettingsCodec.decode(old + ("travelMode" to mode.name))
                // 歩行（20261121-D01）は決まった値 1 / 3 km/h、ほかは 4 / 6 km/h
                val (en, ex) = if (mode == TravelMode.WALK) 1 to 3 else 4 to 6
                assertEquals("$enter / $exit $mode", en / 3.6f, s.holdEnterSpeedMps, 1e-6f)
                assertEquals("$enter / $exit $mode", ex / 3.6f, s.holdExitSpeedMps, 1e-6f)
                assertEquals(listOf(4, 4, 4), s.customReach.map { it.holdEnterKmh })
                assertEquals(listOf(6, 6, 6), s.customReach.map { it.holdExitKmh })
            }
        }
        // 前の全体の保存だけがある端末（D06 より前のように見えても）は「前の版の設定」として扱い、到達判定のプリセットはカスタム1
        // （前からの決まり。移動手段の項目がない設定は、保存してあった到達判定の値をカスタム1 に移す）
        val onlyHold = SettingsCodec.decode(mapOf("holdEnterSpeedMps" to "1.9444445", "holdExitSpeedMps" to "3.0555556"))
        assertEquals(TravelMode.CUSTOM1, onlyHold.travelMode)
        assertEquals(4 / 3.6f, onlyHold.holdEnterSpeedMps, 1e-6f)
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

    @Test
    fun rateWindowIsDiscarded() {
        // 20261104-D03: RATE の窓（変化を測る時間）の設定はなくした。前の保存（rateWindowSec）は読まずに捨て、ほかの設定は変わらない
        assertTrue("rateWindowSec" in SettingsCodec.OBSOLETE_KEYS)
        assertFalse(SettingsCodec.encode(NavSettings()).containsKey("rateWindowSec"))
        val saved = SettingsCodec.encode(NavSettings(noFixTimeoutSec = 15, panReturnSec = 20))
        for (old in listOf("10", "30", "60", "45", "x")) {
            assertEquals(SettingsCodec.decode(saved), SettingsCodec.decode(saved + ("rateWindowSec" to old)))
        }
        // 前の保存で捨てる名前は、今の保存の名前と重ならない
        assertTrue(SettingsCodec.OBSOLETE_KEYS.none { it in SettingsCodec.encode(NavSettings()).keys })
    }

    /** 20261121-D01: コンパスを外した。前の「方位の求め方」（HYBRID・COMPASS・GPS）と全体の方位精度の上限は読まずに消す */
    @Test
    fun compassAndGlobalBearingAccuracyAreDropped() {
        assertTrue("sourceMode" in SettingsCodec.OBSOLETE_KEYS)
        assertTrue("maxGpsBearingAccDeg" in SettingsCodec.OBSOLETE_KEYS)
        val keys = SettingsCodec.encode(NavSettings().selectTravelMode(TravelMode.WALK)).keys
        assertTrue(SettingsCodec.OBSOLETE_KEYS.none { it in keys })
        for (mode in listOf("HYBRID", "COMPASS", "GPS")) {
            val s = SettingsCodec.decode(mapOf("travelMode" to "CAR", "sourceMode" to mode, "maxGpsBearingAccDeg" to "45.0"))
            assertEquals(NavSettings(), s)
            assertEquals(HeadingMethod.GPS, s.headingMethod)
            assertEquals(20f, s.maxGpsBearingAccDeg)
        }
        // 前の全体の値はカスタムにも入らない（カスタムは自動車の値 20° から）
        val c = SettingsCodec.decode(mapOf("travelMode" to "CUSTOM1", "custom1.reachRadiusM" to "50.0", "maxGpsBearingAccDeg" to "45.0", "sourceMode" to "HYBRID"))
        assertEquals(20f, c.maxGpsBearingAccDeg)
        assertEquals(HeadingMethod.GPS, c.headingMethod)
        // 前の版の保存だけ（移動のプリセットの項目なし）でも、前の版の設定として扱う（カスタム1。前からの決まり）
        assertEquals(TravelMode.CUSTOM1, SettingsCodec.decode(mapOf("sourceMode" to "HYBRID")).travelMode)
    }

    /** 20261121-D01: プリセットの値（方位の出し方・方位精度・GS・ETA）の読み込み。選べない値はその項目だけ自動車の値 */
    @Test
    fun presetHeadingAndAveragingValues() {
        fun c1(vararg kv: Pair<String, String>) = SettingsCodec.decode(mapOf("travelMode" to "CUSTOM1", *kv))
        val ok = c1("custom1.headingMethod" to "GPS_TRACK", "custom1.maxBearingAccDeg" to "45", "custom1.gsAverageSec" to "10", "custom1.etaAverageSec" to "120")
        assertEquals(HeadingMethod.GPS_TRACK, ok.headingMethod)
        assertEquals(45f, ok.maxGpsBearingAccDeg)
        assertEquals(10, ok.gsAverageSec)
        assertEquals(120, ok.etaAverageSec)
        val bad = c1("custom1.headingMethod" to "COMPASS", "custom1.maxBearingAccDeg" to "95", "custom1.gsAverageSec" to "4", "custom1.etaAverageSec" to "45")
        assertEquals(HeadingMethod.GPS, bad.headingMethod)
        assertEquals(20f, bad.maxGpsBearingAccDeg)
        assertEquals(1, bad.gsAverageSec)
        assertEquals(10, bad.etaAverageSec)
        assertEquals(listOf(1, 3, 5, 10), NavSettings.GS_AVERAGE_CHOICES_SEC)
        assertEquals(listOf(10, 20, 30, 60, 120), NavSettings.ETA_AVERAGE_CHOICES_SEC)
        // 歩行は保存に何があっても歩行の値
        val w = SettingsCodec.decode(mapOf("travelMode" to "WALK", "custom1.maxBearingAccDeg" to "45"))
        assertEquals(ReachProfile.WALK, w.reachProfile)
    }

    /** 20261121-D01: 到着半径の選択肢に 10・15・20 m を足した（自動車は 30 m のまま） */
    @Test
    fun arrivalRadiusChoices() {
        assertEquals(listOf(10.0, 15.0, 20.0, 30.0, 50.0, 100.0, 200.0, 500.0), NavSettings.REACH_RADIUS_CHOICES_M)
        assertEquals(30.0, NavSettings().reachRadiusM, 0.0)
        for (r in listOf(10.0, 15.0, 20.0)) {
            assertEquals(r, SettingsCodec.decode(mapOf("travelMode" to "CUSTOM1", "custom1.reachRadiusM" to r.toString())).reachRadiusM, 0.0)
        }
        // 選択肢にない値は自動車の値
        assertEquals(30.0, SettingsCodec.decode(mapOf("travelMode" to "CUSTOM1", "custom1.reachRadiusM" to "12.0")).reachRadiusM, 0.0)
    }
}
