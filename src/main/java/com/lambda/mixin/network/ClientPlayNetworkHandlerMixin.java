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
import com.lambda.event.events.ChatEvent;
import com.lambda.event.events.InventoryEvent;
import com.lambda.event.events.WorldEvent;
import com.lambda.interaction.managers.inventory.InventoryManager;
import com.lambda.module.modules.movement.Velocity;
import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.packet.s2c.play.*;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayNetworkHandler.class)
public class ClientPlayNetworkHandlerMixin {
    @WrapMethod(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V")
    void injectJoinPacket(GameJoinS2CPacket packet, Operation<Void> original) {
        original.call(packet);
        EventFlow.post(new WorldEvent.Join());
    }

    @WrapMethod(method = "handlePlayerListAction(Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket$Action;Lnet/minecraft/network/packet/s2c/play/PlayerListS2CPacket$Entry;Lnet/minecraft/client/network/PlayerListEntry;)V")
    void injectPlayerList(PlayerListS2CPacket.Action action, PlayerListS2CPacket.Entry receivedEntry, PlayerListEntry currentEntry, Operation<Void> original) {
        if (action != PlayerListS2CPacket.Action.UPDATE_LISTED)
            original.call(action, receivedEntry, currentEntry);

        var name = currentEntry.getProfile().name();
        var uuid = currentEntry.getProfile().id();

        if (receivedEntry.listed()) EventFlow.post(new WorldEvent.Player.Join(name, uuid, currentEntry));
        else EventFlow.post(new WorldEvent.Player.Leave(name, uuid, currentEntry));
    }

    @Inject(method = "onUpdateSelectedSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/network/NetworkThreadUtils;forceMainThread(Lnet/minecraft/network/packet/Packet;Lnet/minecraft/network/listener/PacketListener;Lnet/minecraft/network/PacketApplyBatcher;)V", shift = At.Shift.AFTER), cancellable = true)
    private void onUpdateSelectedSlot(UpdateSelectedSlotS2CPacket packet, CallbackInfo ci) {
        if (EventFlow.post(new InventoryEvent.HotbarSlot.Sync(packet.slot())).isCanceled()) ci.cancel();
    }

    @WrapMethod(method = "onScreenHandlerSlotUpdate")
    private void onScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, Operation<Void> original) {
        original.call(packet);
        EventFlow.post(new InventoryEvent.SlotUpdate(packet.getSyncId(), packet.getRevision(), packet.getSlot(), packet.getStack()));
    }

    /**
     * Sets seenInsecureChatWarning to {@link NoRender#getNoChatVerificationToast()}
     * <pre>{@code
     * this.secureChatEnforced = packet.enforcesSecureChat();
     * if (this.serverInfo != null && !this.seenInsecureChatWarning && !this.isSecureChatEnforced()) {
     * SystemToast systemToast = SystemToast.create(this.client, SystemToast.Type.UNSECURE_SERVER_WARNING, UNSECURE_SERVER_TOAST_TITLE, UNSECURE_SERVER_TOAST_TEXT);
     * this.client.getToastManager().add(systemToast);
     * this.seenInsecureChatWarning = true;
     * }
     * }</pre>
     *
     * Note: In 1.21.11, displayedUnsecureChatWarning was renamed to seenInsecureChatWarning.
     */
    @ModifyExpressionValue(method = "onGameJoin(Lnet/minecraft/network/packet/s2c/play/GameJoinS2CPacket;)V", at = @At(value = "FIELD", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;seenInsecureChatWarning:Z", ordinal = 0))
    public boolean onServerMetadata(boolean original) {
        return (NoRender.getNoChatVerificationToast() && NoRender.INSTANCE.isEnabled()) || original;
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

    @WrapMethod(method = "onScreenHandlerSlotUpdate")
    private void wrapOnScreenHandlerSlotUpdate(ScreenHandlerSlotUpdateS2CPacket packet, Operation<Void> original) {
        InventoryManager.onSlotUpdate(packet, original);
    }

    @WrapMethod(method = "onInventory")
    private void wrapOnInventory(InventoryS2CPacket packet, Operation<Void> original) {
        InventoryManager.onInventoryUpdate(packet, original);
    }

    @WrapMethod(method = "sendChatMessage(Ljava/lang/String;)V")
    void onSendMessage(String content, Operation<Void> original) {
        var event = new ChatEvent.Send(content);

        if (!EventFlow.post(event).isCanceled())
            original.call(event.getMessage());
    }
}