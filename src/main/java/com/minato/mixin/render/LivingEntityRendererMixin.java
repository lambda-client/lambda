
package com.minato.mixin.render;

import com.minato.Minato;
import com.minato.interaction.managers.rotating.RotationManager;
import com.minato.module.modules.render.Nametags;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.minato.util.math.LinearKt.lerp;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    @WrapOperation(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F"))
    private float wrapGetLerpedPitch(LivingEntity livingEntity, float v, Operation<Float> original) {
        Float headPitch = RotationManager.getHeadPitch();
        if (livingEntity != Minato.getMc().player || headPitch == null) return original.call(livingEntity, v);

        return lerp(v, RotationManager.getPrevServerRotation().getPitchF(), headPitch);
    }

    @Inject(method = "hasLabel(Lnet/minecraft/entity/LivingEntity;D)Z", at = @At("HEAD"), cancellable = true)
    private void injectHasLabel(LivingEntity livingEntity, double d, CallbackInfoReturnable<Boolean> cir) {
        if (Nametags.INSTANCE.isEnabled() && Nametags.shouldRenderNametag(livingEntity)) cir.setReturnValue(false);
    }
}
