package ru.hdlabs.treex.ui.background

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.currentStateAsState
import ru.hdlabs.treex.ui.theme.HdBlack
import ru.hdlabs.treex.ui.theme.HdBlue
import ru.hdlabs.treex.ui.theme.HdOrange
import ru.hdlabs.treex.ui.theme.LocalHdPalette
import kotlin.math.sin
import kotlin.random.Random

private data class Star(val dirX: Float, val dirY: Float, val depthOffset: Float, val speed: Float, val radius: Float, val alpha: Float)
private data class Meteor(val startX: Float, val startY: Float, val vx: Float, val vy: Float, val offset: Float, val trail: Float)
private data class Pulsar(val x: Float, val y: Float, val tint: Color, val speed: Float, val offset: Float)

@Composable
fun SpaceBackground(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val reduceMotion = remember {
        runCatching {
            android.provider.Settings.Global.getFloat(
                context.contentResolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
            ) == 0f
        }.getOrDefault(false)
    }
    val animEnabled = lifecycleState.isAtLeast(Lifecycle.State.RESUMED) && !reduceMotion
    val stars = remember {
        val random = Random(42)
        List(220) {
            Star(
                dirX = random.nextFloat() * 2f - 1f,
                dirY = random.nextFloat() * 2f - 1f,
                depthOffset = random.nextFloat(),
                speed = if (random.nextBoolean()) 1f else 1.6f,
                radius = random.nextFloat() * 1.3f + 0.4f,
                alpha = random.nextFloat() * 0.45f + 0.45f,
            )
        }
    }
    val meteors = remember {
        val random = Random(11)
        List(3) {
            val left = random.nextBoolean()
            Meteor(
                startX = if (left) 0.75f else 0.15f,
                startY = random.nextFloat() * 0.25f,
                vx = if (left) -0.4f else 0.4f,
                vy = 0.38f,
                offset = random.nextFloat(),
                trail = 0.09f,
            )
        }
    }
    val pulsars = remember {
        listOf(
            Pulsar(0.2f, 0.18f, HdBlue, 2.4f, 0.1f),
            Pulsar(0.78f, 0.22f, HdOrange, 1.8f, 0.4f),
            Pulsar(0.62f, 0.72f, HdBlue, 2.1f, 0.7f),
        )
    }
    val transition = rememberInfiniteTransition(label = "space")
    val phase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(26000, easing = LinearEasing), RepeatMode.Restart),
        label = "phase",
    )
    val travelPhase = if (animEnabled) phase else 0.35f
    val palette = LocalHdPalette.current
    val background = HdBlack
    Canvas(modifier.fillMaxSize()) {
        drawRect(background)
        drawRect(
            Brush.radialGradient(
                listOf(palette.spaceGradientInner, background),
                center = Offset(size.width * 0.5f, size.height * 0.42f),
                radius = size.maxDimension * 0.72f,
            ),
        )
        if (!palette.useSpaceEffects) return@Canvas
        val vanishX = size.width * 0.5f
        val vanishY = size.height * 0.46f
        stars.forEach { star ->
            val travel = ((travelPhase * star.speed + star.depthOffset) % 1f + 1f) % 1f
            val depth = 0.08f + travel * travel * 0.92f
            drawCircle(
                color = Color.White.copy(alpha = star.alpha * (0.35f + depth * 0.65f)),
                radius = star.radius * (0.4f + depth),
                center = Offset(vanishX + star.dirX * size.width * 0.58f * depth, vanishY + star.dirY * size.height * 0.58f * depth),
            )
        }
        if (animEnabled) {
            meteors.forEach { meteor ->
                val local = ((phase + meteor.offset) % 1f)
                if (local < 0.18f) {
                    val t = local / 0.18f
                    val head = Offset((meteor.startX + meteor.vx * t) * size.width, (meteor.startY + meteor.vy * t) * size.height)
                    val tail = Offset(head.x - meteor.vx * meteor.trail * size.width, head.y - meteor.vy * meteor.trail * size.height)
                    drawLine(
                        brush = Brush.linearGradient(listOf(Color.Transparent, Color.White.copy(alpha = 0.8f)), tail, head),
                        start = tail,
                        end = head,
                        strokeWidth = 1.6f,
                    )
                }
            }
        }
        pulsars.forEach { pulsar ->
            val center = Offset(pulsar.x * size.width, pulsar.y * size.height)
            val wave = if (animEnabled) sin((phase * pulsar.speed + pulsar.offset) * 6.28318f) else 0.4f
            val flash = ((0.5f + 0.5f * wave).coerceIn(0f, 1f)).let { it * it }
            drawCircle(pulsar.tint.copy(alpha = 0.28f * flash), 5f + 10f * flash, center)
            drawCircle(Color.White.copy(alpha = 0.25f + 0.7f * flash), 1.4f, center)
            if (animEnabled) {
                val ring = ((phase * pulsar.speed + pulsar.offset) % 1f)
                if (ring < 0.3f) {
                    drawCircle(
                        color = pulsar.tint.copy(alpha = (1f - ring / 0.3f) * 0.7f),
                        radius = 3f + ring / 0.3f * 18f,
                        center = center,
                        style = Stroke(1.3f),
                    )
                }
            }
        }
    }
}
