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

import baritone.Baritone;
import baritone.api.utils.Rotation;
import baritone.utils.player.BaritonePlayerContext;
import com.lambda.interaction.BaritoneHandler;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

import static net.minecraft.util.math.MathHelper.wrapDegrees;

@Mixin(value = BaritonePlayerContext.class, remap = false) // fix compileJava warning
public abstract class BaritonePlayerContextMixin {
//    @Shadow
//    @Final
//    private Baritone baritone;
//
//    @Shadow
//    public abstract ClientPlayerEntity player();
//
//    // Let baritone know the actual rotation
//    @ModifyReturnValue(method = "playerRotations", at = @At("RETURN"), remap = false)
//    Rotation syncRotationWithBaritone(Rotation original) {
//        if (baritone != BaritoneHandler.getPrimary()) return original;
//
//        var baritoneRot = baritone.getLookBehavior().getEffectiveRotation();
//        var lambdaRot = RotationManager.getActiveRotation();
//
//        float yaw = baritoneRot.map(Rotation::getYaw).orElseGet(lambdaRot::getYawF);
//        float pitch = baritoneRot.map(Rotation::getPitch).orElseGet(lambdaRot::getPitchF);
//
//        if (Float.isNaN(yaw) || Float.isNaN(pitch)) {
//            return original;
//        }
//
//        return new Rotation(wrapDegrees(yaw), pitch);
//    }
}
