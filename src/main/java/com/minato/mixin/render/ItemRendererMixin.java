package com.minato.mixin.render;

import com.minato.graphics.mc.ItemAlphaManager;
import net.minecraft.client.render.item.ItemRenderer;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Mixin into {@link ItemRenderer} to apply held item alpha transparency.
 *
 * ### Pipeline
 * 1. {@link HeldItemRendererMixin} sets {@link ItemAlphaManager#currentAlpha} at HEAD of
 *    {@code renderFirstPersonItem()}.
 * 2. This mixin intercepts {@code renderItem()} and modifies the light level to
 *    visually dim items when alpha < 255.
 * 3. {@link HeldItemRendererMixin} clears the alpha at RETURN of renderFirstPersonItem().
 *
 * ### Note on VertexConsumer alpha wrapping
 * In 1.21.x, {@code renderBakedItemModel} was removed in favor of the
 * {@code OrderedRenderCommandQueue} pipeline. Full vertex alpha wrapping requires
 * a mixin into {@code OrderedRenderCommandQueue.submitItemModel()} (signature TBD).
 * The {@link HeldItemAlphaConsumer} utility class is available for when the correct
 * 1.21.x mixin point is identified.
 */
@Mixin(ItemRenderer.class)
public class ItemRendererMixin {

    /**
     * Modify the light parameter of renderItem based on held item alpha.
     * When alpha < 255, reduce light to visually dim the item (approximate transparency).
     */
    @ModifyVariable(
        method = "renderItem(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/item/ItemStack;Lnet/minecraft/item/ItemDisplayContext;Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;I)V",
        at = @At("HEAD"),
        argsOnly = true,
        index = 5
    )
    private int modifyLightForAlpha(int light) {
        if (!ItemAlphaManager.INSTANCE.isAlphaActive()) return light;
        // Blend light toward zero based on alpha — 0 alpha = 0 light (fully dark),
        // 255 alpha = original light
        float factor = ItemAlphaManager.INSTANCE.getAlphaFloat();
        int blockLight = (light & 0xFF);
        int skyLight = (light >> 16) & 0xFF;
        int newBlock = Math.round(blockLight * factor);
        int newSky = Math.round(skyLight * factor);
        return (newSky << 16) | newBlock;
    }
}
