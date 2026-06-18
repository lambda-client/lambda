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

package com.lambda.mixin.baritone;

import baritone.api.utils.Rotation;
import baritone.behavior.LookBehavior;
import com.lambda.interaction.BaritoneHandler;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;

@Mixin(value = LookBehavior.class, remap = false)
public class LookBehaviourMixin {
//    @Unique
//    LookBehavior instance = (LookBehavior) (Object) this;
//
//    // Redirect baritone's rotations into our rotation engine
//    @Inject(method = "updateTarget", at = @At("HEAD"))
//    void onTargetUpdate(Rotation rotation, boolean blockInteract, CallbackInfo ci) {
//        if (instance.baritone != BaritoneHandler.getPrimary()) return;
//        RotationManager.handleBaritoneRotation(rotation.getYaw(), rotation.getPitch());
//    }
//
//    @WrapOperation(method = "onPlayerUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;setYaw(F)V"))
//    private void wrapSetYaw(ClientPlayerEntity instance, float v, Operation<Void> original) {}
//
//    @WrapOperation(method = "onPlayerUpdate", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;setPitch(F)V"))
//    private void wrapSetPitch(ClientPlayerEntity instance, float v, Operation<Void> original) {}
//
//    @WrapOperation(method = "pig", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;setYaw(F)V"))
//    private void wrapPigSetYaw(ClientPlayerEntity instance, float v, Operation<Void> original) {}
}
