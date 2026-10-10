package io.github.eightbrows.navhud.core

import io.github.eightbrows.navhud.core.nav.NavSettings
import io.github.eightbrows.navhud.core.nav.OwnshipPosition

/** テスト用の設定。 */
object TestSettings {
    /**
     * 20261010-D02 までの初期値のうち、縮尺と RATE に関わるもの（使う段 100m〜20km、起動時 1km、AUTO の上限 1km の段、
     * ARC の自機の位置 高め、HUD に描く WP の数 15、RATE 60 秒）。
     * D03 で初期値を変えたあとも、AUTO 縮尺・RATE の計算の決まりを、前と同じ値で確かめるために使う。
     */
    val BEFORE_D03 = NavSettings(
        rangeStepsKm = listOf(0.1, 0.2, 0.5, 1.0, 2.0, 5.0, 10.0, 20.0),
        initialRangeKm = 1.0,
        autoMaxRangeKm = 1.0,
        ownshipPosition = OwnshipPosition.HIGH,
        hudWpCount = 15,
        rateWindowSec = 60,
    )
}
