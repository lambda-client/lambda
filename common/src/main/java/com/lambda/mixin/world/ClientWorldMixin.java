package com.lambda.mixin.world;

import com.lambda.event.EventFlow;
import com.lambda.event.events.WorldEvent;
import net.minecraft.block.BlockState;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.util.math.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientWorld.class)
public class ClientWorldMixin {
    @Inject(method = "handleBlockUpdate", at = @At("HEAD"), cancellable = true)
    private void handleBlockUpdateInject(BlockPos pos, BlockState state, int flags, CallbackInfo ci) {
        if (EventFlow.post(new WorldEvent.BlockUpdate(pos, state, flags)).isCanceled()) {
            ci.cancel();
        }
    }
}
