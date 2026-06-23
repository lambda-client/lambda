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
import com.lambda.interaction.BaritoneHandler;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.lambda.module.modules.movement.elytrafly.ElytraFly;
import com.lambda.module.modules.player.AutoElytraSwap;
import com.lambda.module.modules.player.Reach;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import kotlin.Unit;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerEntity.class)
public abstract class PlayerEntityMixin {
    @Shadow
    public abstract void startGliding();

    @Inject(method = "clipAtLedge", at = @At(value = "HEAD"), cancellable = true)
    private void injectSafeWalk(CallbackInfoReturnable<Boolean> cir) {
        MovementEvent.ClipAtLedge event = new MovementEvent.ClipAtLedge(((PlayerEntity) (Object) this).isSneaking());
        cir.setReturnValue(EventFlow.post(event).getClip());
    }

    @WrapOperation(method = "tickMovement", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;getYaw()F"))
    private float wrapHeadYaw(PlayerEntity instance, Operation<Float> original) {
        if (BaritoneHandler.isActive()) return original.call(instance);

        if ((Object) this != Lambda.getMc().player) {
            return original.call(instance);
        }

        Float yaw = RotationManager.getHeadYaw();
        return (yaw != null) ? yaw : original.call(instance);
    }

    @SuppressWarnings("RedundantCast")
    @WrapMethod(method = "getBlockInteractionRange")
    private double wrapGetBlockInteractionRange(Operation<Double> original) {
        if ((PlayerEntity) (Object) this == Lambda.getMc().player && Reach.INSTANCE.isEnabled()) return Reach.getBlockReach();
        return original.call();
    }

    @SuppressWarnings("RedundantCast")
    @WrapMethod(method = "getEntityInteractionRange")
    private double wrapGetEntityInteractionRange(Operation<Double> original) {
        if ((PlayerEntity) (Object) this == Lambda.getMc().player && Reach.INSTANCE.isEnabled()) return Reach.getEntityReach();
        return original.call();
    }

    @Inject(method = "checkGliding", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/player/PlayerEntity;startGliding()V"), cancellable = true)
    private void injectCheckGliding(CallbackInfoReturnable<Boolean> cir) {
        if ((Object) this != Lambda.getMc().player) return;
        cir.setReturnValue(false);
        if (AutoElytraSwap.INSTANCE.isEnabled()) {
            AutoElytraSwap.registerOnGlide(sc -> {
                AutoElytraSwap.onGlide(sc);
                return Unit.INSTANCE;
            });
        }
        final var elytraFly = ElytraFly.getMode().getElytraFly();
        if (!elytraFly.isEnabled()) {
            AutoElytraSwap.registerOnGlide(sc -> { glideWithPacket(); return Unit.INSTANCE; });
            return;
        }
        AutoElytraSwap.registerOnGlide(sc -> { elytraFly.flyOrFakeFly(sc, null); return Unit.INSTANCE; });
    }

    @Unique
    private void glideWithPacket() {
        startGliding();
        final var networkHandler = Lambda.getMc().getNetworkHandler();
        if (networkHandler == null) return;
        networkHandler.sendPacket(new ClientCommandC2SPacket((Entity) (Object) this, ClientCommandC2SPacket.Mode.START_FALL_FLYING));
    }
}
