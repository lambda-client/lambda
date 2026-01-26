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

package com.lambda.mixin.world;

import com.lambda.event.EventFlow;
import com.lambda.event.events.WorldEvent;
import com.lambda.module.modules.render.Weather;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(World.class)
public abstract class WorldMixin {
    @WrapMethod(method = "onBlockStateChanged")
    void onBlockChanged(BlockPos pos, BlockState oldState, BlockState newState, Operation<Void> original) {
        original.call(pos, oldState, newState);
        EventFlow.post(new WorldEvent.BlockUpdate.Client(pos, oldState, newState));
    }

    @WrapMethod(method = "getThunderGradient(F)F")
    private float injectGetThunderGradient(float tickProgress, Operation<Float> original) {
        if (Weather.INSTANCE.isDisabled())
            return original.call(tickProgress);

        if (Weather.getWeatherMode() == Weather.WeatherMode.Thunder) return 1f;
        else return 0f;
    }

    @WrapMethod(method = "getRainGradient")
    private float injectGetRainGradient(float tickProgress, Operation<Float> original) {
        if (Weather.INSTANCE.isDisabled())
            return original.call(tickProgress);

        Weather.WeatherMode mode = Weather.getWeatherMode();
        if (mode == Weather.WeatherMode.Rain ||
                mode == Weather.WeatherMode.Snow ||
                mode == Weather.WeatherMode.Thunder) return 1f;
        else return 0f;
    }
}
