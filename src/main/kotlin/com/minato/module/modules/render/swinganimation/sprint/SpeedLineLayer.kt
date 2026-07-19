@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.sprint

import com.minato.Minato.mc
import net.minecraft.client.gui.DrawContext
import net.minecraft.util.math.random.Random
import java.awt.Color
import kotlin.math.cos
import kotlin.math.sin

/**
 * SpeedLineLayer — vạch tốc độ ở rìa màn hình khi sprint.
 */
object SpeedLineLayer {

    data class SpeedLine(
        var x: Float,
        var y: Float,
        var length: Float,
        var angle: Float,
        var alpha: Float,
        var lifetime: Int,
    )

    private val lines = mutableListOf<SpeedLine>()
    private val random = Random.create()
    private var spawnCounter = 0
    private var targetAlpha = 0f
    private var currentAlpha = 0f

    fun reset() {
        lines.clear()
        spawnCounter = 0
        targetAlpha = 0f
        currentAlpha = 0f
    }

    fun tick(intensity: Float, maxLines: Int, enabled: Boolean) {
        targetAlpha = if (enabled && intensity > 0.3f) {
            (intensity * 0.25f).coerceAtMost(0.3f)
        } else {
            0f
        }

        currentAlpha += (targetAlpha - currentAlpha) * 0.15f
        if (currentAlpha < 0.001f) {
            lines.clear()
            return
        }

        spawnCounter++
        if (enabled && intensity > 0.4f && lines.size < maxLines) {
            val interval = (10 - (intensity * 7)).toInt().coerceIn(2, 8)
            if (spawnCounter % interval == 0) {
                spawnLine(intensity)
            }
        }

        val iterator = lines.iterator()
        while (iterator.hasNext()) {
            val line = iterator.next()
            line.lifetime--
            line.alpha = (line.lifetime.toFloat() / 20f) * currentAlpha
            if (line.lifetime <= 0) {
                iterator.remove()
            }
        }
    }

    private fun spawnLine(intensity: Float) {
        val side = if (random.nextBoolean()) -1f else 1f
        val margin = 20f
        val screenW = mc.window.scaledWidth.toFloat()
        val screenH = mc.window.scaledHeight.toFloat()

        val x = if (side < 0) margin + random.nextFloat() * 40f
                else screenW - margin - random.nextFloat() * 40f
        val y = 60f + random.nextFloat() * (screenH - 120f)
        val length = (15f + random.nextFloat() * 25f) * intensity.coerceIn(0.5f, 1f)
        val angle = (random.nextFloat() - 0.5f) * 30f
        val lifetime = (10 + (random.nextFloat() * 15).toInt()).toInt()

        lines.add(SpeedLine(x, y, length, angle, 0f, lifetime))
    }

    /**
     * Render speed lines using DrawContext.fill().
     *
     * @param accentColor Optional theme-aware color RGB (ignoring alpha); falls back to white if null.
     */
    fun render(screenWidth: Int, screenHeight: Int, context: DrawContext, accentColor: Color? = null) {
        if (lines.isEmpty() || currentAlpha < 0.01f) return

        lines.forEach { line ->
            val angleRad = Math.toRadians(line.angle.toDouble())
            val dx = (sin(angleRad) * line.length / 2).toFloat()
            val dy = (cos(angleRad) * line.length / 2).toFloat()

            val a = (line.alpha * 255).toInt().coerceIn(0, 255)
            val baseRgb = if (accentColor != null) accentColor.rgb and 0xFFFFFF else 0xFFFFFF
            val color = (a shl 24) or baseRgb

            // Draw thin line as a small filled rectangle
            val x1 = (line.x - dx).toInt()
            val y1 = (line.y - dy).toInt()
            val x2 = (line.x + dx).toInt()
            val y2 = (line.y + dy).toInt()
            context.fill(x1, y1, x2, y2, color)
        }
    }
}
