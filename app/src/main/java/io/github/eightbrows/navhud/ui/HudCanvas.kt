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
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.sp
import io.github.eightbrows.navhud.core.nav.NavState
import io.github.eightbrows.navhud.core.view.HudMetrics
import io.github.eightbrows.navhud.core.view.HudRect
import io.github.eightbrows.navhud.core.view.HudScene
import io.github.eightbrows.navhud.core.view.HudSceneBuilder
import io.github.eightbrows.navhud.core.view.P

/** HUD の図。座標は core（HudSceneBuilder）で計算済みのものを描くだけ。 */
@Composable
fun HudCanvas(state: NavState, modifier: Modifier = Modifier) {
    val density = LocalDensity.current.density
    val metrics = remember(density) { HudMetrics().scaled(density) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val scene = remember(state, size, metrics) {
        if (size == IntSize.Zero) null
        else HudSceneBuilder.build(state, HudRect(0f, 0f, size.width.toFloat(), size.height.toFloat()), metrics)
    }
    val textMeasurer = rememberTextMeasurer()
    Canvas(
        modifier
            .clipToBounds()
            .onSizeChanged { size = it },
    ) {
        scene?.let { drawScene(it, textMeasurer, density) }
    }
}

private val LabelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)
private val SmallLabelStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 11.sp)

private fun P.o() = Offset(x, y)

private fun DrawScope.drawScene(scene: HudScene, tm: TextMeasurer, density: Float) {
    val thin = 1.2f * density
    val bold = 2.2f * density
    val dash = PathEffect.dashPathEffect(floatArrayOf(6f * density, 5f * density))

    drawRect(HudColors.Background)

    for (a in scene.arcs) {
        drawArc(
            color = HudColors.of(a.ink),
            // core の角度は「上が 0」、Canvas は「右が 0」
            startAngle = a.startDeg - 90f,
            sweepAngle = a.sweepDeg,
            useCenter = false,
            topLeft = Offset(a.center.x - a.radius, a.center.y - a.radius),
            size = Size(a.radius * 2, a.radius * 2),
            style = Stroke(thin),
        )
    }
    for (s in scene.segments) {
        drawLine(
            color = HudColors.of(s.ink),
            start = s.a.o(),
            end = s.b.o(),
            strokeWidth = if (s.bold) bold else thin,
            pathEffect = if (s.dashed) dash else null,
        )
    }
    for (l in scene.labels) drawLabel(tm, l.text, l.at, HudColors.of(l.ink), if (l.small) SmallLabelStyle else LabelStyle)

    for (w in scene.wpMarks) {
        val c = HudColors.of(w.ink)
        val r = 6f * density
        // WP はひし形
        val path = Path().apply {
            moveTo(w.at.x, w.at.y - r)
            lineTo(w.at.x + r, w.at.y)
            lineTo(w.at.x, w.at.y + r)
            lineTo(w.at.x - r, w.at.y)
            close()
        }
        drawPath(path, c, style = Stroke(bold, pathEffect = if (w.dashed) dash else null))
        drawLabel(tm, w.name, P(w.at.x, w.at.y - r - 10f * density), c, LabelStyle)
    }
    for (a in scene.arrows) {
        val c = HudColors.of(a.ink)
        triangle(a.at, a.angleDeg, 12f * density, c)
        drawLabel(tm, a.text, a.textAt, c, SmallLabelStyle)
    }
    for (p in scene.pointers) triangle(p.tip, p.angleDeg, p.sizePx, HudColors.of(p.ink), filled = false, stroke = bold)

    val own = scene.ownShip
    val oc = HudColors.of(own.ink)
    if (own.angleDeg == null) {
        drawCircle(oc, radius = 7f * density, center = own.at.o(), style = Stroke(bold))
    } else {
        // 自機: 先端が機首方位を向く三角
        rotate(own.angleDeg, pivot = own.at.o()) {
            val s = 11f * density
            val path = Path().apply {
                moveTo(own.at.x, own.at.y - s * 1.3f)
                lineTo(own.at.x + s * 0.8f, own.at.y + s * 0.8f)
                lineTo(own.at.x, own.at.y + s * 0.3f)
                lineTo(own.at.x - s * 0.8f, own.at.y + s * 0.8f)
                close()
            }
            drawPath(path, oc, style = Stroke(bold))
        }
    }
}

/** 先端 tip が angleDeg（上が 0、時計回り）を向く三角形。 */
private fun DrawScope.triangle(tip: P, angleDeg: Float, size: Float, color: Color, filled: Boolean = true, stroke: Float = 0f) {
    rotate(angleDeg, pivot = tip.o()) {
        val path = Path().apply {
            moveTo(tip.x, tip.y)
            lineTo(tip.x + size * 0.6f, tip.y + size)
            lineTo(tip.x - size * 0.6f, tip.y + size)
            close()
        }
        if (filled) drawPath(path, color) else drawPath(path, color, style = Stroke(stroke))
    }
}

/** 文字の中心を at に合わせて描く。 */
private fun DrawScope.drawLabel(tm: TextMeasurer, text: String, at: P, color: Color, style: TextStyle) {
    val layout = tm.measure(text, style.copy(color = color))
    drawText(layout, topLeft = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f))
}
