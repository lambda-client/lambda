@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

import kotlin.math.pow
import kotlin.math.sin

/**
 * Hàm easing cho animation.
 * Dùng để nội suy chuyển động mượt mà.
 */
object EaseFunctions {
    fun linear(t: Float) = t.coerceIn(0f, 1f)
    fun easeInQuad(t: Float) = t * t
    fun easeOutQuad(t: Float) = t * (2f - t)
    fun easeInOutQuad(t: Float) = if (t < 0.5f) 2f * t * t else -1f + (4f - 2f * t) * t
    fun easeOutCubic(t: Float): Float = 1f - (1f - t).pow(3f)
    fun easeInCubic(t: Float) = t * t * t
    fun easeOutExpo(t: Float): Float = if (t >= 1f) 1f else 1f - (2f).pow(-10f * t)
    fun easeOutBack(t: Float): Float {
        val c1 = 1.70158f
        val c3 = c1 + 1f
        return 1f + c3 * (t - 1f).pow(3f) + c1 * (t - 1f).pow(2f)
    }
    fun easeOutElastic(t: Float): Float {
        if (t >= 1f) return 1f
        val c4 = (2f * Math.PI.toFloat()) / 3f
        return (2f).pow(-10f * t) * sin((t * 10f - 0.75f) * c4) + 1f
    }

    /** Smooth step: 3t² - 2t³ */
    fun smoothStep(t: Float) = t * t * (3f - 2f * t)

    /** Smoother step: 6t⁵ - 15t⁴ + 10t³ */
    fun smootherStep(t: Float) = t * t * t * (t * (t * 6f - 15f) + 10f)

    /** Map t (0..1) qua easing function */
    fun apply(t: Float, easing: Easing): Float = when (easing) {
        Easing.LINEAR -> linear(t)
        Easing.IN_QUAD -> easeInQuad(t)
        Easing.OUT_QUAD -> easeOutQuad(t)
        Easing.IN_OUT_QUAD -> easeInOutQuad(t)
        Easing.OUT_CUBIC -> easeOutCubic(t)
        Easing.OUT_EXPO -> easeOutExpo(t)
        Easing.OUT_BACK -> easeOutBack(t)
        Easing.OUT_ELASTIC -> easeOutElastic(t)
        Easing.SMOOTH_STEP -> smoothStep(t)
        Easing.SMOOTHER_STEP -> smootherStep(t)
    }
}

enum class Easing {
    LINEAR,
    IN_QUAD,
    OUT_QUAD,
    IN_OUT_QUAD,
    OUT_CUBIC,
    OUT_EXPO,
    OUT_BACK,
    OUT_ELASTIC,
    SMOOTH_STEP,
    SMOOTHER_STEP,
}
