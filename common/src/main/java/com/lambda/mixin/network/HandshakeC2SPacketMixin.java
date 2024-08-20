package com.lambda.mixin.network;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ConnectionEvent;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.packet.c2s.handshake.HandshakeC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(HandshakeC2SPacket.class)
public class HandshakeC2SPacketMixin {
    @Inject(method = "<init>(ILjava/lang/String;ILnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V", at = @At("TAIL"))
    private void onHandshakeC2SPacket(int i, String string, int j, ConnectionIntent connectionIntent, CallbackInfo ci) {
        EventFlow.post(new ConnectionEvent.Connect.Handshake(i, string, j, connectionIntent));
    }
}
