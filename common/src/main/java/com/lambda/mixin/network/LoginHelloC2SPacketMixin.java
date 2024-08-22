package com.lambda.mixin.network;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ConnectionEvent;
import net.minecraft.network.packet.c2s.login.LoginHelloC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(LoginHelloC2SPacket.class)
public class LoginHelloC2SPacketMixin {
    @Inject(method = "<init>(Ljava/lang/String;Ljava/util/UUID;)V", at = @At("TAIL"))
    private void onLoginHelloC2SPacket(String string, UUID uUID, CallbackInfo ci) {
        EventFlow.post(new ConnectionEvent.Connect.Login.Hello(string, uUID));
    }
}
