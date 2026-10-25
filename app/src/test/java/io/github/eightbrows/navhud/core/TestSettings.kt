package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.nav.DisplayMode
import io.github.eightbrows.navhud.core.nav.LandmarkItem
import io.github.eightbrows.navhud.core.nav.MapItem
import io.github.eightbrows.navhud.core.nav.MapRange
import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition

/** テスト用の設定。 */
object TestSettings {
    /**
     * 20261010-D02 までの初期値のうち、縮尺と RATE に関わるもの（使う段 100m〜20km、起動時 1km、AUTO の広域の限度 1km の段、
     * ARC の自機の位置 高め、HUD に描く WP の数 15、RATE 60 秒）。表示モードはそのころの初期値の ARC（20261025-D01 で初期値が North Up になったので書いた）。
     * D03 で初期値を変えたあとも、AUTO 縮尺・RATE の計算の決まりを、前と同じ値で確かめるために使う。
     */
    val BEFORE_D03 = NavSettings(
        displayMode = DisplayMode.ARC,
        rangeStepsKm = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0),
        initialRangeKm = 1.0,
        autoMaxRangeKm = 1.0,
        ownshipPosition = OwnshipPosition.HIGH,
        hudWpCount = 15,
        rateWindowSec = 60,
    )

    /** 20261024-D09 までの背景地図の項目の初期値（高速道路・国道は「〜100k」、ほかは OFF） */
    val MAP_ITEMS_BEFORE_1025_D01: Map<MapItem, MapRange> = MapItem.entries.associateWith {
        if (it in setOf(MapItem.MOTORWAY, MapItem.NATIONAL_ROAD)) MapRange.UPTO_100KM else MapRange.OFF
    }

    /** 20261024-D09 までのランドマークの初期値（空港の敷地「〜10k」、空港の略号「〜25k」、ほかは「〜100k」） */
    val LANDMARKS_BEFORE_1025_D01: Map<LandmarkItem, MapRange> = LandmarkItem.entries.associateWith {
        when (it) {
            LandmarkItem.AIRPORT_AREA -> MapRange.UPTO_10KM
            LandmarkItem.AIRPORT_CODE -> MapRange.UPTO_25KM
            else -> MapRange.UPTO_100KM
        }
    }

    /**
     * 20261024-D09 までの初期値（20261025-D01 で実機の設定を初期値にした。変わった項目だけ前の値に戻す）。
     * 初期値を「ふつうの設定」として使っていた動きのテストを、前と同じ値で確かめるために使う。
     */
    val BEFORE_1025_D01 = NavSettings(
        displayMode = DisplayMode.ARC,
        rangeStepsKm = listOf(0.05, 0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0, 50.0),
        ownshipPosition = OwnshipPosition.HIGHER,
        mapItems = MAP_ITEMS_BEFORE_1025_D01,
        landmarks = LANDMARKS_BEFORE_1025_D01,
        landmarkBrightness = 4,
        mapLineWidth = 4,
        mapLineBrightness = 4,
        trackBrightnessPct = 75,
    )
}
