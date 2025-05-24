/*
 * Copyright 2025 Lambda
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

package com.lambda.mixin.network;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ConnectionEvent;
import com.lambda.event.events.PacketEvent;
import io.netty.channel.ChannelHandlerContext;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.NetworkSide;
import net.minecraft.network.listener.ClientPacketListener;
import net.minecraft.network.listener.ClientPlayPacketListener;
import net.minecraft.network.listener.ServerPacketListener;
import net.minecraft.network.listener.ServerPlayPacketListener;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.handshake.ConnectionIntent;
import net.minecraft.network.state.NetworkState;
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

    @SuppressWarnings("unchecked")
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("HEAD"), cancellable = true)
    private void sendingPacket(Packet<?> packet, final CallbackInfo callbackInfo) {
        if (side != NetworkSide.CLIENTBOUND) return;

        if (EventFlow.post(new PacketEvent.Send.Pre((Packet<? extends ServerPlayPacketListener>) packet)).isCanceled()) {
            callbackInfo.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "send(Lnet/minecraft/network/packet/Packet;)V", at = @At("RETURN"))
    private void sendingPacketPost(Packet<?> packet, final CallbackInfo callbackInfo) {
        if (side != NetworkSide.CLIENTBOUND) return;

        EventFlow.post(new PacketEvent.Send.Post((Packet<? extends ServerPlayPacketListener>) packet));
    }

    @SuppressWarnings("all")
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.BEFORE), cancellable = true, require = 1)
    private void receivingPacket(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        if (side != NetworkSide.CLIENTBOUND) return;

        if (EventFlow.post(new PacketEvent.Receive.Pre((Packet<? extends ClientPlayPacketListener>) packet)).isCanceled()) {
            callbackInfo.cancel();
        }
    }

    @SuppressWarnings("unchecked")
    @Inject(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/ClientConnection;handlePacket(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;)V", shift = At.Shift.AFTER))
    private void receivingPacketPost(
            ChannelHandlerContext channelHandlerContext,
            Packet<?> packet,
            CallbackInfo callbackInfo
    ) {
        if (side != NetworkSide.CLIENTBOUND) return;

        EventFlow.post(new PacketEvent.Receive.Post((Packet<? extends ClientPlayPacketListener>) packet));
    }

    @Inject(method = "connect(Ljava/lang/String;ILnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/listener/ClientPacketListener;Lnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V", at = @At("HEAD"), cancellable = true)
    private
    <S extends ServerPacketListener, C extends ClientPacketListener>
    void onConnect(
            String address,
            int port,
            NetworkState<S> outboundState,
            NetworkState<C> inboundState,
            C prePlayStateListener,
            ConnectionIntent intent,
            CallbackInfo ci
    ) {
        if (EventFlow.post(new ConnectionEvent.Connect.Pre(address, port, prePlayStateListener, intent)).isCanceled()) ci.cancel();
    }

    @Inject(method = "disconnect(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onDisconnect(Text reason, CallbackInfo ci) {
        EventFlow.post(new ConnectionEvent.Disconnect(reason));
    }
}
