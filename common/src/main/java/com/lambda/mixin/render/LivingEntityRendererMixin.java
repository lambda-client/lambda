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

import com.lambda.interaction.request.rotating.RotationManager;
import net.minecraft.client.render.entity.LivingEntityRenderer;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.MathHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(LivingEntityRenderer.class)
public class LivingEntityRendererMixin<T extends LivingEntity> {
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
    @Redirect(method = "render(Lnet/minecraft/entity/LivingEntity;FFLnet/minecraft/client/util/math/MatrixStack;Lnet/minecraft/client/render/VertexConsumerProvider;I)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/MathHelper;lerp(FFF)F", ordinal = 0), require = 0)
    private float injectRotationPitch(float g, float f, float s) {
        Float headPitch = RotationManager.getHeadPitch();
        if (headPitch != null) {
            return MathHelper.lerp(g, RotationManager.INSTANCE.getPrevServerRotation().getPitchF(), headPitch);
        } else return MathHelper.lerp(g, f, s);
    }
}
