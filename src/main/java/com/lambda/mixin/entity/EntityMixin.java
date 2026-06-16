/*
 * Copyright 2026 Lambda
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

package com.lambda.mixin.entity;

import com.lambda.event.EventFlow;
import com.lambda.event.events.EntityEvent;
import com.lambda.event.events.PlayerEvent;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.lambda.module.modules.movement.elytrafly.ElytraFly;
import com.lambda.module.modules.movement.elytrafly.ElytraFly.FlyMode;
import com.lambda.module.modules.render.NoRender;
import com.lambda.util.math.Vec2d;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.v2.WrapWithCondition;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityPose;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static com.lambda.Lambda.getMc;

@Mixin(Entity.class)
public abstract class EntityMixin {
    @Shadow
    public void move(MovementType movementType, Vec3d movement) {}

    @Shadow
    public abstract float getYaw();

    @Shadow
    private Vec3d velocity;

    /**
     * Modifies the player yaw when there is an active rotation to apply the player velocity correctly
     */
    @WrapOperation(method = "updateVelocity", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    public float velocityYaw(Entity entity, Operation<Float> original) {
        if ((Object) this != getMc().player) return original.call(entity);

        Float y = RotationManager.getMovementYaw();
        if (y == null) return original.call(entity);

        return y;
    }

    /**
     * Modifies the player yaw for the given tick delta for interpolation when there is an active rotation
     * <pre>{@code
     * public final Vec3d getRotationVec(float tickDelta) {
     *     return this.getRotationVector(this.getPitch(tickDelta), this.getYaw(tickDelta));
     * }
     * }</pre>
     */
    @WrapOperation(method = "getRotationVec", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    float fixDirectionYaw(Entity entity, float tickDelta, Operation<Float> original) {
        Vec2d rot = RotationManager.getRotationForVector(tickDelta);
        if (entity != getMc().player || rot == null) return original.call(entity, tickDelta);

        return (float) rot.getX();
    }

    /**
     * Modifies the player pitch for the given tick delta for interpolation when there is an active rotation
     * <pre>{@code
     * public final Vec3d getRotationVec(float tickDelta) {
     *     return this.getRotationVector(this.getPitch(tickDelta), this.getYaw(tickDelta));
     * }
     * }</pre>
     */
    @WrapOperation(method = "getRotationVec", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    float fixDirectionPitch(Entity entity, float tickDelta, Operation<Float> original) {
        Vec2d rot = RotationManager.getRotationForVector(tickDelta);
        if (entity != getMc().player || rot == null) return original.call(entity, tickDelta);

        return (float) rot.getY();
    }

    /**
     * Modifies the player yaw for the current rotation yaw
     * <pre>{@code
     * public Vec3d getRotationVector() {
     * 	return this.getRotationVector(this.getPitch(), this.getYaw());
     * }
     * }</pre>
     */
    @WrapOperation(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    float fixDirectionYaw2(Entity entity, Operation<Float> original) {
        Vec2d rot = RotationManager.getRotationForVector(1.0);
        if (entity != getMc().player || rot == null) return original.call(entity);

        return (float) rot.getX();
    }

    /**
     * Modifies the player yaw for the current rotation pitch
     * <pre>{@code
     * public Vec3d getRotationVector() {
     * 	return this.getRotationVector(this.getPitch(), this.getYaw());
     * }
     * }</pre>
     */
    @WrapOperation(method = "getRotationVector()Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch()F"))
    float fixDirectionPitch2(Entity entity, Operation<Float> original) {
        Vec2d rot = RotationManager.getRotationForVector(1.0);
        if (entity != getMc().player || rot == null) return original.call(entity);

        return (float) rot.getY();
    }

    @Inject(method = "changeLookDirection", at = @At("HEAD"), cancellable = true)
    private void changeLookDirection(double cursorDeltaX, double cursorDeltaY, CallbackInfo ci) {
        if (EventFlow.post(new PlayerEvent.ChangeLookDirection(cursorDeltaX, cursorDeltaY)).isCanceled()) ci.cancel();
    }

    @Inject(method = "onTrackedDataSet(Lnet/minecraft/entity/data/TrackedData;)V", at = @At("TAIL"))
    public void onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;
        EventFlow.post(new EntityEvent.Update(entity, data));
    }

    @ModifyExpressionValue(method = "isInvisible", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getFlag(I)Z"))
    private boolean modifyGetFlagInvisible(boolean original) {
        return (NoRender.INSTANCE.isDisabled() || !NoRender.getNoInvisibility()) && original;
    }

    @ModifyExpressionValue(method = "isGlowing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getFlag(I)Z"))
    private boolean modifyGetFlagGlowing(boolean original) {
        return (NoRender.INSTANCE.isDisabled() || !NoRender.getNoGlow()) && original;
    }

    @WrapWithCondition(method = "changeLookDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setYaw(F)V"))
    private boolean wrapSetYaw(Entity instance, float yaw) {
        if ((Object) this != getMc().player) return true;
        return RotationManager.getLockYaw() == null;
    }

    @WrapWithCondition(method = "changeLookDirection", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;setPitch(F)V"))
    private boolean wrapSetPitch(Entity instance, float yaw) {
        if ((Object) this != getMc().player) return true;
        return RotationManager.getLockPitch() == null;
    }

    @ModifyReturnValue(method = "isSprinting()Z", at = @At("RETURN"))
    private boolean injectIsSprinting(boolean original) {
        var player = getMc().player;
        if ((Object) this != getMc().player) return original;

        if (ElytraFly.INSTANCE.isEnabled() && ElytraFly.getMode() == FlyMode.Bounce && player.isGliding())
            return true;

        return original;
    }

    @ModifyReturnValue(method = "getPose", at = @At("RETURN"))
    private EntityPose injectGetPose(EntityPose original) {
        var player = getMc().player;
        if ((Object) this != getMc().player) return original;

        if (ElytraFly.INSTANCE.isDisabled() || ElytraFly.getMode() != FlyMode.Bounce || !player.isGliding()) return original;

        return EntityPose.GLIDING;
    }

    @ModifyExpressionValue(method = "getHorizontalFacing", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw()F"))
    private float modifyGetYaw(float original) {
        return (Object) this == getMc().player ? RotationManager.getServerRotation().getYawF() : original;
    }

    @Inject(method = "getVelocity", at = @At("HEAD"), cancellable = true)
    private void injectGetVelocity(CallbackInfoReturnable<Vec3d> cir) {
        if (ElytraFly.INSTANCE.isDisabled() || ElytraFly.getMode() != FlyMode.Bounce) return;
        cir.setReturnValue(ElytraFly.getBounceMode().getModifiedVelocity(velocity));
    }
}
