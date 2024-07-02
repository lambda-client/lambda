package com.lambda.mixin.render;

import com.lambda.graphics.gl.VaoUtils;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private int indexBufferId;

    @Inject(method = "uploadIndexBuffer(Lnet/minecraft/client/render/BuiltBuffer$DrawParameters;Ljava/nio/ByteBuffer;)Lcom/mojang/blaze3d/systems/RenderSystem$ShapeIndexBuffer;", at = @At("RETURN"))
    private void onConfigureIndexBuffer(BuiltBuffer.DrawParameters parameters, ByteBuffer indexBuffer, CallbackInfoReturnable<RenderSystem.ShapeIndexBuffer> cir) {
        RenderSystem.ShapeIndexBuffer value = cir.getReturnValue();
        VaoUtils.lastIbo = value == null ? this.indexBufferId : value.id;
    }
}
