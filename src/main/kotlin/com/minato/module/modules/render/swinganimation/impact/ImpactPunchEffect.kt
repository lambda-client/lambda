@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.impact

import net.minecraft.client.gui.DrawContext
import net.minecraft.util.math.Vec3d
import kotlin.random.Random

/**
 * ImpactPunchEffect — hiệu ứng khi đánh trúng entity:
 * - FOV punch: Camera giật nhẹ (thuần render, không delay input)
 * - Screen flash: Rìa màn hình nhấp nháy
 * - Camera micro-shake: Rung camera nhẹ
 *
 * ### Thread Safety
 * - Tất cả state được update từ render thread (qua TickEvent + HudRenderEvent)
 * - Không cần lock
 *
 * ### Lifecycle
 * - Trigger bởi [PlayerEvent.Attack.Entity]
 * - Duration: ~60ms FOV punch, ~100ms flash, ~40ms shake
 * - Tự động reset sau khi hết duration
 */
object ImpactPunchEffect {

    /** Current FOV punch offset (0 = no effect) */
    var fovPunchAmount = 0f
        private set

    /** Current screen flash alpha (0 = no effect) */
    var flashAlpha = 0f
        private set

    /** Current camera shake offset */
    var shakeOffset: Vec3d = Vec3d.ZERO
        private set

    /** Duration constants (nanos) */
    private const val FOV_PUNCH_NANOS = 60_000_000L     // 60ms
    private const val FLASH_NANOS = 100_000_000L         // 100ms
    private const val SHAKE_NANOS = 40_000_000L          // 40ms

    /** Intensity multipliers */
    private var fovIntensity = 0.02f
    private var flashIntensity = 0.12f
    private var shakeIntensity = 0.015f

    private var fovStartTime = 0L
    private var flashStartTime = 0L
    private var shakeStartTime = 0L
    private var shakeSeed = 0L

    fun onHit() {
        val now = System.nanoTime()
        fovStartTime = now
        flashStartTime = now
        shakeStartTime = now
        shakeSeed = now
    }

    fun tick(
        baseFovIntensity: Float,
        baseFlashIntensity: Float,
        baseShakeIntensity: Float,
        enabled: Boolean,
    ) {
        val now = System.nanoTime()

        fovIntensity = baseFovIntensity
        flashIntensity = baseFlashIntensity
        shakeIntensity = baseShakeIntensity

        if (!enabled) {
            fovPunchAmount = 0f
            flashAlpha = 0f
            shakeOffset = Vec3d.ZERO
            return
        }

        // FOV punch
        val fovElapsed = now - fovStartTime
        fovPunchAmount = if (fovElapsed < FOV_PUNCH_NANOS) {
            val t = fovElapsed.toFloat() / FOV_PUNCH_NANOS
            (1f - t) * fovIntensity
        } else {
            0f
        }

        // Screen flash
        val flashElapsed = now - flashStartTime
        flashAlpha = if (flashElapsed < FLASH_NANOS) {
            val t = flashElapsed.toFloat() / FLASH_NANOS
            if (t < 0.3f) (t / 0.3f) * flashIntensity
            else (1f - (t - 0.3f) / 0.7f) * flashIntensity
        } else {
            0f
        }

        // Camera shake
        val shakeElapsed = now - shakeStartTime
        shakeOffset = if (shakeElapsed < SHAKE_NANOS) {
            val t = shakeElapsed.toFloat() / SHAKE_NANOS
            val decay = 1f - t
            val shakeRng = Random(shakeSeed + (shakeElapsed / 100_000L))
            Vec3d(
                ((shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity * decay).toDouble(),
                ((shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity * decay).toDouble(),
                ((shakeRng.nextFloat() - 0.5f) * 2f * shakeIntensity * decay).toDouble(),
            )
        } else {
            Vec3d.ZERO
        }
    }

    fun reset() {
        fovPunchAmount = 0f
        flashAlpha = 0f
        shakeOffset = Vec3d.ZERO
        fovStartTime = 0L
        flashStartTime = 0L
        shakeStartTime = 0L
        shakeSeed = 0L
    }

    /**
     * Render screen flash overlay using DrawContext.fill().
     */
    fun renderFlash(screenWidth: Int, screenHeight: Int, context: DrawContext) {
        if (flashAlpha <= 0.005f) return

        val a = (flashAlpha * 255).toInt().coerceIn(0, 255)
        val color = (a shl 24) or 0xFFFFFF  // white flash

        val edgeSize = (minOf(screenWidth, screenHeight) * 0.12f).toInt().coerceAtLeast(10)

        // Draw 4 edge rectangles
        context.fill(0, 0, screenWidth, edgeSize, color)
        context.fill(0, screenHeight - edgeSize, screenWidth, screenHeight, color)
        context.fill(0, 0, edgeSize, screenHeight, color)
        context.fill(screenWidth - edgeSize, 0, screenWidth, screenHeight, color)
    }
}
