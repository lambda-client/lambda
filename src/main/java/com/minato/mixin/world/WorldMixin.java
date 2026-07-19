
package com.minato.mixin.world;

import com.minato.event.EventFlow;
import com.minato.event.events.WorldEvent;
import com.minato.module.modules.render.Weather;
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
