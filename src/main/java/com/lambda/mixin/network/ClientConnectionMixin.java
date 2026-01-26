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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
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

@Mixin(ClientConnection.class)
public class ClientConnectionMixin {
    @Shadow
    @Final
    private NetworkSide side;

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "send(Lnet/minecraft/network/packet/Packet;)V")
    private void sendingPacket(Packet<?> packet, Operation<Void> original) {
        if (side != NetworkSide.CLIENTBOUND) return;

        if (!EventFlow.post(new PacketEvent.Send.Pre((Packet<? extends ServerPlayPacketListener>) packet)).isCanceled()) {
            original.call(packet);
            EventFlow.post(new PacketEvent.Send.Post((Packet<? extends ServerPlayPacketListener>) packet));
        }
    }

    @SuppressWarnings("unchecked")
    @WrapMethod(method = "channelRead0(Lio/netty/channel/ChannelHandlerContext;Lnet/minecraft/network/packet/Packet;)V", require = 1)
    private void receivingPacket(ChannelHandlerContext channelHandlerContext, Packet<?> packet, Operation<Void> original) {
        if (side != NetworkSide.CLIENTBOUND) return;

        if (!EventFlow.post(new PacketEvent.Receive.Pre((Packet<? extends ClientPlayPacketListener>) packet)).isCanceled()) {
            original.call(channelHandlerContext, packet);
            EventFlow.post(new PacketEvent.Receive.Post((Packet<? extends ClientPlayPacketListener>) packet));
        }
    }

    @WrapMethod(method = "connect(Ljava/lang/String;ILnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/state/NetworkState;Lnet/minecraft/network/listener/ClientPacketListener;Lnet/minecraft/network/packet/c2s/handshake/ConnectionIntent;)V")
    private <S extends ServerPacketListener, C extends ClientPacketListener>
    void onConnect(String address, int port, NetworkState<S> outboundState, NetworkState<C> inboundState, C prePlayStateListener, ConnectionIntent intent, Operation<Void> original) {
        if (!EventFlow.post(new ConnectionEvent.Connect.Pre(address, port, prePlayStateListener, intent)).isCanceled())
            original.call(address, port, outboundState, inboundState, prePlayStateListener, intent);
    }

    @WrapMethod(method = "disconnect(Lnet/minecraft/text/Text;)V")
    private void onDisconnect(Text disconnectReason, Operation<Void> original) {
        original.call(disconnectReason);
        EventFlow.post(new ConnectionEvent.Disconnect(disconnectReason));
    }
}
