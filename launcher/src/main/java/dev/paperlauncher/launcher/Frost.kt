package dev.paperlauncher.launcher

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.PowerManager
import android.provider.Settings
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

private const val HOLD_LIMIT_MS = 2500L
private const val CLEAR_MS = 320
private const val FLAT_DEGREES = 170f

/** Listens only while a frost transition is running. Nothing runs when the launcher is idle or hidden. */
private suspend fun awaitFlat(context: Context) {
    val sensors = context.getSystemService(SensorManager::class.java)
    val hinge = sensors.getDefaultSensor(Sensor.TYPE_HINGE_ANGLE) ?: return
    callbackFlow {
        val listener = object : SensorEventListener {
            override fun onSensorChanged(e: SensorEvent) { trySend(e.values[0]) }
            override fun onAccuracyChanged(s: Sensor, accuracy: Int) = Unit
        }
        sensors.registerListener(listener, hinge, SensorManager.SENSOR_DELAY_NORMAL)
        awaitClose { sensors.unregisterListener(listener) }
    }.first { it >= FLAT_DEGREES }
}

private fun effectsAllowed(context: Context): Boolean {
    val power = context.getSystemService(PowerManager::class.java)
    val animations = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f)
    return !power.isPowerSaveMode && animations > 0f && power.currentThermalStatus < PowerManager.THERMAL_STATUS_SEVERE
}

/** Frost starts when the window grows from cover to inner, holds until the hinge reads flat, then clears right to left. */
@Composable
fun rememberFrostModifier(expanded: Boolean, userEnabled: Boolean, paper: Color, wash: Color): Modifier {
    val context = LocalContext.current
    val memory = remember { booleanArrayOf(expanded) }
    var level by remember { mutableFloatStateOf(0f) }
    val grew = expanded && !memory[0]
    memory[0] = expanded
    if (grew && userEnabled && effectsAllowed(context)) level = 1f
    if (!expanded) level = 0f

    LaunchedEffect(expanded) {
        if (level > 0f) {
            withTimeoutOrNull(HOLD_LIMIT_MS) { awaitFlat(context) }
            animate(level, 0f, animationSpec = tween(CLEAR_MS, easing = LinearEasing)) { value, _ -> level = value }
        }
    }

    val layer = rememberGraphicsLayer()
    return Modifier.drawWithContent {
        drawContent()
        val p = level
        if (p > 0f) {
            val blur = 14.dp.toPx() * (0.4f + 0.6f * p)
            layer.renderEffect = BlurEffect(blur, blur, TileMode.Clamp)
            layer.record { this@drawWithContent.drawContent() }
            val width = size.width
            val edge = width * (-0.18f + 0.73f * p)
            drawIntoCanvas { canvas ->
                canvas.saveLayer(Rect(0f, 0f, width, size.height), Paint())
                drawRect(paper)
                drawLayer(layer)
                drawRect(wash.copy(alpha = 0.45f))
                drawRect(
                    Brush.horizontalGradient(
                        0f to Color.Black,
                        1f to Color.Transparent,
                        startX = edge,
                        endX = edge + width * 0.18f,
                    ),
                    blendMode = BlendMode.DstIn,
                )
                canvas.restore()
            }
        }
    }
}
