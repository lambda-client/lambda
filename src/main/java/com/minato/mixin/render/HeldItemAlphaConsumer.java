package com.minato.mixin.render;

import com.minato.graphics.mc.ItemAlphaManager;
import net.minecraft.client.render.VertexConsumer;

/**
 * A VertexConsumer wrapper that intercepts color calls and applies the
 * alpha value from {@link ItemAlphaManager#currentAlpha}.
 *
 * This is used by {@link ItemRendererMixin} to make held items transparent
 * by modifying the alpha channel of every rendered vertex without changing
 * the underlying rendering pipeline.
 *
 * When {@link ItemAlphaManager#isAlphaActive} is false, this wrapper
 * delegates directly with no overhead.
 *
 * NOTE: In 1.21.x, VertexConsumer interface changed significantly.
 * This class is kept as a reference for the alpha pipeline pattern.
 * To wire it in, wrap the delegate VertexConsumer before rendering.
 */
public class HeldItemAlphaConsumer {

    private final VertexConsumer delegate;

    public HeldItemAlphaConsumer(VertexConsumer delegate) {
        this.delegate = delegate;
    }

    /**
     * Apply alpha modifier and delegate color(int, int, int, int).
     */
    public VertexConsumer color(int r, int g, int b, int a) {
        float alphaMultiplier = ItemAlphaManager.INSTANCE.getAlphaFloat();
        int newAlpha = Math.round(a * alphaMultiplier);
        return delegate.color(r, g, b, newAlpha);
    }

    /**
     * Apply alpha modifier and delegate color(float, float, float, float).
     */
    public VertexConsumer color(float r, float g, float b, float a) {
        float alphaMultiplier = ItemAlphaManager.INSTANCE.getAlphaFloat();
        float newAlpha = a * alphaMultiplier;
        return delegate.color(r, g, b, newAlpha);
    }

    /** Get the wrapped delegate. */
    public VertexConsumer getDelegate() {
        return delegate;
    }
}
