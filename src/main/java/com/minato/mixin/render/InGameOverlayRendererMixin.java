
package com.minato.mixin.render;

import com.minato.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.texture.Sprite;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArg;

@Mixin(InGameOverlayRenderer.class)
public class InGameOverlayRendererMixin {
    @WrapMethod(method = "renderFireOverlay")
    private static void wrapRenderFireOverlay(MatrixStack matrices, VertexConsumerProvider vertexConsumers, Sprite sprite, Operation<Void> original) {
        if (!(NoRender.INSTANCE.isEnabled() && NoRender.getNoFireOverlay())) {
            original.call(matrices, vertexConsumers, sprite);
        }
    }

    @ModifyArg(method = "renderFireOverlay", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/util/math/MatrixStack;translate(FFF)V"), index = 1)
    private static float onRenderFireOverlayTranslate(float x) {
        if (NoRender.INSTANCE.isEnabled()) {
            return (float) NoRender.getFireOverlayYOffset() - 0.3f;
        } else {
            return -0.3f;
        }
    }

    @WrapMethod(method = "renderUnderwaterOverlay")
    private static void wrapRenderUnderwaterOverlay(MinecraftClient client, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Operation<Void> original) {
        if (!(NoRender.INSTANCE.isEnabled() && NoRender.getNoFluidOverlay())) {
            original.call(client, matrices, vertexConsumers);
        }
    }

    @WrapMethod(method = "renderInWallOverlay")
    private static void wrapRenderInWallOverlay(Sprite sprite, MatrixStack matrices, VertexConsumerProvider vertexConsumers, Operation<Void> original) {
        if (!(NoRender.INSTANCE.isEnabled() && NoRender.getNoInWall())) {
            original.call(sprite, matrices, vertexConsumers);
        }
    }
}
