
package com.minato.mixin.render;

import com.minato.module.modules.render.Fullbright;
import com.minato.module.modules.render.NoRender;
import com.minato.module.modules.render.XRay;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.GpuTexture;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.ColorHelper;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LightmapTextureManager.class)
public class LightmapTextureManagerMixin {
    @Shadow
    @Final
    private GpuTexture glTexture;

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/profiler/Profiler;pop()V", shift = At.Shift.BEFORE))
    private void injectUpdate(float tickProgress, CallbackInfo ci) {
        if (Fullbright.INSTANCE.isEnabled() || XRay.INSTANCE.isEnabled()) {
            RenderSystem.getDevice().createCommandEncoder().clearColorTexture(glTexture, ColorHelper.fullAlpha(ColorHelper.getWhite(1.0f)));
        }
    }

    @ModifyReturnValue(method = "getDarkness", at = @At("RETURN"))
    private float modifyGetDarkness(float original, LivingEntity entity, float factor, float tickProgress) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoDarkness()) return 0.0f;
        return original;
    }
}
