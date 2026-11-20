package org.carbridge.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.sin
import kotlinx.coroutines.launch
import org.carbridge.Job
import org.carbridge.JobStep
import org.carbridge.R

@Composable
internal fun JobBar(job: Job, waiting: Int) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                job.headline(LocalContext.current, waiting),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                job.stepText(LocalContext.current),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            // Android's install prompt is the toll gate: the car waits there until the user confirms
            val stage = when {
                job.step == JobStep.Installed.ordinal -> Crossing.Arrived
                job.step == JobStep.Confirm.ordinal && (job.update || job.silent) -> Crossing.GateOpen
                job.step == JobStep.Confirm.ordinal -> Crossing.AtGate
                else -> Crossing.Driving
            }
            BridgeCrossing(job.icon, job.progress, stage, Modifier.padding(top = 10.dp))
        }
    }
}

@Composable
internal fun BackgroundBar(label: String) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        shadowElevation = 8.dp,
    ) {
        Row(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 3.dp)
            Spacer(Modifier.width(14.dp))
            Text(
                stringResource(R.string.background_update, label),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Progress as a crossing: a little guide-green car carries the app's icon over a suspension
 * bridge toward the car. The deck it has covered turns green; a gate at the far end stays down
 * until the install goes ahead, then the car drives through and off the bridge.
 */
@Composable
private fun BridgeCrossing(icon: ImageBitmap, progress: Float, stage: Crossing, modifier: Modifier = Modifier) {
    val atGate = stage == Crossing.AtGate
    val signs = LocalSigns.current
    val structure = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val road = MaterialTheme.colorScheme.outlineVariant
    val covered = MaterialTheme.colorScheme.primary
    val post = MaterialTheme.colorScheme.onSurfaceVariant

    // 0..1 is the deck up to the gate; past 1 the car goes through the gate and off the edge
    val target = when (stage) {
        Crossing.Driving -> progress.coerceIn(0f, 1f)
        Crossing.AtGate -> 1f
        Crossing.GateOpen -> 1.25f
        Crossing.Arrived -> 2f
    }
    val position by animateFloatAsState(target, tween(900, easing = FastOutSlowInEasing), label = "drive")
    val gateLift by animateFloatAsState(if (stage >= Crossing.GateOpen) 1f else 0f, tween(400), label = "gate")
    val idle = rememberInfiniteTransition(label = "idle")
    // A running engine: the car shivers a little even while it waits
    val bob by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(260), RepeatMode.Reverse), label = "bob")
    val blink by idle.animateFloat(0f, 1f, infiniteRepeatable(tween(450), RepeatMode.Reverse), label = "blink")
    val cargo = rememberIconPainter(icon)

    // Tap the car to honk: it hops, flashes its headlight and beeps in a sign-yellow bubble
    val honk = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val hit = remember { CarHit() }
    val measurer = rememberTextMeasurer()
    val beepStyle = MaterialTheme.typography.labelLarge.copy(color = signs.onCaution)
    val beepText = stringResource(R.string.honk)

    Canvas(
        // Clipped, so the car can drive off the right edge once the copy is installed
        modifier.fillMaxWidth().height(80.dp).clipToBounds()
            .pointerInput(Unit) {
                detectTapGestures { tap ->
                    if (!hit.rect.inflate(8.dp.toPx()).contains(tap)) return@detectTapGestures
                    haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                    scope.launch {
                        honk.snapTo(0.001f)
                        honk.animateTo(1f, tween(HONK_MS, easing = LinearEasing))
                        honk.snapTo(0f)
                    }
                }
            }
            .semantics {
            progressBarRangeInfo = ProgressBarRangeInfo(progress.coerceIn(0f, 1f), 0f..1f)
        },
    ) {
        val margin = 4.dp.toPx()
        val deckY = size.height * 0.74f
        val deckTop = deckY - 2.dp.toPx()
        val gateX = size.width - margin - 2.dp.toPx()

        // Bridge: two towers, a main cable sagging between them, side spans down to the banks
        val towerTop = 8.dp.toPx()
        val leftTower = size.width * 0.27f
        val rightTower = size.width * 0.73f
        val sag = deckY - 10.dp.toPx()
        val spans = listOf(
            Span(margin, deckY - 3.dp.toPx(), leftTower, towerTop, (deckY + towerTop) / 2),
            Span(leftTower, towerTop, rightTower, towerTop, 2 * sag - towerTop),
            Span(rightTower, towerTop, size.width - margin, deckY - 3.dp.toPx(), (deckY + towerTop) / 2),
        )
        val cableWidth = 1.5.dp.toPx()
        for (span in spans) {
            drawPath(span.path(), structure, style = Stroke(cableWidth, cap = StrokeCap.Round))
            var x = span.x0 + 10.dp.toPx()
            while (x < span.x1 - 4.dp.toPx()) {
                drawLine(structure, Offset(x, span.yAt(x)), Offset(x, deckTop), 1.dp.toPx())
                x += 10.dp.toPx()
            }
        }
        for (tower in listOf(leftTower, rightTower)) {
            drawLine(structure, Offset(tower, towerTop - 2.dp.toPx()), Offset(tower, size.height), 3.dp.toPx(), StrokeCap.Round)
        }

        // Car geometry (drawn on a 56×29 grid), so the green deck ends right under it and it stops
        // short of the gate arm
        val armLen = 22.dp.toPx()
        val unit = 1.1.dp.toPx()
        val carW = CAR_WIDTH * unit
        val travel = (gateX - armLen - 6.dp.toPx() - carW) - margin
        val beyond = size.width + 4.dp.toPx() - (margin + travel)
        val rear = margin + travel * position.coerceAtMost(1f) + beyond * (position - 1f).coerceAtLeast(0f)

        val deckWidth = 4.dp.toPx()
        drawLine(road, Offset(margin, deckY), Offset(size.width - margin, deckY), deckWidth, StrokeCap.Round)
        drawLine(covered, Offset(margin, deckY), Offset((rear + carW / 2).coerceAtMost(size.width - margin), deckY), deckWidth, StrokeCap.Round)

        // Gate: a striped arm that swings up once installing no longer needs the user
        val postTop = deckTop - 22.dp.toPx()
        drawLine(post, Offset(gateX, deckTop), Offset(gateX, postTop), 2.dp.toPx(), StrokeCap.Round)
        rotate(degrees = 90f * gateLift, pivot = Offset(gateX, postTop + 3.dp.toPx())) {
            val armY = postTop + 3.dp.toPx()
            drawLine(signs.onCaution, Offset(gateX, armY), Offset(gateX - armLen, armY), 4.dp.toPx(), StrokeCap.Round)
            var stripe = gateX - 3.dp.toPx()
            while (stripe > gateX - armLen) {
                drawLine(signs.caution, Offset(stripe, armY), Offset(stripe - 3.dp.toPx(), armY), 4.dp.toPx())
                stripe -= 6.dp.toPx()
            }
        }

        // Wheels turn with the distance driven, so they stop when the car stops
        val wheelR = WHEEL_R * unit
        val turned = ((rear - margin) / wheelR) * (180f / PI.toFloat())
        val top = deckTop - CAR_HEIGHT * unit
        for (wheelX in WHEEL_XS) {
            val center = Offset(rear + wheelX * unit, top + WHEEL_Y * unit)
            drawCircle(Tire, wheelR, center)
            drawCircle(Hub, wheelR * 0.45f, center)
            rotate(turned, pivot = center) {
                drawLine(Hub, center - Offset(0f, wheelR * 0.9f), center - Offset(0f, wheelR * 0.45f), 1.2.dp.toPx())
            }
        }
        val honking = honk.value > 0f
        // A quick hop in the first third of the honk
        val hop = if (honking && honk.value < 0.3f) sin(PI.toFloat() * honk.value / 0.3f) * 6.dp.toPx() else 0f
        val shiver = bob * 0.8.dp.toPx() + hop
        hit.rect = Rect(rear, top - 22.dp.toPx(), rear + carW, deckTop)
        withTransform({
            translate(rear, top - shiver)
            scale(unit, unit, pivot = Offset.Zero)
        }) {
            drawPath(CarBody, signs.guide)
            drawPath(CarBody, signs.onGuide, style = Stroke(1.4f, join = StrokeJoin.Round))
            drawPath(CarWindow, signs.onGuideMuted)
            drawLine(signs.guide, Offset(24f, 6f), Offset(24f, 13f), 1.6f)
            drawLine(signs.onGuide.copy(alpha = 0.5f), Offset(24f, 14.5f), Offset(24f, 21f), 0.8f)
            // Headlight blinks while waiting at the gate; taillight stays on
            val flash = honking && honk.value < 0.5f
            val light = if (atGate && !flash) signs.caution.copy(alpha = 0.35f + 0.65f * blink) else signs.caution
            drawCircle(light, if (flash) 2.6f else 1.8f, Offset(53.2f, 17f))
            drawRoundRect(TailLight, Offset(1.6f, 15f), Size(1.8f, 3.2f), CornerRadius(0.8f))
            // The app rides on a roof rack
            drawLine(Hub, Offset(15f, 3.6f), Offset(33f, 3.6f), 1.2f, StrokeCap.Round)
            drawLine(Hub, Offset(17f, 3.6f), Offset(17f, 4.8f), 1f)
            drawLine(Hub, Offset(31f, 3.6f), Offset(31f, 4.8f), 1f)
            val icon = 20.dp.toPx() / unit
            translate(left = 24f - icon / 2, top = 3f - icon) {
                with(cargo) { draw(Size(icon, icon)) }
            }
        }
        if (honking) drawBeep(measurer.measure(beepText, beepStyle), hit.rect, honk.value, signs.caution)
    }
}

