package dev.nikhil.cmfsocialremote.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.nikhil.cmfsocialremote.model.GestureEvent
import dev.nikhil.cmfsocialremote.model.RemoteGesture
import dev.nikhil.cmfsocialremote.model.WatchInput
import dev.nikhil.cmfsocialremote.ui.theme.Cmf

/**
 * The circular gesture controller. It mirrors what the watch does and doubles as a test pad:
 * swipe the dial up / down or double-tap it to send the same commands the watch would.
 */
@Composable
fun GestureDial(
    event: GestureEvent?,
    onTest: (WatchInput) -> Unit,
    modifier: Modifier = Modifier,
) {
    val test by rememberUpdatedState(onTest)
    val pulse = remember { Animatable(0f) }
    var pulseKind by remember { mutableStateOf<RemoteGesture?>(null) }

    LaunchedEffect(event?.id) {
        if (event != null) {
            pulseKind = event.gesture
            pulse.snapTo(1f)
            pulse.animateTo(0f, tween(700))
        }
    }

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(12_000, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )

    BoxWithConstraints(
        modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                detectTapGestures(onDoubleTap = { test(WatchInput.DoubleTap) })
            }
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = {
                        if (total < -80f) test(WatchInput.Up) else if (total > 80f) test(WatchInput.Down)
                    },
                    onVerticalDrag = { _, dy -> total += dy },
                )
            },
    ) {
        val unit = maxWidth.value / 100f
        val kind = pulseKind

        Canvas(Modifier.fillMaxSize()) {
            val c = center
            val r = size.minDimension / 2f * 0.96f
            val ring = Brush.sweepGradient(listOf(Cmf.Orange, Cmf.Pink, Cmf.Violet, Cmf.Orange), center = c)

            // Soft glow: wider, fainter strokes under the crisp ring.
            rotate(angle, pivot = c) {
                drawCircle(ring, r, c, alpha = 0.10f, style = Stroke(width = 22.dp.toPx()))
                drawCircle(ring, r, c, alpha = 0.18f, style = Stroke(width = 11.dp.toPx()))
                drawCircle(ring, r, c, style = Stroke(width = 2.5.dp.toPx()))
            }
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF16121A), Cmf.Black), center = c, radius = r),
                r * 0.985f,
                c,
            )

            // Centre pad
            val pad = r * 0.36f
            drawCircle(
                Brush.radialGradient(listOf(Cmf.Pink.copy(alpha = 0.30f), Color.Transparent), c, pad * 1.5f),
                pad * 1.5f,
                c,
            )
            drawCircle(Cmf.Pink.copy(alpha = 0.55f), pad, c, style = Stroke(1.5.dp.toPx()))
            drawHeart(Offset(c.x, c.y - pad * 0.28f), pad * 0.34f, Cmf.Pink)

            // Chevrons
            drawChevron(Offset(c.x, c.y - r * 0.78f), r * 0.07f, up = true)
            drawChevron(Offset(c.x, c.y + r * 0.74f), r * 0.07f, up = false)

            // Feedback pulse where the last gesture happened
            val p = pulse.value
            if (p > 0f && kind != null) {
                val spot = when (kind) {
                    RemoteGesture.SWIPE_UP -> Offset(c.x, c.y - r * 0.62f)
                    RemoteGesture.SWIPE_DOWN -> Offset(c.x, c.y + r * 0.62f)
                    else -> c
                }
                val color = if (kind == RemoteGesture.DOUBLE_TAP) Cmf.Pink else Cmf.Orange
                val radius = r * (if (kind == RemoteGesture.DOUBLE_TAP) 0.62f else 0.45f)
                drawCircle(
                    Brush.radialGradient(listOf(color.copy(alpha = 0.55f * p), Color.Transparent), spot, radius),
                    radius,
                    spot,
                )
            }
        }

        // Labels
        Column(
            Modifier.align(Alignment.TopCenter).padding(top = (unit * 15).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Swipe Up", color = Cmf.Text, fontWeight = FontWeight.SemiBold, fontSize = (unit * 6.2f).sp)
            Text("Next", color = Cmf.TextDim, fontSize = (unit * 5f).sp)
        }
        Column(
            Modifier.align(Alignment.Center).padding(top = (unit * 25).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                "Double Tap",
                color = Cmf.Text,
                fontWeight = FontWeight.SemiBold,
                fontSize = (unit * 5.8f).sp,
                textAlign = TextAlign.Center,
            )
            Text("Like / Unlike", color = Cmf.TextDim, fontSize = (unit * 4.6f).sp)
        }
        Column(
            Modifier.align(Alignment.BottomCenter).padding(bottom = (unit * 12).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Swipe Down", color = Cmf.Text, fontWeight = FontWeight.SemiBold, fontSize = (unit * 6.2f).sp)
            Text("Previous", color = Cmf.TextDim, fontSize = (unit * 5f).sp)
        }
    }
}

private fun DrawScope.drawHeart(c: Offset, s: Float, color: Color) {
    val path = Path().apply {
        moveTo(c.x, c.y + s * 0.95f)
        cubicTo(c.x - s * 1.7f, c.y - s * 0.05f, c.x - s * 0.95f, c.y - s * 1.25f, c.x, c.y - s * 0.45f)
        cubicTo(c.x + s * 0.95f, c.y - s * 1.25f, c.x + s * 1.7f, c.y - s * 0.05f, c.x, c.y + s * 0.95f)
        close()
    }
    drawPath(path, Brush.verticalGradient(listOf(Color(0xFFFF6B9D), color), c.y - s, c.y + s))
}

private fun DrawScope.drawChevron(c: Offset, s: Float, up: Boolean) {
    val dir = if (up) -1f else 1f
    for (i in 0..1) {
        val y = c.y + dir * i * s * 0.9f
        val path = Path().apply {
            moveTo(c.x - s * 1.4f, y - dir * s * 0.5f)
            lineTo(c.x, y + dir * s * 0.5f)
            lineTo(c.x + s * 1.4f, y - dir * s * 0.5f)
        }
        drawPath(
            path,
            Cmf.Text.copy(alpha = if (i == 0) 1f else 0.55f),
            style = Stroke(2.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
