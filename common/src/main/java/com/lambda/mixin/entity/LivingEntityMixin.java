package com.lambda.mixin.entity;

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.MovementEvent;
import com.lambda.interaction.RotationManager;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin {

    @Shadow
    protected abstract float getJumpVelocity();

    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    void onJump(CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != Lambda.getMc().player) return;
        ci.cancel();

        float height = this.getJumpVelocity();
        MovementEvent.Jump event = EventFlow.post(new MovementEvent.Jump(height));

        if (event.isCanceled()) return;

        Vec3d vec3d = self.getVelocity();
        self.setVelocity(vec3d.x, event.getHeight(), vec3d.z);

        if (self.isSprinting()) {
            Float yaw = RotationManager.getMovementYaw();
            float f = ((yaw != null) ? yaw : self.getYaw()) * ((float) Math.PI / 180);
            self.setVelocity(self.getVelocity().add(-MathHelper.sin(f) * 0.2f, 0.0, MathHelper.cos(f) * 0.2f));
        }

        self.velocityDirty = true;
    }

    @Inject(method = "travel", at = @At("HEAD"), cancellable = true)
    void onTravelPre(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != Lambda.getMc().player) return;

        if (EventFlow.post(new MovementEvent.Travel.Pre()).isCanceled()) ci.cancel();
    }

    @Inject(method = "travel", at = @At("TAIL"))
    void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self != Lambda.getMc().player) return;

        EventFlow.post(new MovementEvent.Travel.Post());
    }

    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float hookModifyFallFlyingPitch(LivingEntity entity) {
        Float pitch = RotationManager.getMovementPitch();
        if (entity != Lambda.getMc().player || pitch == null) return entity.getPitch();

        return pitch;
    }

    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"), slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F", ordinal = 1)))
    private float rotBody(LivingEntity entity) {
        if ((Object) this != Lambda.getMc().player) {
            return entity.getYaw();
        }

        Float yaw = RotationManager.getRenderYaw();
        return (yaw == null) ? entity.getYaw() : yaw;
    }

    @Redirect(method = "turnHead", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float rotHead(LivingEntity entity) {
        if ((Object) this != Lambda.getMc().player) {
            return entity.getYaw();
        }

        Float yaw = RotationManager.getRenderYaw();
        return (yaw == null) ? entity.getYaw() : yaw;
    }
}
