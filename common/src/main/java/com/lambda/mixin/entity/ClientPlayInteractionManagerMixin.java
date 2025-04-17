/*
 * Copyright 2024 Lambda
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

package com.lambda.mixin.entity;

import com.lambda.event.EventFlow;
import com.lambda.event.events.InventoryEvent;
import com.lambda.event.events.PlayerEvent;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayInteractionManagerMixin {

    @Shadow
    public float currentBreakingProgress;

    @Inject(method = "interactBlock", at = @At("HEAD"), cancellable = true)
    public void interactBlockHead(final ClientPlayerEntity player, final Hand hand, final BlockHitResult hitResult, final CallbackInfoReturnable<ActionResult> cir) {
        if (EventFlow.post(new PlayerEvent.Interact.Block(hand, hitResult)).isCanceled()) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "interactEntityAtLocation", at = @At("HEAD"), cancellable = true)
    public void interactEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (EventFlow.post(new PlayerEvent.Interact.Entity(hand, entity, hitResult)).isCanceled()) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "interactItem", at = @At("HEAD"), cancellable = true)
    public void interactItemHead(PlayerEntity player, Hand hand, CallbackInfoReturnable<ActionResult> cir) {
        if (EventFlow.post(new PlayerEvent.Interact.Item(hand)).isCanceled()) {
            cir.setReturnValue(ActionResult.FAIL);
        }
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    public void onAttackBlock(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (EventFlow.post(new PlayerEvent.Attack.Block(pos, side)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    void onAttackPre(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (EventFlow.post(new PlayerEvent.Attack.Entity(target)).isCanceled()) ci.cancel();
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void clickSlotHead(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (syncId != player.currentScreenHandler.syncId) return;
        var click = new PlayerEvent.SlotClick(syncId, slotId, button, actionType, player.currentScreenHandler);
        if (EventFlow.post(click).isCanceled()) ci.cancel();
    }

    /**
     * Posts {@link InventoryEvent.HotbarSlot.Update} and returns the event value as the selected slot
     * <pre>{@code
     * private void syncSelectedSlot() {
     *     int i = this.client.player.getInventory().selectedSlot;
     *     if (i != this.lastSelectedSlot) {
     *         this.lastSelectedSlot = i;
     *         this.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(this.lastSelectedSlot));
     *     }
     * }
     * }</pre>
     */
    @Redirect(method = "syncSelectedSlot", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I"))
    public int overrideSelectedSlotSync(PlayerInventory instance) {
        return EventFlow.post(new InventoryEvent.HotbarSlot.Update(instance.selectedSlot)).getSlot();
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void updateBlockBreakingProgressPre(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        var event = EventFlow.post(new PlayerEvent.Breaking.Update(pos, side, currentBreakingProgress));
        if (event.isCanceled()) cir.setReturnValue(false);

        currentBreakingProgress = event.getProgress();
    }

    @Inject(method = "cancelBlockBreaking", at = @At("HEAD"), cancellable = true)
    private void cancelBlockBreakingPre(CallbackInfo ci) {
        if (EventFlow.post(new PlayerEvent.Breaking.Cancel(currentBreakingProgress)).isCanceled()) ci.cancel();
    }
}
