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

package com.lambda.mixin.baritone;

import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.utils.Rotation;
import baritone.behavior.LookBehavior;
import com.lambda.interaction.BaritoneManager;
import com.lambda.interaction.managers.rotating.RotationManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = LookBehavior.class, remap = false)
public class LookBehaviourMixin {
    @Unique
    LookBehavior instance = ((LookBehavior) (Object) this);

    // Redirect baritone's rotations into our rotation engine
    @Inject(method = "updateTarget", at = @At("HEAD"), cancellable = true)
    void onTargetUpdate(Rotation rotation, boolean blockInteract, CallbackInfo ci) {
        if (instance.baritone != BaritoneManager.getPrimary()) return;

        RotationManager.handleBaritoneRotation(rotation.getYaw(), rotation.getPitch());
        ci.cancel();
    }

    @Inject(method = "onPlayerUpdate", at = @At("HEAD"), cancellable = true)
    void onUpdate(PlayerUpdateEvent event, CallbackInfo ci) {
        if (instance.baritone != BaritoneManager.getPrimary()) return;

        ci.cancel();
    }

    @Inject(method = "onPlayerRotationMove", at = @At("HEAD"), cancellable = true)
    void onMovementUpdate(RotationMoveEvent event, CallbackInfo ci) {
        if (instance.baritone != BaritoneManager.getPrimary()) return;

        ci.cancel();
    }
}
