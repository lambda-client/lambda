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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(value = LookBehavior.class, remap = false)
public class LookBehaviourMixin {
    @Unique
    LookBehavior instance = ((LookBehavior) (Object) this);

    // Redirect baritone's rotations into our rotation engine
    @WrapMethod(method = "updateTarget")
    void onTargetUpdate(Rotation rotation, boolean blockInteract, Operation<Void> original) {
        if (instance.baritone != BaritoneManager.getPrimary())
            original.call(rotation, blockInteract);

        RotationManager.handleBaritoneRotation(rotation.getYaw(), rotation.getPitch());
    }

    @WrapMethod(method = "onPlayerUpdate")
    void onUpdate(PlayerUpdateEvent event, Operation<Void> original) {
        if (instance.baritone != BaritoneManager.getPrimary())
            original.call(event);
    }

    @WrapMethod(method = "onPlayerRotationMove")
    void onMovementUpdate(RotationMoveEvent event, Operation<Void> original) {
        if (instance.baritone != BaritoneManager.getPrimary())
            original.call(event);
    }
}
