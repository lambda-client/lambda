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
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public abstract class WorldMixin {
    @Inject(method = "onBlockStateChanged", at = @At("TAIL"))
    void onBlockChanged(BlockPos pos, BlockState oldBlock, BlockState newBlock, CallbackInfo ci) {
        EventFlow.post(new WorldEvent.BlockUpdate.Client(pos, oldBlock, newBlock));
    }

    @Inject(method = "getThunderGradient(F)F", at = @At("HEAD"), cancellable = true)
    private void injectGetThunderGradient(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (Weather.INSTANCE.isEnabled()) {
            if (Weather.getWeatherMode() == Weather.WeatherMode.Thunder) cir.setReturnValue(1f);
            else cir.setReturnValue(0f);
        }
    }

    @Inject(method = "getRainGradient", at = @At("HEAD"), cancellable = true)
    private void injectGetRainGradient(float tickProgress, CallbackInfoReturnable<Float> cir) {
        if (Weather.INSTANCE.isEnabled()) {
            Weather.WeatherMode mode = Weather.getWeatherMode();
            if (mode == Weather.WeatherMode.Rain ||
                    mode == Weather.WeatherMode.Snow ||
                    mode == Weather.WeatherMode.Thunder
            ) cir.setReturnValue(1f);
            else cir.setReturnValue(0f);
        }
    }
}
