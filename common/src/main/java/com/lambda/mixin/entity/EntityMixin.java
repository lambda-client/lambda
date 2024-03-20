package com.lambda.mixin.entity;

import com.lambda.Lambda;
import com.lambda.manager.RotationManager;
import com.lambda.util.math.Vec2d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow public void move(MovementType movementType, Vec3d movement) {}

    @Shadow public abstract float getYaw();

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
}