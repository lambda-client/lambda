package com.lambda.mixin;

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.ClientEvent;
import com.lambda.event.events.TickEvent;
import com.lambda.module.modules.player.Interact;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
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

    @Inject(at = @At(value = "INVOKE", target = "Lorg/slf4j/Logger;info(Ljava/lang/String;)V", shift = At.Shift.AFTER, remap = false), method = "stop")
    private void onShutdown(CallbackInfo ci) {
        EventFlow.post(new ClientEvent.Shutdown());
    }

    /**
     * Inject after the thread field is set so `ThreadExecutor#getThread` is available
     */
    @Inject(at = @At(value = "FIELD", target = "Lnet/minecraft/client/MinecraftClient;thread:Ljava/lang/Thread;", shift = At.Shift.AFTER, ordinal = 0), method = "run")
    private void onStartup(CallbackInfo ci) {
        EventFlow.post(new ClientEvent.Startup());
    }

    @Redirect(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerInteractionManager;isBreakingBlock()Z"))
    boolean injectMultiActon(ClientPlayerInteractionManager instance) {
        if (instance == null) return true;

        if (Interact.INSTANCE.isEnabled() && Interact.getMultiAction()) return false;
        return instance.isBreakingBlock();
    }

    @Inject(method = "doItemUse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;isRiding()Z"))
    void injectFastPlace(CallbackInfo ci) {
        if (!Interact.INSTANCE.isEnabled()) return;

        Lambda.getMc().itemUseCooldown = Interact.getPlaceDelay();
    }
}
