package io.github.eightbrows.navhud.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import io.github.eightbrows.navhud.ui.HudColors

/**
 * アプリのテーマ: HudColors に合わせた固定の暗い配色。端末の壁紙の色（ダイナミックカラー）やライト / ダークの設定は使わない。
 * Material の部品（起動時のダイアログ、WP 設定の入力欄・確認のダイアログなど）の色がここで決まる。
 * 文字や枠の色は UI の色（白 / 緑 / 琥珀）に合わせる。
 */
@Composable
fun NavHUDTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = hudColorScheme(), content = content)
}

/** HudColors から作る配色（UI の色を変えたら作り直す）。 */
private fun hudColorScheme(): ColorScheme = darkColorScheme(
    primary = HudColors.Scale,
    onPrimary = HudColors.Background,
    secondary = HudColors.ScaleDim,
    onSecondary = HudColors.Background,
    tertiary = HudColors.Active,
    onTertiary = HudColors.Background,
    background = HudColors.Background,
    onBackground = HudColors.Scale,
    surface = HudColors.Background,
    onSurface = HudColors.Scale,
    surfaceVariant = HudColors.Frame,
    onSurfaceVariant = HudColors.Caption,
    surfaceContainerLowest = HudColors.Background,
    surfaceContainerLow = HudColors.Frame,
    surfaceContainer = HudColors.Frame,
    surfaceContainerHigh = HudColors.Frame,
    surfaceContainerHighest = HudColors.Frame,
    outline = HudColors.ScaleDim,
    outlineVariant = HudColors.Frame,
    error = HudColors.Warning,
    onError = HudColors.Background,
)
