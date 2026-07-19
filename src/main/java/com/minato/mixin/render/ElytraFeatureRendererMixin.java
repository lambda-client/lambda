
package com.minato.mixin.render;

import com.minato.Minato;
import com.minato.module.modules.client.Capes;
import com.minato.module.modules.render.NoRender;
import com.minato.network.CapeHandler;
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

@Mixin(ElytraFeatureRenderer.class)
public class ElytraFeatureRendererMixin {
    @ModifyReturnValue(method = "getTexture", at = @At("RETURN"))
    private static Identifier injectElytra(Identifier original, BipedEntityRenderState state) {
        if (!(state instanceof PlayerEntityRenderState playerState))
            return original;

        var networkHandler = Minato.getMc().getNetworkHandler();
        if (networkHandler == null) return original;

        var entry = playerState.playerName != null
                ? networkHandler.getPlayerListEntry(playerState.playerName.getString())
                : null;
        if (entry == null) return original;

        var profile = entry.getProfile();

        if (!Capes.INSTANCE.isEnabled() || !CapeHandler.INSTANCE.getCache().containsKey(profile.id()))
            return original;

        return Identifier.of("minato", CapeHandler.INSTANCE.getCache().get(profile.id()));
    }

    @WrapMethod(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/BipedEntityRenderState;FF)V")
    private void injectRender(MatrixStack matrixStack, OrderedRenderCommandQueue commandQueue, int i, BipedEntityRenderState bipedEntityRenderState, float f, float g, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoElytra())
            original.call(matrixStack, commandQueue, i, bipedEntityRenderState, f, g);
    }
}
