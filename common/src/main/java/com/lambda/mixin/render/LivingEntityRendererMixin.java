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

package com.lambda.mixin.render;

import com.lambda.Lambda;
import com.lambda.interaction.request.rotation.RotationManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.client.render.entity.state.LivingEntityRenderState;
import net.minecraft.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Objects;

// This mixin's purpose is to set the player's pitch the current render pitch to correctly show the rotation
// regardless of the camera position
@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity, S extends LivingEntityRenderState> {
    @Unique
    private Float lambda$pitch = null;

    @Inject(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At("HEAD"))
    private void injectRender(T livingEntity, S livingEntityRenderState, float f, CallbackInfo ci) {
        Float rotationPitch = RotationManager.getRenderPitch();

        this.lambda$pitch = null;

        if (livingEntity != Lambda.getMc().player || rotationPitch == null) {
            return;
        }

        this.lambda$pitch = rotationPitch;
    }

    /**
     * Uses the current rotation render pitch
     * <pre>{@code
     * float m = MathHelper.lerp(g, livingEntity.prevPitch, livingEntity.getPitch());
     *     if (shouldFlipUpsideDown(livingEntity)) {
     *     m *= -1.0F;
     *     k *= -1.0F;
     * }
     * }</pre>
     */
    // FixMe: When there are no rotations, the pitch is always set to 0
    @Redirect(method = "updateRenderState(Lnet/minecraft/entity/LivingEntity;Lnet/minecraft/client/render/entity/state/LivingEntityRenderState;F)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/LivingEntity;getLerpedPitch(F)F", ordinal = 0), require = 0)
    private float injectRotationPitch(LivingEntity instance, float v) {
        return Objects.requireNonNullElse(lambda$pitch, v);
    }
}
