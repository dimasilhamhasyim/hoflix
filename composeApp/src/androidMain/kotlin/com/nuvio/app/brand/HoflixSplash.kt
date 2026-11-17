package com.nuvio.app.brand

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min
import kotlin.math.tan

/**
 * Opening splash derived from the HOFLIX brand splash: red wordmark letters
 * rise in one by one over a soft red ambient glow while a gloss band sweeps
 * across the letters, then the whole mark fades out and the app is revealed.
 */
@Composable
internal fun HoflixBrandHost(content: @Composable () -> Unit) {
    var showSplash by remember { mutableStateOf(!HoflixSplashState.played) }
    Box(modifier = Modifier.fillMaxSize()) {
        content()
        if (showSplash) {
            HoflixSplash(onFinished = {
                HoflixSplashState.played = true
                showSplash = false
            })
        }
    }
}

private object HoflixSplashState {
    var played: Boolean = false
}

private const val SPLASH_SECONDS = 3.0f
private const val FADE_START = 2.72f
private const val FADE_LENGTH = 0.28f

private const val VIEW_LEFT = 100f
private const val VIEW_TOP = 660f
private const val VIEW_WIDTH = 1750f
private const val VIEW_HEIGHT = 600f

private val LETTER_DATA = listOf(
    "M146 717 H253 V903 H406 V717 H512 V1175 L406 1185 V1000 H253 V1201 L146 1216 Z",
    "M542 1006 A179 172 0 1 1 900 1006 A179 172 0 1 1 542 1006 Z " +
        "M799 1005 A79 84 0 1 0 641 1005 A79 84 0 1 0 799 1005 Z",
    "M943 1153 V927 H900 V844 H943 V825 C943 750 990 706 1070 703 L1116 702 V786 " +
        "L1085 786 C1055 788 1042 805 1042 835 V844 H1113 V927 H1042 V1153 Z",
    "M1142 702 H1242 V1160 L1142 1155 Z",
    "M1301 702 H1400 V802 H1301 Z M1301 844 H1400 V1170 L1301 1162 Z",
    "M1427 844 H1543 L1603 928 L1664 844 H1780 L1657 1008 L1797 1213 L1670 1195 " +
        "L1597 1092 L1532 1181 L1423 1171 L1543 1008 Z",
)

private val letterPaths: List<Path> by lazy {
    LETTER_DATA.mapIndexed { index, data ->
        PathParser().parsePathString(data).toPath().also { path ->
            if (index == 1) path.fillType = PathFillType.EvenOdd
        }
    }
}

private val letterBounds: List<Rect> by lazy { letterPaths.map { it.getBounds() } }

@Composable
private fun HoflixSplash(onFinished: () -> Unit) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        clock.animateTo(1f, tween(durationMillis = (SPLASH_SECONDS * 1000).toInt(), easing = LinearEasing))
        onFinished()
    }
    val seconds = clock.value * SPLASH_SECONDS
    val fade = if (seconds <= FADE_START) {
        1f
    } else {
        1f - ((seconds - FADE_START) / FADE_LENGTH).coerceIn(0f, 1f)
    }
    Canvas(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        drawAmbient(seconds, fade)
        if (fade > 0f) drawWordmark(seconds, fade)
    }
}

private fun DrawScope.drawAmbient(seconds: Float, fade: Float) {
    val rise = easeOut(((seconds - 0.4f) / 1.0f).coerceIn(0f, 1f)) * fade
    if (rise <= 0f) return
    val radius = size.minDimension * 0.85f
    drawCircle(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xFFE50914).copy(alpha = 0.22f * rise), Color.Transparent),
            center = center,
            radius = radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.drawWordmark(seconds: Float, fade: Float) {
    val scale = min(
        size.width * 0.86f / VIEW_WIDTH,
        size.height * 0.52f / VIEW_HEIGHT,
    )
    val originX = (size.width - VIEW_WIDTH * scale) / 2f - VIEW_LEFT * scale
    val originY = (size.height - VIEW_HEIGHT * scale) / 2f - VIEW_TOP * scale

    val fill = Brush.linearGradient(
        colors = listOf(Color(0xFFD70A20), Color(0xFFC5091D), Color(0xFFCF0820)),
        start = Offset(146f, 702f),
        end = Offset(1797f, 1217f),
    )

    withTransform({
        translate(originX, originY)
        scale(scale, scale, Offset.Zero)
    }) {
        letterPaths.forEachIndexed { index, path ->
            val start = 0.4f + index * 0.12f
            val progress = easeOut(((seconds - start) / 0.8f).coerceIn(0f, 1f))
            if (progress <= 0f) return@forEachIndexed
            val bounds = letterBounds[index]
            val letterScale = 1.25f - 0.25f * progress
            val drift = 14f * (1f - progress)
            withTransform({
                translate(bounds.center.x, bounds.center.y + drift)
                scale(letterScale, letterScale, Offset.Zero)
                translate(-bounds.center.x, -bounds.center.y)
            }) {
                drawPath(path, fill, alpha = progress * fade)
            }
        }

        val glossProgress = ((seconds - 1.7f) / 1.3f).coerceIn(0f, 1f)
        if (glossProgress > 0f && glossProgress < 1f) {
            val bandX = -100f + glossProgress * 2600f
            val glossPath = glossBand(bandX)
            val glossBrush = Brush.linearGradient(
                colors = listOf(Color.Transparent, Color.White.copy(alpha = 0.75f), Color.Transparent),
                start = Offset(bandX + 1000f * -0.364f, 1000f),
                end = Offset(bandX + 240f + 1000f * -0.364f, 1000f),
            )
            letterPaths.forEach { path ->
                clipPath(path) {
                    drawPath(glossPath, glossBrush, alpha = fade)
                }
            }
        }
    }
}

private fun glossBand(startX: Float): Path {
    val skew = tan(Math.toRadians(-20.0)).toFloat()
    val top = 640f
    val bottom = 1340f
    val width = 240f
    return Path().apply {
        moveTo(startX + top * skew, top)
        lineTo(startX + width + top * skew, top)
        lineTo(startX + width + bottom * skew, bottom)
        lineTo(startX + bottom * skew, bottom)
        close()
    }
}

private fun easeOut(value: Float): Float {
    val inverse = 1f - value
    return 1f - inverse * inverse * inverse
}
