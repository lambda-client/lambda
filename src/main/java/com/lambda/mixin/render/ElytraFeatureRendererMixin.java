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
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ElytraFeatureRenderer.class)
public class ElytraFeatureRendererMixin<T extends LivingEntity> {
    @ModifyReturnValue(method = "getTexture(Lnet/minecraft/client/render/entity/state/BipedEntityRenderState;)Lnet/minecraft/util/Identifier;", at = @At("TAIL"))
    private static Identifier getTexture(Identifier original, BipedEntityRenderState state) {
        // FixMe: fuck you mojang
        /*var entity = StreamSupport.stream(Lambda.getMc().world.getEntities().spliterator(), false)
                        .filter(e -> e.getDisplayName() == state.displayName)
                        .findFirst().get();

        if (!Capes.INSTANCE.isEnabled() || !CapeManager.INSTANCE.containsKey(entity.getUuid())) return original;

        return Identifier.of("lambda", CapeManager.INSTANCE.get(entity.getUuid()));*/

        return original;
    }

    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void injectRender(MatrixStack matrixStack, VertexConsumerProvider vertexConsumerProvider, int i, BipedEntityRenderState bipedEntityRenderState, float f, float g, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled()&& NoRender.getNoElytra()) ci.cancel();
    }
}
