package io.github.eightbrows.navhud.ui

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.Tuning
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.nav.ProfileSize
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.P
import io.github.eightbrows.navhud.core.view.ProfileBuilder
import io.github.eightbrows.navhud.core.view.ProfileMetrics

private val AxisStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = Tuning.PROFILE_TEXT_SP.sp)
private val NameStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = Tuning.PROFILE_TEXT_SP.sp)

/** 標高プロファイル（§6.6）。座標は core（ProfileBuilder）で計算済みのものを描くだけ。 */
@Composable
fun ProfileView(state: NavState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val metrics = remember(density) { ProfileMetrics().scaled(density) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    // 中・大では WP の名前も描く（小は高さが足りない）
    val showNames = state.settings.profileSize == ProfileSize.MEDIUM || state.settings.profileSize == ProfileSize.LARGE
    val scene = remember(state, size, metrics, showNames) {
        if (size == IntSize.Zero) null
        else ProfileBuilder.build(state, HudRect(0f, 0f, size.width.toFloat(), size.height.toFloat()), metrics, showNames)
    }
    val tm = rememberTextMeasurer()
    Canvas(modifier.clipToBounds().onSizeChanged { size = it }) {
        drawRect(HudColors.Background)
        val sc = scene ?: return@Canvas
        val dash = PathEffect.dashPathEffect(floatArrayOf(5f * density, 4f * density))
        for (s in sc.segments) {
            drawLine(
                HudColors.of(s.ink), Offset(s.a.x, s.a.y), Offset(s.b.x, s.b.y),
                strokeWidth = Tuning.PROFILE_LINE_DP * density,
                pathEffect = if (s.dashed) dash else null,
            )
        }
        for (p in sc.points) {
            val c = HudColors.of(p.ink)
            drawCircle(c, radius = Tuning.PROFILE_POINT_RADIUS_DP * density, center = Offset(p.at.x, p.at.y))
            p.name?.let { drawLabel(tm, it, P(p.at.x, p.at.y - 10f * density), c, NameStyle) }
        }
        for (l in sc.labels) drawLabel(tm, l.text, l.at, HudColors.of(l.ink), AxisStyle)
    }
}
