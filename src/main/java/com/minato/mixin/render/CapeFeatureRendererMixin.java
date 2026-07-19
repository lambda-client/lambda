
package com.minato.mixin.render;

import com.minato.Minato;
import com.minato.module.modules.client.Capes;
import com.minato.network.CapeHandler;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.CapeFeatureRenderer;
import net.minecraft.client.render.entity.state.PlayerEntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Mixin to override cape textures with Minato capes.
 */
@Mixin(CapeFeatureRenderer.class)
public class CapeFeatureRendererMixin {
    @ModifyExpressionValue(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/PlayerEntityRenderState;FF)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/AssetInfo$TextureAsset;texturePath()Lnet/minecraft/util/Identifier;"))
    Identifier renderCape(Identifier original, MatrixStack matrixStack, OrderedRenderCommandQueue commandQueue, int i, PlayerEntityRenderState player, float f, float g) {
        var networkHandler = Minato.getMc().getNetworkHandler();
        if (networkHandler == null) return original;

        var entry = player.playerName != null ? networkHandler.getPlayerListEntry(player.playerName.getString()) : null;
        if (entry == null) return original;

        var profile = entry.getProfile();
        if (!Capes.INSTANCE.isEnabled() || !CapeHandler.INSTANCE.getCache().containsKey(profile.id())) return original;

        return Identifier.of("minato", CapeHandler.INSTANCE.getCache().get(profile.id()));
    }
}
