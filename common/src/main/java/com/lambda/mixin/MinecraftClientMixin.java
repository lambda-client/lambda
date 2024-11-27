/*
 * Copyright 2024 Lambda
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

package com.lambda.mixin;

import com.lambda.Lambda;
import com.lambda.event.EventFlow;
import com.lambda.event.events.ClientEvent;
import com.lambda.event.events.ScreenHandlerEvent;
import com.lambda.event.events.TickEvent;
import com.lambda.module.modules.player.Interact;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.ScreenHandlerProvider;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {
    @Shadow
    @Nullable
    public Screen currentScreen;

    @Inject(method = "tick", at = @At("HEAD"))
    void onTickPre(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Pre());
    }

    @Inject(method = "tick", at = @At("RETURN"))
    void onTickPost(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Post());
    }

    @Inject(method = "render", at = @At("HEAD"))
    void onLoopTickPre(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Render.Pre());
    }

    @Inject(method = "render", at = @At("RETURN"))
    void onLoopTickPost(CallbackInfo ci) {
        EventFlow.post(new TickEvent.Render.Post());
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

    @Inject(method = "setScreen", at = @At("HEAD"))
    private void onScreenOpen(@Nullable Screen screen, CallbackInfo ci) {
        if (screen == null) return;
        if (screen instanceof ScreenHandlerProvider<?> handledScreen) {
            EventFlow.post(new ScreenHandlerEvent.Open(handledScreen.getScreenHandler()));
        }
    }

    @Inject(method = "setScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;removed()V", shift = At.Shift.AFTER))
    private void onScreenRemove(@Nullable Screen screen, CallbackInfo ci) {
        if (currentScreen == null) return;
        if (currentScreen instanceof ScreenHandlerProvider<?> handledScreen) {
            EventFlow.post(new ScreenHandlerEvent.Close(handledScreen.getScreenHandler()));
        }
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
