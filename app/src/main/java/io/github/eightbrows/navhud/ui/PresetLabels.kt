package io.github.eightbrows.navhud.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import io.github.eightbrows.navhud.R
import io.github.eightbrows.navhud.core.nav.TravelMode

/**
 * 移動のプリセットの名前と色（20261121-D05 で設定画面から移した。設定画面の帯と、メイン画面の上部バーの左で同じものを使う）
 */

/** 移動のプリセットの名前（自動車 / 歩行 / カスタム1〜3。英語は Car / Walk / Custom 1〜3） */
@Composable
internal fun presetName(m: TravelMode): String = stringResource(
    when (m) {
        TravelMode.CAR -> R.string.travel_car
        TravelMode.WALK -> R.string.travel_walk
        TravelMode.CUSTOM1 -> R.string.travel_custom1
        TravelMode.CUSTOM2 -> R.string.travel_custom2
        TravelMode.CUSTOM3 -> R.string.travel_custom3
    },
)

/** 移動のプリセットの色（20261121-D03。自動車 水色・歩行 緑・カスタム 紫） */
internal fun presetColor(m: TravelMode): Color = when (m) {
    TravelMode.CAR -> HudColors.PresetCar
    TravelMode.WALK -> HudColors.PresetWalk
    else -> HudColors.PresetCustom
}
