package com.lambda.mixin.render;

import com.lambda.interaction.RotationManager;
import com.lambda.module.modules.player.Freecam;
import com.lambda.module.modules.render.CameraTweaks;
import net.minecraft.client.render.Camera;
import net.minecraft.entity.Entity;
import net.minecraft.world.BlockView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Camera.class)
public abstract class CameraMixin {
    @Shadow
    public abstract void setRotation(float yaw, float pitch);

    @Inject(method = "update", at = @At("TAIL"))
    private void onUpdate(
            BlockView area,
            Entity focusedEntity,
            boolean thirdPerson,
            boolean inverseView,
            float tickDelta,
            CallbackInfo ci
    ) {
        if (!Freecam.INSTANCE.isEnabled()) return;

        Freecam.updateCam();
    }

    @Inject(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;setPos(DDD)V", shift = At.Shift.AFTER))
    private void injectQuickPerspectiveSwap(BlockView area, Entity focusedEntity, boolean thirdPerson, boolean inverseView, float tickDelta, CallbackInfo ci) {
        var rot = RotationManager.getLockRotation();
        if (rot == null) return;
        setRotation(rot.getYawF(), rot.getPitchF());
    }

    @Inject(method = "clipToSpace", at = @At("HEAD"), cancellable = true)
    private void onClipToSpace(float f, CallbackInfoReturnable<Float> cir) {
        if (CameraTweaks.INSTANCE.isEnabled() && CameraTweaks.getNoClipCam()) cir.setReturnValue(f);
    }

    @ModifyArg(method = "update", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/render/Camera;clipToSpace(F)F"))
    private float onDistanceUpdate(float f) {
        if (CameraTweaks.INSTANCE.isEnabled()) return CameraTweaks.getCamDistance();

        return f;
    }
}
