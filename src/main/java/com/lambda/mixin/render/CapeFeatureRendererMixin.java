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
import com.lambda.network.CapeManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to override cape textures with Lambda capes.
 *
 * Note: In 1.21.11, render method uses OrderedRenderCommandQueue instead of VertexConsumerProvider.
 * Cape texture is now accessed via skinTextures.cape().texturePath() instead of capeTexture().
 */
@Mixin(CapeFeatureRenderer.class)
public class CapeFeatureRendererMixin {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/AssetInfo$TextureAsset;texturePath()Lnet/minecraft/util/Identifier;"))
    Identifier renderCape(Identifier original, MatrixStack matrixStack, OrderedRenderCommandQueue commandQueue, int i, PlayerEntityRenderState player, float f, float g) {
        var networkHandler = Lambda.getMc().getNetworkHandler();
        if (networkHandler == null) return original;

        var entry = player.playerName != null ? networkHandler.getPlayerListEntry(player.playerName.getString()) : null;
        if (entry == null) return original;

        var profile = entry.getProfile();
        if (!Capes.INSTANCE.isEnabled() || !CapeManager.INSTANCE.getCache().containsKey(profile.id())) return original;

        return Identifier.of("lambda", CapeManager.INSTANCE.getCache().get(profile.id()));
    }
}
