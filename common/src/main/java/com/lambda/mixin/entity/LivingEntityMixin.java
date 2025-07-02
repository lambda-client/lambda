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
import com.lambda.event.events.MovementEvent;
import com.lambda.interaction.request.rotating.RotationManager;
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
        LivingEntity entity = (LivingEntity) (Object) this;
        if (EventFlow.post(new MovementEvent.Entity.Pre(entity, movementInput)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "travel", at = @At("TAIL"))
    void onTravelPost(Vec3d movementInput, CallbackInfo ci) {
        EventFlow.post(new MovementEvent.Entity.Post((LivingEntity) (Object) this, movementInput));
    }

    /**
     * Modifies the entity pitch with the current rotation when the entity is fall flying
     */
    @Redirect(method = "travel", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getPitch()F"))
    private float hookModifyFallFlyingPitch(LivingEntity entity) {
        Float pitch = RotationManager.getMovementPitch();
        if (entity != Lambda.getMc().player || pitch == null) return entity.getPitch();

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
    @Redirect(method = "tick", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"), slice = @Slice(to = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F", ordinal = 1)))
    private float rotBody(LivingEntity entity) {
        if ((Object) this != Lambda.getMc().player) {
            return entity.getYaw();
        }

        Float yaw = RotationManager.getRenderYaw();
        return (yaw == null) ? entity.getYaw() : yaw;
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
    @Redirect(method = "turnHead", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    private float rotHead(LivingEntity entity) {
        if ((Object) this != Lambda.getMc().player) {
            return entity.getYaw();
        }

        Float yaw = RotationManager.getRenderYaw();
        return (yaw == null) ? entity.getYaw() : yaw;
    }
}
