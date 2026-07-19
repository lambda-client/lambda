@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

import net.minecraft.util.math.Vec3d

/**
 * KillEffectSequence — quản lý vòng đời của 1 hiệu ứng kill.
 *
 * ### Lifecycle
 * 1. Spawn khi [KillDetector] phát hiện kill
 * 2. Duration: ~1.2s tổng cộng (1200ms)
 * 3. Phases: Lightning flash (120ms) → Soul rise (1s) → Shockwave (150ms) → Fade
 * 4. Auto remove khi hết hạn
 *
 * ### Thread Safety
 * - Dùng [System.nanoTime] cho timing accuracy
 * - progress = 0.0 → 1.0 (1200ms)
 * - isAlive = false khi hoàn thành
 */
class KillEffectSequence(
    /** Vị trí kill trong world */
    var killPosition: Vec3d,
    /** Streak hiện tại */
    var streak: Int,
    /** Label cho streak (nếu có) */
    var streakLabel: String,
    /** Có reduce flash không (accessibility) */
    var reduceFlash: Boolean,
) {
    companion object {
        /** Tổng duration tối đa ~1.2 giây (nanos) */
        const val DURATION_NANOS = 1_200_000_000L
        /** Lightning phase: 120ms flash + 80ms fade */
        const val LIGHTNING_DURATION_NANOS = 200_000_000L
        /** Soul particles: 1 giây */
        const val SOUL_DURATION_NANOS = 1_000_000_000L
        /** Shockwave: 150ms */
        const val SHOCKWAVE_DURATION_NANOS = 150_000_000L
        /** Vignette + text: 800ms */
        const val VIGNETTE_DURATION_NANOS = 800_000_000L
        /** Giới hạn concurrent sequences */
        const val MAX_CONCURRENT = 3
    }

    var startTimeNanos: Long = System.nanoTime()
    private val elapsedNanos: Long get() = System.nanoTime() - startTimeNanos

    /** Progress 0.0 → 1.0 */
    val progress: Float get() = (elapsedNanos.toFloat() / DURATION_NANOS).coerceAtMost(1f)

    /** Còn alive? */
    val isAlive get() = elapsedNanos < DURATION_NANOS

    /** Lightning phase active? */
    val isLightningActive get() = elapsedNanos < LIGHTNING_DURATION_NANOS

    /** Lightning alpha (flash nhanh + fade) */
    val lightningAlpha: Float
        get() {
            if (!isLightningActive) return 0f
            val t = elapsedNanos.toFloat() / LIGHTNING_DURATION_NANOS
            return if (t < 0.6f) 1f else (1f - (t - 0.6f) / 0.4f).coerceAtLeast(0f)
        }

    /** Soul phase active? */
    val isSoulActive get() = elapsedNanos < SOUL_DURATION_NANOS

    /** Soul progress (0→1) */
    val soulProgress get() = (elapsedNanos.toFloat() / SOUL_DURATION_NANOS).coerceAtMost(1f)

    /** Shockwave active? */
    val isShockwaveActive get() = elapsedNanos < SHOCKWAVE_DURATION_NANOS

    /** Shockwave progress (0→1) */
    val shockwaveProgress get() = (elapsedNanos.toFloat() / SHOCKWAVE_DURATION_NANOS).coerceAtMost(1f)

    /** Shockwave alpha (fade) */
    val shockwaveAlpha get() = (1f - shockwaveProgress).coerceIn(0.3f, 1f)

    /** Vignette active? */
    val isVignetteActive get() = elapsedNanos < VIGNETTE_DURATION_NANOS

    /** Vignette alpha (peak early, fade late) */
    val vignetteAlpha: Float
        get() {
            if (!isVignetteActive) return 0f
            val t = elapsedNanos.toFloat() / VIGNETTE_DURATION_NANOS
            val maxAlpha = if (reduceFlash) 0.06f else 0.12f
            return if (t < 0.2f) (t / 0.2f) * maxAlpha
            else (1f - (t - 0.2f) / 0.8f).coerceAtLeast(0f) * maxAlpha
        }

    /** Text alpha (fade in/out trong 800ms) */
    val textAlpha: Float
        get() {
            if (!isVignetteActive) return 0f
            val t = elapsedNanos.toFloat() / VIGNETTE_DURATION_NANOS
            return if (t < 0.15f) t / 0.15f
            else if (t < 0.7f) 1f
            else (1f - (t - 0.7f) / 0.3f).coerceAtLeast(0f)
        }

    /** Lightning bolt segment count (dựa trên quality preset) */
    var lightningSegments: Int = 9
    /** Soul particle count (dựa trên quality preset) */
    var soulParticleCount: Int = 20

    /** Cached soul particle data — generate 1 lần, render nhiều frame */
    var soulParticles: SoulParticleLayer.ParticleData? = null

    /**
     * Re-initialize với dữ liệu mới (object pooling).
     */
    fun reinit(
        killPosition: Vec3d,
        streak: Int,
        streakLabel: String,
        reduceFlash: Boolean,
    ) {
        this.killPosition = killPosition
        this.streak = streak
        this.streakLabel = streakLabel
        this.reduceFlash = reduceFlash
        this.startTimeNanos = System.nanoTime()
        lightningSegments = 9
        soulParticleCount = 20
        soulParticles = null
    }
}
