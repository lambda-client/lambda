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

package com.lambda.mixin.world;

import com.lambda.interaction.BaritoneHandler;
import com.lambda.interaction.managers.rotating.RotationManager;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.Entity;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.lambda.Lambda.getMc;

@Mixin(Direction.class)
public class DirectionMixin {
    @ModifyExpressionValue(method = "getEntityFacingOrder", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getYaw(F)F"))
    private static float modifyGetYaw(float original, Entity entity) {
        return BaritoneHandler.isActive() ? original : entity == getMc().player ? RotationManager.getServerRotation().getYawF() : original;
    }

    @ModifyExpressionValue(method = "getEntityFacingOrder", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPitch(F)F"))
    private static float modifyGetPitch(float original, Entity entity) {
        return BaritoneHandler.isActive() ? original : entity == getMc().player ? RotationManager.getServerRotation().getPitchF() : original;
    }
}