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

package com.lambda.mixin.baritone;

import baritone.api.event.events.PlayerUpdateEvent;
import baritone.api.event.events.RotationMoveEvent;
import baritone.api.utils.Rotation;
import baritone.behavior.LookBehavior;
import com.lambda.interaction.request.rotation.RotationManager;
import com.lambda.util.BaritoneUtils;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LookBehavior.class)
public class MixinLookBehavior {
    // Redirect baritone's rotations into our rotation engine
    @Inject(method = "updateTarget", at = @At("HEAD"), remap = false, cancellable = true)
    void onTargetUpdate(Rotation rotation, boolean blockInteract, CallbackInfo ci) {
        LookBehavior instance = ((LookBehavior) (Object) this);
        if (instance.baritone != BaritoneUtils.getPrimary()) return;

        RotationManager.BaritoneProcessor.handleBaritoneRotation(rotation.getYaw(), rotation.getPitch());
        ci.cancel();
    }

    @Inject(method = "onPlayerUpdate", at = @At("HEAD"), remap = false, cancellable = true)
    void onUpdate(PlayerUpdateEvent event, CallbackInfo ci) {
        LookBehavior instance = ((LookBehavior) (Object) this);
        if (instance.baritone != BaritoneUtils.getPrimary()) return;

        ci.cancel();
    }

    @Inject(method = "onPlayerRotationMove", at = @At("HEAD"), remap = false, cancellable = true)
    void onMovementUpdate(RotationMoveEvent event, CallbackInfo ci) {
        LookBehavior instance = ((LookBehavior) (Object) this);
        if (instance.baritone != BaritoneUtils.getPrimary()) return;

        ci.cancel();
    }
}
