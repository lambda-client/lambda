package com.minato.graphics.mc

/**
 * ItemAlphaManager — ThreadLocal-based alpha state carrier for the HeldItemRenderer
 * alpha transparency pipeline.
 *
 * ### Flow
 * 1. [HeldItemRendererMixin] calls [setForHand] at HEAD of `renderFirstPersonItem`,
 *    reading the alpha from [ViewModel.itemAlpha].
 * 2. [ItemRendererMixin] or [HeldItemAlphaConsumer] reads [currentAlpha] to apply
 *    transparency during item model rendering.
 * 3. [HeldItemRendererMixin] calls [clear] at RETURN of `renderFirstPersonItem`.
 *
 * This ThreadLocal approach avoids modifying any vanilla method signatures while
 * passing per-render-call state between mixin injection points.
 */
object ItemAlphaManager {

    /** Thread-local alpha (0–255). 255 = fully opaque, 0 = fully transparent. */
    val currentAlpha: ThreadLocal<Int> = ThreadLocal.withInitial { 255 }

    /** Whether an alpha override is active (< 255). */
    val isAlphaActive: Boolean get() = currentAlpha.get() < 255

    /**
     * Set the alpha for the current render pass.
     * Called from HeldItemRendererMixin at HEAD of `renderFirstPersonItem`.
     */
    fun setForHand(alpha: Int) {
        currentAlpha.set(alpha.coerceIn(0, 255))
    }

    /**
     * Get the current alpha as a float 0.0–1.0 for vertex color multiplication.
     */
    val alphaFloat: Float get() = currentAlpha.get().coerceIn(0, 255) / 255f

    /**
     * Clear the alpha after the render pass completes.
     * Called from HeldItemRendererMixin at RETURN of `renderFirstPersonItem`.
     */
    fun clear() {
        currentAlpha.set(255)
    }
}