/** The honk's speech bubble beside the car's front, or behind it when the front is near the edge. */
private fun DrawScope.drawBeep(text: TextLayoutResult, car: Rect, t: Float, color: Color) {
    val padX = 8.dp.toPx()
    val padY = 3.dp.toPx()
    val w = text.size.width + 2 * padX
    val h = text.size.height + 2 * padY
    val tail = 5.dp.toPx()
    val toRight = car.right + w + 2.dp.toPx() < size.width
    val left = if (toRight) car.right - 6.dp.toPx() else car.left - w + 6.dp.toPx()
    val top = (car.top - h + 10.dp.toPx()).coerceAtLeast(0f)
    val alpha = if (t > 0.8f) (1f - t) / 0.2f else 1f
    drawRoundRect(color.copy(alpha = alpha), Offset(left, top), Size(w, h), CornerRadius(h / 2))
    val tailX = if (toRight) left + 8.dp.toPx() else left + w - 8.dp.toPx()
    drawPath(
        Path().apply {
            moveTo(tailX - tail / 2, top + h - 1f)
            lineTo(tailX + tail / 2, top + h - 1f)
            lineTo(tailX + (if (toRight) -tail else tail), top + h + tail)
            close()
        },
        color.copy(alpha = alpha),
    )
    drawText(text, topLeft = Offset(left + padX, top + padY), alpha = alpha)
}

