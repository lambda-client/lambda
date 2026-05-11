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

package com.lambda.mixin.render;

import com.lambda.module.modules.render.Weather;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
    @Inject(method = "getPrecipitationAt", at = @At("HEAD"), cancellable = true)
    private void injectGetPrecipitationAt(World world, BlockPos pos, CallbackInfoReturnable<Biome.Precipitation> cir) {
        if (Weather.INSTANCE.isEnabled()) {
            Weather.WeatherMode mode = Weather.getWeatherMode();
            if (world.getRegistryKey() == World.OVERWORLD) {
                if (mode == Weather.WeatherMode.Rain && Weather.getOverrideSnow()) cir.setReturnValue(Biome.Precipitation.RAIN);
                else if (mode == Weather.WeatherMode.Snow) cir.setReturnValue(Biome.Precipitation.SNOW);
            } else {
                if (mode == Weather.WeatherMode.Snow) cir.setReturnValue(Biome.Precipitation.SNOW);
                else cir.setReturnValue(Biome.Precipitation.RAIN);
            }
        }
    }
}
