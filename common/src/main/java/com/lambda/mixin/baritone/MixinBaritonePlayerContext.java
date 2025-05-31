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
import com.lambda.interaction.request.rotation.RotationManager;
import com.lambda.util.BaritoneUtils;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaritonePlayerContext.class, remap = false) // fix compileJava warning
public class MixinBaritonePlayerContext {
    @Shadow
    @Final
    private Baritone baritone;

    // Let baritone know the actual rotation
    @Inject(method = "playerRotations", at = @At("HEAD"), cancellable = true, remap = false)
    void syncRotationWithBaritone(CallbackInfoReturnable<Rotation> cir) {
        if (baritone != BaritoneUtils.getPrimary()) return;

        RotationManager rm = RotationManager.INSTANCE;
        cir.setReturnValue(new Rotation(
                (float) rm.getActiveRotation().getYaw(), (float) rm.getActiveRotation().getPitch())
        );
    }
}
