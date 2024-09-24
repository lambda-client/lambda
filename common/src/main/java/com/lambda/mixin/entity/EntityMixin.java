package com.lambda.mixin.entity;

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.EntityEvent;
import com.lambda.event.events.WorldEvent;
import com.lambda.interaction.RotationManager;
import com.lambda.util.math.Vec2d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public void move(MovementType movementType, Vec3d movement) {
    }

    @Shadow
    public abstract float getYaw();

    @Redirect(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    public float velocityYaw(Entity entity) {
        if ((Object) this != Lambda.getMc().player) return getYaw();

        Float y = RotationManager.getMovementYaw();
        if (y == null) return getYaw();

        return y;
    }

    @Redirect(method = "getRotationVec", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    float fixDirectionYaw(Entity entity, float tickDelta) {
        Vec2d rot = RotationManager.getRotationForVector(tickDelta);
        if (entity != Lambda.getMc().player || rot == null) return entity.getYaw(tickDelta);

        return (float) rot.getX();
    }

    @Redirect(method = "getRotationVec", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    float fixDirectionPitch(Entity entity, float tickDelta) {
        Vec2d rot = RotationManager.getRotationForVector(tickDelta);
        if (entity != Lambda.getMc().player || rot == null) return entity.getPitch(tickDelta);

        return (float) rot.getY();
    }

    @Redirect(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    float fixDirectionYaw2(Entity entity) {
        Vec2d rot = RotationManager.getRotationForVector(1.0);
        if (entity != Lambda.getMc().player || rot == null) return entity.getYaw();

        return (float) rot.getX();
    }

    @Redirect(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch()F"))
    float fixDirectionPitch2(Entity entity) {
        Vec2d rot = RotationManager.getRotationForVector(1.0);
        if (entity != Lambda.getMc().player || rot == null) return entity.getPitch();

        return (float) rot.getY();
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void changeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (EventFlow.post(new EntityEvent.ChangeLookDirection(cursorDeltaX, cursorDeltaY)).isCanceled()) ci.cancel();
    }

    @Inject(method = "onTrackedDataSet(Lnet/minecraft/entity/data/TrackedData;)V", at = @At("TAIL"))
    public void onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        EventFlow.post(new WorldEvent.EntityUpdate(entity, data));
    }
}
