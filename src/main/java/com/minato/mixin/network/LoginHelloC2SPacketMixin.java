
package com.minato.mixin.network;

import com.minato.event.EventFlow;
import com.minato.event.events.ConnectionEvent;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.encryption.NetworkEncryptionException;
import net.minecraft.network.packet.s2c.login.LoginHelloS2CPacket;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.security.PublicKey;

@Mixin(LoginHelloS2CPacket.class)
public abstract class LoginHelloC2SPacketMixin {
    @Shadow @Final private String serverId;

    @Shadow @Final private byte[] nonce;

    @Shadow public abstract PublicKey getPublicKey() throws NetworkEncryptionException;

    @Inject(method = "<init>(Lnet/minecraft/network/PacketByteBuf;)V", at = @At("TAIL"))
    private void onLoginHelloC2SPacket(PacketByteBuf buf, CallbackInfo ci) throws NetworkEncryptionException {
        EventFlow.post(new ConnectionEvent.Connect.Login.EncryptionRequest(serverId, getPublicKey(), nonce));
    }
}
