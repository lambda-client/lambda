
package com.minato.mixin.render;

import com.minato.module.modules.render.NoRender;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import net.minecraft.client.render.fog.FogRenderer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.client.render.fog.FogRenderer.FOG_UBO_SIZE;

@Mixin(FogRenderer.class)
public class FogRendererMixin {
    @Final
    @Shadow
    private GpuBuffer emptyBuffer;

    @Inject(method = "getFogBuffer", at = @At("HEAD"), cancellable = true)
    private void modify(FogRenderer.FogType fogType, CallbackInfoReturnable<GpuBufferSlice> cir) {
        if (fogType == FogRenderer.FogType.WORLD && NoRender.INSTANCE.isEnabled() && NoRender.getNoTerrainFog())
            cir.setReturnValue(emptyBuffer.slice(0L, FOG_UBO_SIZE));
    }
}
