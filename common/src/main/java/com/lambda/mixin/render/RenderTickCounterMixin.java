package com.lambda.mixin.render;

import com.lambda.core.TimerManager;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(RenderTickCounter.class)
public class RenderTickCounterMixin {

    @Shadow
    public float lastFrameDuration;
    @Shadow
    public float tickDelta;
    @Shadow
    private long prevTimeMillis;

    @Inject(method = "beginRenderTick", at = @At("HEAD"), cancellable = true)
    private void beginRenderTick(long timeMillis, CallbackInfoReturnable<Integer> ci) {
        lastFrameDuration = (timeMillis - prevTimeMillis) / TimerManager.INSTANCE.getLength();
        prevTimeMillis = timeMillis;
        tickDelta += lastFrameDuration;
        int i = (int) tickDelta;
        tickDelta -= i;

        ci.setReturnValue(i);
    }
}
