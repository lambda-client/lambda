package com.lambda.mixin.network;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ConnectionEvent;
import net.minecraft.network.packet.c2s.login.LoginKeyC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.crypto.SecretKey;
import java.security.PublicKey;

@Mixin(LoginKeyC2SPacket.class)
public class LoginKeyC2SPacketMixin {
    @Inject(method = "<init>(Ljavax/crypto/SecretKey;Ljava/security/PublicKey;[B)V", at = @At("TAIL"))
    private void onLoginKeyC2SPacket(SecretKey secretKey, PublicKey publicKey, byte[] nonce, CallbackInfo ci) {
        // Please note this won't work if the server is offline mode because the player doesn't
        // fetch the server's public key.
        EventFlow.post(new ConnectionEvent.Connect.Login.Key(secretKey, publicKey, nonce));
    }
}
