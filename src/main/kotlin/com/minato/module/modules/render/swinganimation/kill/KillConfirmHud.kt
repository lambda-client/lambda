@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

import com.minato.Minato.mc
import net.minecraft.client.gui.DrawContext

/**
 * KillConfirmHud — vignette pulse màu + kill confirm text trên màn hình.
 *
 * ### Behavior
 * - Vignette pulse: ~150ms, giới hạn alpha 0.12 (0.06 khi Reduce Flash)
 * - Text \"ELIMINATED\" fade in/out trong 800ms
 * - Vị trí: KHÔNG đè lên crosshair (1/3 màn hình từ trên xuống)
 * - Killstreak text đổi màu theo mốc
 */
object KillConfirmHud {

    private const val KILL_TEXT = "ELIMINATED"

    /** Labels cho killstreak milestones */
    private val streakLabels = mapOf(
        2 to "DOUBLE KILL",
        3 to "TRIPLE KILL",
        5 to "RAMPAGE",
        10 to "UNSTOPPABLE",
    )

    /**
     * Render vignette pulse ở rìa màn hình.
     */
    fun renderVignette(
        alpha: Float,
        screenWidth: Int,
        screenHeight: Int,
        reduceFlash: Boolean,
        context: DrawContext,
    ) {
        if (alpha <= 0f) return
        val maxA = if (reduceFlash) 0.06f else 0.12f
        val a = (alpha * maxA * 255).toInt().coerceIn(0, 255)
        val color = (a shl 24) or 0xFFD700  // gold ARGB

        val edgeSize = (minOf(screenWidth, screenHeight) * 0.15f).toInt().coerceAtLeast(20)

        // Draw 4 edge rectangles using DrawContext.fill()
        context.fill(0, 0, screenWidth, edgeSize, color)
        context.fill(0, screenHeight - edgeSize, screenWidth, screenHeight, color)
        context.fill(0, 0, edgeSize, screenHeight, color)
        context.fill(screenWidth - edgeSize, 0, screenWidth, screenHeight, color)
    }

    /**
     * Render kill confirm text + streak label.
     */
    fun renderText(
        textAlpha: Float,
        streak: Int,
        streakLabel: String,
        screenWidth: Int,
        screenHeight: Int,
        context: DrawContext,
    ) {
        if (textAlpha <= 0f) return

        val a = (textAlpha * 255).toInt().coerceIn(0, 255)
        val textRenderer = mc.textRenderer ?: return

        val textX = screenWidth / 2
        val textY = screenHeight / 3  // 1/3 từ trên xuống, không đè crosshair

        // Main kill text (gold)
        val mainColor = getColorWithAlpha(a, 0xFFD700)
        val killTextWidth = textRenderer.getWidth(KILL_TEXT)
        context.drawTextWithShadow(
            textRenderer,
            KILL_TEXT,
            textX - killTextWidth / 2,
            textY,
            mainColor,
        )

        // Streak label nếu có
        if (streak >= 2) {
            val label = streakLabel.ifEmpty { "KILL" }
            val streakColorId = getStreakColor(streak)
            val streakColor = getColorWithAlpha(a, streakColorId)
            val streakTextWidth = textRenderer.getWidth(label)
            context.drawTextWithShadow(
                textRenderer,
                label,
                textX - streakTextWidth / 2,
                textY + 20,
                streakColor,
            )
        }
    }

    private fun getColorWithAlpha(alpha: Int, rgb: Int): Int {
        return (alpha.coerceIn(0, 255) shl 24) or (rgb and 0xFFFFFF)
    }

    /** Màu text theo mốc streak */
    private fun getStreakColor(streak: Int): Int = when {
        streak >= 10 -> 0xFF4444.toInt() // Red
        streak >= 5 -> 0xFF6B35.toInt()   // Orange
        streak >= 3 -> 0xFFD700.toInt()   // Gold
        streak >= 2 -> 0x90EE90.toInt()   // Light green
        else -> 0xFFD700.toInt()
    }

    /** Format streak label từ streak count */
    fun formatStreakLabel(streak: Int): String = streakLabels[streak] ?: ""
}
