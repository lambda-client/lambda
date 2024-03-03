package com.lambda.mixin;

import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MixinMinecraftClient {
    @Inject(method = "tick", at = @At("HEAD"))
    void onTickPre(CallbackInfo ci) {
//        EventBus.post(new TickEvent.Pre());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    void onTickPost(CallbackInfo ci) {
//        EventBus.post(new TickEvent.Post());
    }
}
