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

import com.lambda.module.modules.render.XRay;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.fabricmc.fabric.impl.client.indigo.renderer.mesh.MutableQuadViewImpl;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.AbstractTerrainRenderContext;
import net.fabricmc.fabric.impl.client.indigo.renderer.render.BlockRenderInfo;
import net.minecraft.client.render.VertexConsumer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractTerrainRenderContext.class)
public class AbstractTerrainRenderContextMixin {
    @Final
    @Shadow(remap = false)
    protected BlockRenderInfo blockInfo;

    @WrapOperation(method = "bufferQuad", at = @At(value = "INVOKE", target = "Lnet/fabricmc/fabric/impl/client/indigo/renderer/render/AbstractTerrainRenderContext;bufferQuad(Lnet/fabricmc/fabric/impl/client/indigo/renderer/mesh/MutableQuadViewImpl;Lnet/minecraft/client/render/VertexConsumer;)V"))
    private void injectBufferQuad(AbstractTerrainRenderContext instance, MutableQuadViewImpl mutableQuadView, VertexConsumer vertexConsumer, Operation<Void> original) {
        if (XRay.INSTANCE.isDisabled() || XRay.isSelected(blockInfo.blockState))
            original.call(instance, mutableQuadView, vertexConsumer);

        int opacity = XRay.getOpacity();

        if (opacity > 0) {
            int alpha = (int) (opacity * 2.55f);
            for (int i = 0; i < 4; i++) {
                mutableQuadView.color(i, ((alpha & 0xFF) << 24) | (mutableQuadView.color(i) & 0x00FFFFFF));
            }
        }
    }
}
