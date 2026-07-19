@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.sprint

import net.minecraft.client.gui.DrawContext

/**
 * SpeedVignetteOverlay — hiệu ứng tối ở rìa màn hình khi di chuyển nhanh.
 */
object SpeedVignetteOverlay {

    private var currentAlpha = 0f
    private var targetAlpha = 0f

    fun reset() {
        currentAlpha = 0f
        targetAlpha = 0f
    }

    fun tick(intensity: Float, maxOpacity: Float, enabled: Boolean) {
        targetAlpha = if (enabled && intensity > 0.4f) {
            (intensity * maxOpacity).coerceAtMost(maxOpacity)
        } else {
            0f
        }

        currentAlpha += (targetAlpha - currentAlpha) * 0.1f
        if (currentAlpha < 0.001f) currentAlpha = 0f
    }

    /**
     * Render vignette overlay using DrawContext.fill().
     */
    fun render(screenWidth: Int, screenHeight: Int, context: DrawContext) {
        if (currentAlpha < 0.005f) return

        val a = (currentAlpha * 255).toInt().coerceIn(0, 255)
        val color = a shl 24  // black with alpha
        val vignetteWidth = (screenWidth * 0.15f).toInt().coerceAtLeast(30)
        val vignetteHeight = (screenHeight * 0.12f).toInt().coerceAtLeast(20)

        // Top
        context.fill(0, 0, screenWidth, vignetteHeight, color)
        // Bottom
        context.fill(0, screenHeight - vignetteHeight, screenWidth, screenHeight, color)
        // Left
        context.fill(0, 0, vignetteWidth, screenHeight, color)
        // Right
        context.fill(screenWidth - vignetteWidth, 0, screenWidth, screenHeight, color)
    }

    val currentAlphaValue: Float get() = currentAlpha
}
