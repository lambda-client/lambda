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

import baritone.Baritone;
import baritone.api.utils.Rotation;
import baritone.utils.player.BaritonePlayerContext;
import com.lambda.interaction.BaritoneManager;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;

@Mixin(value = BaritonePlayerContext.class, remap = false) // fix compileJava warning
public class BaritonePlayerContextMixin {
    @Shadow
    @Final
    private Baritone baritone;

    // Let baritone know the actual rotation
    @WrapMethod(method = "playerRotations", remap = false)
    Rotation syncRotationWithBaritone(Operation<Rotation> original) {
        if (baritone != BaritoneManager.getPrimary())
            return original.call();

        return new Rotation((float) RotationManager.getActiveRotation().getYaw(), (float) RotationManager.getActiveRotation().getPitch());
    }
}
