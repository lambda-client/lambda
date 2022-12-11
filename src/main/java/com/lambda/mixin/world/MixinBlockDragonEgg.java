package com.lambda.mixin.world;

import com.lambda.client.module.modules.movement.Prevent;
import net.minecraft.block.BlockDragonEgg;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockDragonEgg.class)
public class MixinBlockDragonEgg {
    @Inject(method = "teleport", at = @At("HEAD"), cancellable = true)
    public void onTeleport(World worldIn, BlockPos pos, CallbackInfo ci) {
        // if prevent is enabled, and the dragon egg setting is toggled, cancel the "teleport" function, so no particles spawn
        if (Prevent.INSTANCE.isEnabled() && Prevent.INSTANCE.getDragonEgg()) {
            ci.cancel();
        }
    }
}
