package com.lambda.mixin.network;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ConnectionEvent;
import net.minecraft.client.network.ClientLoginNetworkHandler;
import net.minecraft.network.packet.s2c.login.LoginSuccessS2CPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientLoginNetworkHandler.class)
public class ClientLoginNetworkMixin {

    @Inject(method = "onSuccess(Lnet/minecraft/network/packet/s2c/login/LoginSuccessS2CPacket;)V", at = @At("HEAD"))
    private void onSuccess(LoginSuccessS2CPacket packet, CallbackInfo ci) {
        EventFlow.post(new ConnectionEvent.Connect.Post(packet.getProfile()));
    }
}
