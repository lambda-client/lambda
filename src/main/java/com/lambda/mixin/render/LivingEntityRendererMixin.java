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

package com.lambda.mixin.render;

import com.lambda.Lambda;
import com.lambda.interaction.request.rotating.RotationManager;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import static com.lambda.util.math.LinearKt.lerp;

// This mixin's purpose is to set the player's pitch the current render pitch to correctly show the rotation
// regardless of the camera position
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin {
    /**
     * Uses the current rotation render pitch
     * <pre>{@code
     * float g = MathHelper.lerpAngleDegrees(f, livingEntity.lastHeadYaw, livingEntity.headYaw);
     * livingEntityRenderState.bodyYaw = clampBodyYaw(livingEntity, g, f);
     * livingEntityRenderState.relativeHeadYaw = MathHelper.wrapDegrees(g - livingEntityRenderState.bodyYaw);
     * livingEntityRenderState.pitch = livingEntity.getLerpedPitch(f);
     * livingEntityRenderState.customName = livingEntity.getCustomName();
     * livingEntityRenderState.flipUpsideDown = shouldFlipUpsideDown(livingEntity);
     *     if (livingEntityRenderState.flipUpsideDown) {
     *     livingEntityRenderState.pitch *= -1.0F;
     *     livingEntityRenderState.relativeHeadYaw *= -1.0F;
     * }
     * }</pre>
     */
    @WrapOperation(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F"))
    private float wrapGetLerpedPitch(LivingEntity livingEntity, float v, Operation<Float> original) {
        Float headPitch = RotationManager.getHeadPitch();
        if (livingEntity != Lambda.getMc().player || headPitch == null) {
            return original.call(livingEntity, v);
        }

        return lerp(v, RotationManager.getPrevServerRotation().getPitchF(), headPitch);
    }
}
