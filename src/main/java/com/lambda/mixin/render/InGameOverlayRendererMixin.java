/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.mixin.render;

import com.lambda.module.modules.render.NoRender;
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
