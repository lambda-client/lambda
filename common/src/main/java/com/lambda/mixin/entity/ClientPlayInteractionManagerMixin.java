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
import com.lambda.event.events.AttackEvent;
import com.lambda.event.events.InteractionEvent;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayInteractionManagerMixin {

    @Shadow
    public float currentBreakingProgress;
    @Final
    @Shadow
    private MinecraftClient client;

    @Inject(method = "interactBlock", at = @At("HEAD"))
    public void interactBlockHead(final ClientPlayerEntity player, final Hand hand, final BlockHitResult hitResult, final CallbackInfoReturnable<ActionResult> cir) {
        if (client.world == null) return;
        EventFlow.post(new InteractionEvent.Block(client.world, hitResult));
    }

    @Inject(method = "clickSlot", at = @At("HEAD"), cancellable = true)
    public void clickSlotHead(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, CallbackInfo ci) {
        if (syncId != player.currentScreenHandler.syncId) return;
        var click = new InteractionEvent.SlotClick(syncId, slotId, button, actionType, player.currentScreenHandler);
        if (EventFlow.post(click).isCanceled()) ci.cancel();
    }

    @Inject(method = "attackEntity", at = @At("HEAD"), cancellable = true)
    void onAttackPre(PlayerEntity player, Entity target, CallbackInfo ci) {
        if (EventFlow.post(new AttackEvent.Pre(target)).isCanceled()) ci.cancel();
    }

    @Inject(method = "attackEntity", at = @At("TAIL"))
    void onAttackPost(PlayerEntity player, Entity target, CallbackInfo ci) {
        EventFlow.post(new AttackEvent.Post(target));
    }

    @Inject(method = "attackBlock", at = @At("HEAD"), cancellable = true)
    public void onAttackBlock(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        if (EventFlow.post(new InteractionEvent.BlockAttack.Pre(pos, side)).isCanceled()) cir.cancel();
    }

    @Inject(method = "attackBlock", at = @At("TAIL"))
    public void onAttackBlockPost(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        EventFlow.post(new InteractionEvent.BlockAttack.Post(pos, side));
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("HEAD"), cancellable = true)
    private void updateBlockBreakingProgressPre(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        var event = EventFlow.post(new InteractionEvent.BreakingProgress.Pre(pos, side, currentBreakingProgress));
        if (event.isCanceled()) cir.cancel();

        currentBreakingProgress = event.getProgress();
    }

    @Inject(method = "updateBlockBreakingProgress", at = @At("TAIL"))
    private void updateBlockBreakingProgressPost(BlockPos pos, Direction side, CallbackInfoReturnable<Boolean> cir) {
        EventFlow.post(new InteractionEvent.BreakingProgress.Post(pos, side, currentBreakingProgress));
    }
}
