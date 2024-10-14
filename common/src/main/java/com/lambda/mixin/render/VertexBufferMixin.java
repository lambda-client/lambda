package com.lambda.mixin.render;

import com.lambda.graphics.buffer.vertex.ElementBuffer;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gl.VertexBuffer;
import net.minecraft.client.render.BufferBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.nio.ByteBuffer;

@Mixin(VertexBuffer.class)
public class VertexBufferMixin {
    @Shadow
    private int indexBufferId;

    @Inject(method = "uploadIndexBuffer", at = @At("RETURN"))
    private void onConfigureIndexBuffer(BufferBuilder.DrawParameters parameters, ByteBuffer vertexBuffer, CallbackInfoReturnable<RenderSystem.ShapeIndexBuffer> cir) {
        RenderSystem.ShapeIndexBuffer value = cir.getReturnValue();
        ElementBuffer.lastIbo = value == null ? this.indexBufferId : value.id;
    }
}
