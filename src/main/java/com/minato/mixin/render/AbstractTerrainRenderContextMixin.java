
package com.minato.mixin.render;

import com.minato.module.modules.render.XRay;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AbstractTerrainRenderContext.class)
public class AbstractTerrainRenderContextMixin {
    @Final
    @Shadow(remap = false)
    protected BlockRenderInfo blockInfo;

    @Inject(method = "bufferQuad", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractTerrainRenderContext;bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;Lnet/minecraft/client/render/VertexConsumer;)V"), cancellable = true)
    private void injectBufferQuad(MutableQuadViewImpl quad, CallbackInfo ci) {
        if (XRay.INSTANCE.isDisabled() || XRay.getBlockSelection().contains(blockInfo.blockState.getBlock())) return;
        int opacity = XRay.getOpacity();

        if (opacity == 0) ci.cancel();
        else if (opacity < 100) {
            int alpha = (int) (opacity * 2.55f);
            for (int i = 0; i < 4; i++) {
                quad.color(i, ((alpha & 0xFF) << 24) | (quad.color(i) & 0x00FFFFFF));
            }
        }
    }
}
