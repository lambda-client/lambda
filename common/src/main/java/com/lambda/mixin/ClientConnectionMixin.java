package com.lambda.mixin;

import com.lambda.event.EventFlow;
import com.lambda.event.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.packet.Packet;
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
        PacketEvent.Send.Pre event = new PacketEvent.Send.Pre(packet);
        EventFlow.post(event);
        if (event.isCanceled()) callbackInfo.cancel();
    }

    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("RETURN"))
    private void sendingPacketPost(Packet<?> packet, final CallbackInfo callbackInfo) {
        EventFlow.post(new PacketEvent.Send.Post(packet));
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true, require = 1)
    private void receivingPacket(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        if (side != NetworkSide.CLIENTBOUND) return;

        PacketEvent.Receive.Pre event = new PacketEvent.Receive.Pre(packet);
        EventFlow.post(event);
        if (event.isCanceled()) callbackInfo.cancel();
    }

    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.AFTER))
    private void receivingPacketPost(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        if (side != NetworkSide.CLIENTBOUND) return;

        EventFlow.post(new PacketEvent.Receive.Post(packet));
    }
}
