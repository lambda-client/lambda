/*
 * Copyright 2026 Lambda
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

import com.lambda.module.modules.render.XRay;
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
        if (XRay.INSTANCE.isDisabled() || XRay.isSelected(blockInfo.blockState)) return;
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
