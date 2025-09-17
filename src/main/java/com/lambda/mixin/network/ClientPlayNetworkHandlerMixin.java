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
import com.lambda.event.events.InventoryEvent;
import com.lambda.event.events.WorldEvent;
import com.lambda.module.modules.movement.Velocity;
import com.lambda.module.modules.render.NoRender;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @Inject(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V", at = @At("TAIL"))
    void injectJoinPacket(GameJoinS2CPacket packet, CallbackInfo ci) {
        EventFlow.post(new WorldEvent.Join());
    }

    @Inject(method = "handlePlayerListAction(Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket$Action;Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket$Entry;Lnet/minecraft/client/network/PlayerListEntry;)V", at = @At("TAIL"))
    void injectPlayerList(PlayerListS2CPacket.Action action, PlayerListS2CPacket.Entry receivedEntry, PlayerListEntry currentEntry, CallbackInfo ci) {
        if (action != PlayerListS2CPacket.Action.UPDATE_LISTED) return;

        var name = currentEntry.getProfile().getName();
        var uuid = currentEntry.getProfile().getId();

        if (receivedEntry.listed()) {
            EventFlow.post(new WorldEvent.Player.Join(name, uuid, currentEntry));
        } else EventFlow.post(new WorldEvent.Player.Leave(name, uuid, currentEntry));
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/util/thread/ThreadExecutor;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onUpdateSelectedSlot(UpdateSelectedSlotS2CPacket packet, CallbackInfo ci) {
        if (EventFlow.post(new InventoryEvent.HotbarSlot.Sync(packet.slot())).isCanceled()) ci.cancel();
    }

    @Inject(method = "onScreenHandlerSlotUpdate", at = @At("TAIL"))
    private void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, CallbackInfo ci) {
        EventFlow.post(new InventoryEvent.SlotUpdate(packet.getSyncId(), packet.getRevision(), packet.getSlot(), packet.getStack()));
    }

    /**
     * Sets displayedUnsecureChatWarning to {@link NoRender#getNoChatVerificationToast()}
     * <pre>{@code
     * this.secureChatEnforced = packet.enforcesSecureChat();
     * if (this.serverInfo != null && !this.displayedUnsecureChatWarning && !this.isSecureChatEnforced()) {
     * SystemToast systemToast = SystemToast.create(this.client, SystemToast.Type.UNSECURE_SERVER_WARNING, UNSECURE_SERVER_TOAST_TITLE, UNSECURE_SERVER_TOAST_TEXT);
     * this.client.getToastManager().add(systemToast);
     * this.displayedUnsecureChatWarning = true;
     * }
     * }</pre>
     */
    @Redirect(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;displayedUnsecureChatWarning:Z", ordinal = 0))
    public boolean onServerMetadata(ClientPlayNetworkHandler clientPlayNetworkHandler) {
        return NoRender.getNoChatVerificationToast() && NoRender.INSTANCE.isEnabled();
    }

    /**
     * Cancels the player velocity if {@link Velocity#getExplosion()} is true
     * <pre>{@code
     * 	public void onExplosion(ExplosionS2CPacket packet) {
     * 		NetworkThreadUtils.forceMainThread(packet, this, this.client);
     * 		Vec3d vec3d = packet.center();
     * 		this.client
     * 			.world
     * 			.playSoundClient(
     * 				vec3d.getX(),
     * 				vec3d.getY(),
     * 				vec3d.getZ(),
     * 				packet.explosionSound().value(),
     * 				SoundCategory.BLOCKS,
     * 				4.0F,
     * 				(1.0F + (this.client.world.random.nextFloat() - this.client.world.random.nextFloat()) * 0.2F) * 0.7F,
     * 				false
     * 			);
     * 		this.client.world.addParticleClient(packet.explosionParticle(), vec3d.getX(), vec3d.getY(), vec3d.getZ(), 1.0, 0.0, 0.0);
     * 		packet.playerKnockback().ifPresent(this.client.player::addVelocityInternal);
     * }
     * }</pre>
     */
    @Inject(method = "onExplosion(Lnet/minecraft/network/packet/s2c/play/ExplosionS2CPacket;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/packet/s2c/play/ExplosionS2CPacket;playerKnockback()Ljava/util/Optional;"), cancellable = true)
    void injectVelocity(ExplosionS2CPacket packet, CallbackInfo ci) {
        if (Velocity.getExplosion() && Velocity.INSTANCE.isEnabled()) ci.cancel();
    }
}
