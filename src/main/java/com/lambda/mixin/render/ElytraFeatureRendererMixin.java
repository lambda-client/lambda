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

import com.lambda.Lambda;
import com.lambda.module.modules.client.Capes;
import com.lambda.module.modules.render.NoRender;
import com.lambda.network.CapeManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.ElytraFeatureRenderer;
import net.minecraft.client.render.entity.state.BipedEntityRenderState;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to override elytra textures with Lambda capes and disable elytra rendering.
 *
 * Note: In 1.21.11, render method uses OrderedRenderCommandQueue instead of VertexConsumerProvider.
 * getTexture is now a private static method.
 */
@Mixin(ElytraFeatureRenderer.class)
public class ElytraFeatureRendererMixin {
    @ModifyReturnValue(method = "getTexture", at = @At("RETURN"))
    private static Identifier injectElytra(Identifier original, BipedEntityRenderState state) {
        if (!(state instanceof PlayerEntityRenderState playerState))
            return original;

        var networkHandler = Lambda.getMc().getNetworkHandler();
        if (networkHandler == null) return original;

        var entry = playerState.playerName != null ? networkHandler.getPlayerListEntry(playerState.playerName.getString()) : null;
        if (entry == null) return original;

        var profile = entry.getProfile();

        if (!Capes.INSTANCE.isEnabled() || !CapeManager.INSTANCE.getCache().containsKey(profile.id()))
            return original;

        return Identifier.of("lambda", CapeManager.INSTANCE.getCache().get(profile.id()));
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V")
    private void injectRender(MatrixStack matrixStack, OrderedRenderCommandQueue commandQueue, int i, BipedEntityRenderState bipedEntityRenderState, float f, float g, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoElytra())
            original.call(matrixStack, commandQueue, i, bipedEntityRenderState, f, g);
    }
}
