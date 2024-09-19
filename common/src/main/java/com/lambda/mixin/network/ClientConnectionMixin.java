package com.lambda.mixin.network;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ConnectionEvent;
import com.lambda.event.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.ClientPacketListener;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.listener.ServerPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    @Shadow
    @Final
    private NetworkSide side;

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendingPacket(Packet<?> packet, final CallbackInfo callbackInfo) {
        if (side != NetworkSide.SERVERBOUND) return;

        if (EventFlow.post(new PacketEvent.Send.Pre((Packet<ServerPacketListener>) packet)).isCanceled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("RETURN"))
    private void sendingPacketPost(Packet<?> packet, final CallbackInfo callbackInfo) {
        if (side != NetworkSide.SERVERBOUND) return;

        EventFlow.post(new PacketEvent.Send.Post((Packet<ServerPacketListener>) packet));
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.BEFORE), cancellable = true, require = 1)
    private void receivingPacket(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        if (side != NetworkSide.CLIENTBOUND) return;

        if (EventFlow.post(new PacketEvent.Receive.Pre((Packet<ClientPacketListener>) packet)).isCanceled()) {
            callbackInfo.cancel();
        }
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.AFTER))
    private void receivingPacketPost(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        if (side != NetworkSide.CLIENTBOUND) return;

        EventFlow.post(new PacketEvent.Receive.Post((Packet<ClientPacketListener>) packet));
    }

    @Inject(method = "connect(Ljava/lang/String;ILnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V", at = @At("HEAD"), cancellable = true)
    private void onConnect(
            String address,
            int port,
            PacketListener listener,
            ConnectionIntent intent,
            CallbackInfo ci
    ) {
        if (EventFlow.post(new ConnectionEvent.Connect.Pre(address, port, listener, intent)).isCanceled()) ci.cancel();
    }

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onDisconnect(Text reason, CallbackInfo ci) {
        EventFlow.post(new ConnectionEvent.Disconnect(reason));
    }
}
