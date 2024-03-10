package com.lambda.common.mixin;

import com.lambda.common.event.EventFlow;
import com.lambda.common.event.events.TickEvent;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Inject(method = "tick", at = @At("HEAD"))
    void onTickPre(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Pre());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    void onTickPost(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Post());
    }
}
