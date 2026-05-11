/*
 * Copyright 2026 Lambda
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
