
package com.minato.mixin.render;

import com.minato.module.modules.render.NoRender;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.feature.HeadFeatureRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HeadFeatureRenderer.class)
public class HeadFeatureRendererMixin {
    @Inject(method = "render(Lnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/command/OrderedRenderCommandQueue;ILnet/minecraft/client/render/entity/state/EntityRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    private void injectRender(MatrixStack matrices, OrderedRenderCommandQueue queue, int light, EntityRenderState state, float limbAngle, float limbDistance, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoArmor() && NoRender.getIncludeNoOtherHeadItems()) ci.cancel();
    }
}
