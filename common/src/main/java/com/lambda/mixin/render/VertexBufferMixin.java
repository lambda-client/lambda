/*
 * Copyright 2024 Lambda
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

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.GpuBuffer;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BuiltBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(VertexBuffer.class)
public class VertexBufferMixin {
    @Shadow
    private GpuBuffer indexBuffer;

    @Inject(method = "uploadIndexBuffer(Lnet/minecraft/client/render/BuiltBuffer$DrawParameters;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/systems/RenderSystem$ShapeIndexBuffer;", at = @At("RETURN"))
    private void onConfigureIndexBuffer(BuiltBuffer.DrawParameters parameters, ByteBuffer buf, CallbackInfoReturnable<RenderSystem.ShapeIndexBuffer> cir) {
        RenderSystem.ShapeIndexBuffer value = cir.getReturnValue();
        Buffer.lastIbo = value == null ? this.indexBuffer.handle : value.buffer.handle;
    }
}
