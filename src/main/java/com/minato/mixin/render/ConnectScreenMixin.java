
package com.minato.mixin.render;

import com.minato.module.modules.combat.autodisconnect.AutoDisconnect;
import com.minato.module.modules.combat.autodisconnect.MultiplayerReconnectTarget;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.network.CookieStorage;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ConnectScreen.class)
public class ConnectScreenMixin {

    @Inject(method = "connect(Lnet/minecraft/client/MinecraftClient;Lnet/minecraft/client/network/ServerAddress;Lnet/minecraft/client/network/ServerInfo;Lnet/minecraft/client/network/CookieStorage;)V", at= @At("HEAD"))
    private void connectHead(MinecraftClient client, ServerAddress address, ServerInfo info, CookieStorage cookieStorage, CallbackInfo ci) {
        AutoDisconnect.INSTANCE.setLastReconnectTarget(new MultiplayerReconnectTarget(address, info, cookieStorage));
    }
}
