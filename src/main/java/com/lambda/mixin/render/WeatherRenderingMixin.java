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

import com.lambda.module.modules.render.Weather;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.render.WeatherRendering;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.biome.Biome;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(WeatherRendering.class)
public class WeatherRenderingMixin {
    @WrapMethod(method = "getPrecipitationAt")
    private Biome.Precipitation injectGetPrecipitationAt(World world, BlockPos pos, Operation<Biome.Precipitation> original) {
        if (Weather.INSTANCE.isDisabled())
            return original.call(world, pos);

        Weather.WeatherMode mode = Weather.getWeatherMode();
        if (world.getRegistryKey() == World.OVERWORLD) {
            if (mode == Weather.WeatherMode.Rain && Weather.getOverrideSnow()) return Biome.Precipitation.RAIN;
            else if (mode == Weather.WeatherMode.Snow) return Biome.Precipitation.SNOW;
        } else {
            if (mode == Weather.WeatherMode.Snow) return Biome.Precipitation.SNOW;
            else return Biome.Precipitation.RAIN;
        }

        return original.call(world, pos); // ??
    }
}
