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

import com.lambda.module.modules.render.NoRender;
import com.lambda.module.modules.render.WorldColors;
import net.minecraft.client.render.BackgroundRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Stream;

/**
 * <pre>{@code
 * Vec3d vec3d2 = camera.getPos().subtract(2.0, 2.0, 2.0).multiply(0.25);
 * Vec3d vec3d3 = CubicSampler.sampleColor(
 *         vec3d2, (x, y, z) -> world.getDimensionEffects().adjustFogColor(Vec3d.unpackRgb(biomeAccess.getBiomeForNoiseGen(x, y, z).value().getFogColor()), v)
 * );
 * red = (float)vec3d3.getX();
 * green = (float)vec3d3.getY();
 * blue = (float)vec3d3.getZ();
 * }</pre>
 */
// FixMe: This crashes the game
@Mixin(BackgroundRenderer.class)
public class BackgroundRendererMixin {
    @Shadow @Final private static List<BackgroundRenderer.StatusEffectFogModifier> FOG_MODIFIERS;

    @Redirect(method = "getFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;getX()D"))
    private static double redirectRed(Vec3d baseColor) {
        return WorldColors.fogOfWarColor(baseColor).getX();
    }

    @Redirect(method = "getFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;getY()D"))
    private static double redirectGreen(Vec3d baseColor) {
        return WorldColors.fogOfWarColor(baseColor).getY();
    }

    @Redirect(method = "getFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/Vec3d;getZ()D"))
    private static double redirectBlue(Vec3d baseColor) {
        return WorldColors.fogOfWarColor(baseColor).getZ();
    }

    @Redirect(method = "getFogColor", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/biome/Biome;getWaterFogColor()I"))
    private static int redirectWaterFogColor(Biome biome) {
        return WorldColors.waterFogColor(biome.getWaterFogColor());
    }

    @Inject(method = "getFogModifier(Lnet/minecraft/entity/Entity;F)Lnet/minecraft/client/render/BackgroundRenderer$StatusEffectFogModifier;", at = @At("HEAD"), cancellable = true)
    private static void injectFogModifier(Entity entity, float tickProgress, CallbackInfoReturnable<BackgroundRenderer.StatusEffectFogModifier> cir){
        if (entity instanceof LivingEntity livingEntity) {
            Stream<BackgroundRenderer.StatusEffectFogModifier> modifiers = FOG_MODIFIERS
                    .stream()
                    .filter((modifier) ->
                            modifier.shouldApply(livingEntity, tickProgress) && NoRender.shouldAcceptFog(modifier)
                    );
            cir.setReturnValue(modifiers.findFirst().orElse(null));
        }
    }
}
