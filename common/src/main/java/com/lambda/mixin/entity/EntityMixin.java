/*
 * Copyright 2024 Lambda
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

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.EntityEvent;
import com.lambda.event.events.PlayerEvent;
import com.lambda.interaction.request.rotation.RotationManager;
import com.lambda.util.math.Vec2d;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
        if (EventFlow.post(new PlayerEvent.ChangeLookDirection(cursorDeltaX, cursorDeltaY)).isCanceled()) ci.cancel();
    }

    @Inject(method = "onTrackedDataSet(Lnet/minecraft/entity/data/TrackedData;)V", at = @At("TAIL"))
    public void onTrackedDataSet(TrackedData<?> data, CallbackInfo ci) {
        Entity entity = (Entity) (Object) this;

        EventFlow.post(new EntityEvent.EntityUpdate(entity, data));
    }

    // ToDo: Does not trigger for some reason.
    @Inject(method = "damage", at = @At("HEAD"), cancellable = true)
    public void damage(DamageSource source, float amount, CallbackInfoReturnable<Boolean> cir) {
        Entity entity = (Entity) (Object) this;

        if (EventFlow.post(new EntityEvent.Damage(entity, source, amount)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
