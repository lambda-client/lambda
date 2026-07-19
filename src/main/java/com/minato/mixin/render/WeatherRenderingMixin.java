
package com.minato.mixin.render;

import com.minato.module.modules.render.Weather;
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
