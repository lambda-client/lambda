
package com.minato.mixin.network;

import com.minato.event.EventFlow;
import com.minato.event.events.ConnectionEvent;
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
        EventFlow.post(new ConnectionEvent.Connect.Login.EncryptionResponse(secretKey, publicKey, nonce));
    }
}
