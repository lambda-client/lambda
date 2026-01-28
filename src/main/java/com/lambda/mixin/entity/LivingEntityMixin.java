/*
 * Copyright 2025 Lambda
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
import com.lambda.event.events.MovementEvent;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.lambda.module.modules.movement.ElytraFly;
import com.lambda.module.modules.movement.Velocity;
import com.lambda.module.modules.render.ViewModel;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin {

    @Unique private final LivingEntity lambda$instance = (LivingEntity) (Object) this;

    @Shadow protected abstract float getJumpVelocity();

    /**
     * Overwrites the jump function to use our rotation and movements
     * <pre>{@code
     * protected void jump() {
     *     Vec3d vec3d = this.getVelocity();
     *     this.setVelocity(vec3d.x, (double)this.getJumpVelocity(), vec3d.z);
     *     if (this.isSprinting()) {
     *         float f = this.getYaw() * (float) (Math.PI / 180.0);
     *         this.setVelocity(this.getVelocity().add((double)(-MathHelper.sin(f) * 0.2F), 0.0, (double)(MathHelper.cos(f) * 0.2F)));
     *     }
     *
     *     this.velocityDirty = true;
     * }
     * }</pre>
     */
    @Inject(method = "jump", at = @At("HEAD"), cancellable = true)
    void onJump(CallbackInfo ci) {
        LivingEntity self = lambda$instance;
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
        if (EventFlow.post(new MovementEvent.Entity.Pre(lambda$instance, movementInput)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        EventFlow.post(new MovementEvent.Entity.Post(lambda$instance, movementInput));
    }

    /**
     * Modifies the entity pitch with the current rotation when the entity is fall flying
     */
    @WrapOperation(method = "calcGlidingVelocity(Lnet/minecraft/util/math/Vec3d;)Lnet/minecraft/util/math/Vec3d;", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float hookModifyFallFlyingPitch(LivingEntity entity, Operation<Float> original) {
        Float pitch = RotationManager.getMovementPitch();
        if (entity != Lambda.getMc().player || pitch == null) return original.call(entity);

        return pitch;
    }

    /**
     * Modifies the entity yaw with the active rotation yaw when the entity swing its hand
     * <pre>{@code
     * protected float turnHead(float bodyRotation, float headRotation) {
     *     float f = MathHelper.wrapDegrees(bodyRotation - this.bodyYaw);
     *     this.bodyYaw += f * 0.3F;
     *     float g = MathHelper.wrapDegrees(this.getYaw() - this.bodyYaw);
     *     float h = this.getMaxRelativeHeadRotation();
     *     if (Math.abs(g) > h) {
     *         this.bodyYaw = this.bodyYaw + (g - (float)MathHelper.sign((double)g) * h);
     *     }
     *
     *     boolean bl = g < -90.0F || g >= 90.0F;
     *     if (bl) {
     *         headRotation *= -1.0F;
     *     }
     *
     *     return headRotation;
     * }
     * }</pre>
     */
    @WrapOperation(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"), slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F", ordinal = 1)))
    private float rotBody(LivingEntity entity, Operation<Float> original) {
        if (lambda$instance != Lambda.getMc().player) {
            return original.call(entity);
        }

        Float yaw = RotationManager.getHeadYaw();
        return (yaw == null) ? original.call(entity) : yaw;
    }

    /**
     * Modifies the entity yaw with the active rotation yaw
     * <pre>{@code
     * protected float turnHead(float bodyRotation, float headRotation) {
     *     float f = MathHelper.wrapDegrees(bodyRotation - this.bodyYaw);
     *     this.bodyYaw += f * 0.3F;
     *     float g = MathHelper.wrapDegrees(this.getYaw() - this.bodyYaw);
     *     float h = this.getMaxRelativeHeadRotation();
     *     if (Math.abs(g) > h) {
     *         this.bodyYaw = this.bodyYaw + (g - (float)MathHelper.sign((double)g) * h);
     *     }
     *
     *     boolean bl = g < -90.0F || g >= 90.0F;
     *     if (bl) {
     *         headRotation *= -1.0F;
     *     }
     *
     *     return headRotation;
     * }
     * }</pre>
     */
    @WrapOperation(method = "turnHead", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float rotHead(LivingEntity entity, Operation<Float> original) {
        if (lambda$instance != Lambda.getMc().player) {
            return original.call(entity);
        }

        Float yaw = RotationManager.getHeadYaw();
        return (yaw == null) ? original.call(entity) : yaw;
    }

    @WrapMethod(method = "getHandSwingDuration")
    private int getHandSwingDuration(Operation<Integer> original) {
        if (lambda$instance != Lambda.getMc().player || ViewModel.INSTANCE.isDisabled()) return original.call();

        return ViewModel.INSTANCE.getSwingDuration();
    }

    @WrapMethod(method = "pushAwayFrom")
    private void wrapPushAwayFrom(Entity entity, Operation<Void> original) {
        if (lambda$instance == Lambda.getMc().player &&
                Velocity.INSTANCE.isEnabled() &&
                Velocity.getPushed()) return;
        original.call(entity);
    }

    @SuppressWarnings("ConstantConditions")
    @ModifyReturnValue(method = "isGliding", at = @At("RETURN"))
    private boolean injectIsGliding(boolean original) {
        if (lambda$instance != Lambda.getMc().player) return original;

        return ElytraFly.isGliding();
    }
}