/** Where the car was last drawn, for hit-testing taps; plain, so drawing doesn't recompose. */
private class CarHit {
    var rect = Rect.Zero
}

private const val HONK_MS = 1100

/** Where the car is: on the deck, stopped at the closed gate, through the open gate, or gone. */
private enum class Crossing { Driving, AtGate, GateOpen, Arrived }

private const val CAR_WIDTH = 56f
private const val CAR_HEIGHT = 29f
private const val WHEEL_R = 5f
private const val WHEEL_Y = 24f
private val WHEEL_XS = listOf(14f, 43f)
private val TailLight = Color(0xFFE53935)

/** A rounded little hatchback facing right, on the 56×29 car grid; wheel arches are cut out. */
private val CarBody: Path by lazy {
    Path().apply {
        moveTo(4f, 24f)
        lineTo(7.5f, 24f)
        arcTo(Rect(7.5f, 17.5f, 20.5f, 30.5f), 180f, 180f, false)
        lineTo(36.5f, 24f)
        arcTo(Rect(36.5f, 17.5f, 49.5f, 30.5f), 180f, 180f, false)
        lineTo(52.5f, 24f)
        quadraticTo(55f, 24f, 55f, 21f)
        lineTo(55f, 18f)
        quadraticTo(55f, 14.8f, 51.5f, 14.4f)
        lineTo(41f, 13f)
        lineTo(35.2f, 6.2f)
        quadraticTo(33.8f, 4.5f, 31f, 4.5f)
        lineTo(18f, 4.5f)
        quadraticTo(14.5f, 4.5f, 12.5f, 7.5f)
        lineTo(8.5f, 13f)
        quadraticTo(2f, 13.4f, 1.5f, 17f)
        lineTo(1.5f, 21f)
        quadraticTo(1.5f, 24f, 4f, 24f)
        close()
    }
}

private val CarWindow: Path by lazy {
    Path().apply {
        moveTo(11.8f, 12.4f)
        lineTo(15f, 7.6f)
        quadraticTo(15.9f, 6.5f, 17.6f, 6.5f)
        lineTo(30.6f, 6.5f)
        quadraticTo(32.2f, 6.5f, 33.2f, 7.6f)
        lineTo(37.6f, 12.4f)
        close()
    }
}

// Tires stay dark in both themes, like real ones
private val Tire = Color(0xFF1E2321)
private val Hub = Color(0xFF9AA39E)

/** One cable of the bridge: a quadratic curve from (x0, y0) to (x1, y1) bending toward [controlY]. */
private class Span(val x0: Float, val y0: Float, val x1: Float, val y1: Float, val controlY: Float) {
    fun path() = Path().apply {
        moveTo(x0, y0)
        quadraticTo((x0 + x1) / 2, controlY, x1, y1)
    }

    // x moves linearly with t because the control point sits halfway across
    fun yAt(x: Float): Float {
        val t = (x - x0) / (x1 - x0)
        return (1 - t) * (1 - t) * y0 + 2 * t * (1 - t) * controlY + t * t * y1
    }
}

@Composable
private fun rememberIconPainter(icon: ImageBitmap) = remember(icon) { BitmapPainter(icon) }
