package com.lambda.mixin.world;

import com.lambda.module.modules.movement.TridentFlight;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(World.class)
public class WorldMixin {
    @Inject(method = "hasRain", at = @At("HEAD"), cancellable = true)
    private void hasRain(BlockPos pos, CallbackInfoReturnable<Boolean> cir) {
        if (TridentFlight.INSTANCE.isEnabled()) cir.setReturnValue(TridentFlight.INSTANCE.getRain());
    }
}
