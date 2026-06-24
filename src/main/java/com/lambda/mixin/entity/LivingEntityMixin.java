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

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.MovementEvent;
import com.lambda.interaction.handlers.BaritoneHandler;
import com.lambda.interaction.handlers.GlideHandler;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.lambda.module.modules.movement.Velocity;
import com.lambda.module.modules.movement.elytrafly.ElytraFly;
import com.lambda.module.modules.render.ViewModel;
import com.llamalad7.mixinextras.expression.Definition;
import com.llamalad7.mixinextras.expression.Expression;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.ref.LocalFloatRef;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Slice;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import static com.lambda.threading.ThreadingKt.runSafe;

@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin extends EntityMixin {

    @Unique private final LivingEntity lambda$instance = (LivingEntity) (Object) this;

    @Definition(id = "getJumpVelocity", method = "Lnet/minecraft/entity/LivingEntity;getJumpVelocity()F")
    @Expression("? = ?.getJumpVelocity()")
    @Inject(method = "jump", at = @At(value = "MIXINEXTRAS:EXPRESSION", shift = At.Shift.AFTER), cancellable = true)
    void onJump(CallbackInfo ci, @Local LocalFloatRef heightRef) {
        if (lambda$instance != Lambda.getMc().player) return;

        float height = heightRef.get();
        MovementEvent.Jump event = EventFlow.post(new MovementEvent.Jump(height));
        heightRef.set(event.getHeight());

        if (event.isCanceled()) {
            ci.cancel();
        }
    }

    @ModifyExpressionValue(method = "jump", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getYaw()F"))
    float hookModifyJumpYaw(float original) {
        if (BaritoneHandler.isActive()) return original;

        if (lambda$instance == Lambda.getMc().player) {
            Float yaw = RotationManager.getMovementYaw();
            return yaw == null ? original : yaw;
        }
        return original;
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
        if (BaritoneHandler.isActive()) return original.call(entity);

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
        if (BaritoneHandler.isActive()) return original.call(entity);

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
        if (BaritoneHandler.isActive()) return original.call(entity);

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

        return ElytraFly.INSTANCE.isEnabled()
                ? ElytraFly.getMode().getElytraFly().isGliding()
                : original;
    }

    @Inject(method = "travelGliding", at = @At("HEAD"), cancellable = true)
    private void injectTravelGliding(Vec3d movementInput, CallbackInfo ci) {
        if (lambda$instance != Lambda.getMc().player) return;
        final var grimMode = ElytraFly.getGrimControlMode();
        if (ElytraFly.getGrimControlMode().isEnabled() &&
                !grimMode.getFlipFlopMode().isFlipFlopping().invoke(grimMode.getHasFirework()) &&
                !grimMode.getMoving()
        ) ci.cancel();
    }

    @ModifyExpressionValue(method = "canGlide", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;canGlideWith(Lnet/minecraft/item/ItemStack;Lnet/minecraft/entity/EquipmentSlot;)Z"))
    private boolean modifyCanGlideWith(boolean original) {
        if ((Object) this != Lambda.getMc().player) return original;
        return runSafe(GlideHandler::canGlide);
    }
}
