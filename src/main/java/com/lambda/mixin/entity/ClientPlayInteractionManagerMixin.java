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

package com.lambda.mixin.entity;

import com.lambda.event.EventFlow;
import com.lambda.event.events.InventoryEvent;
import com.lambda.event.events.PlayerEvent;
import com.lambda.interaction.managers.inventory.InventoryManager;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.client.recipebook.ClientRecipeBook;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.screen.slot.SlotActionType;
import net.minecraft.stat.StatHandler;
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
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientPlayerInteractionManager.class)
public class ClientPlayInteractionManagerMixin {
    @Shadow
    public float currentBreakingProgress;

    @Shadow
    public int lastSelectedSlot;

    @WrapMethod(method = "interactBlock")
    public ActionResult interactBlockHead(ClientPlayerEntity player, Hand hand, BlockHitResult hitResult, Operation<ActionResult> original) {
        if (EventFlow.post(new PlayerEvent.Interact.Block(hand, hitResult)).isCanceled())
            return ActionResult.FAIL;

        return original.call(player, hand, hitResult);
    }

    @WrapMethod(method = "interactEntityAtLocation")
    public ActionResult interactEntityAtLocation(PlayerEntity player, Entity entity, EntityHitResult hitResult, Hand hand, Operation<ActionResult> original) {
        if (EventFlow.post(new PlayerEvent.Interact.Entity(hand, entity, hitResult)).isCanceled())
            return ActionResult.FAIL;

        return original.call(player, entity, hitResult, hand);
    }

    @WrapMethod(method = "interactItem")
    public ActionResult interactItemHead(PlayerEntity player, Hand hand, Operation<ActionResult> original) {
        if (EventFlow.post(new PlayerEvent.Interact.Item(hand)).isCanceled())
            return ActionResult.FAIL;

        return original.call(player, hand);
    }

    @WrapMethod(method = "attackBlock")
    public boolean onAttackBlock(BlockPos pos, Direction direction, Operation<Boolean> original) {
        if (EventFlow.post(new PlayerEvent.Attack.Block(pos, direction)).isCanceled())
            return false;

        return original.call(pos, direction);
    }

    @WrapMethod(method = "attackEntity")
    void onAttackPre(PlayerEntity player, Entity target, Operation<Void> original) {
        if (EventFlow.post(new PlayerEvent.Attack.Entity(target)).isCanceled())
            return;

        original.call(player, target);
    }

    @WrapMethod(method = "clickSlot")
    public void clickSlotHead(int syncId, int slotId, int button, SlotActionType actionType, PlayerEntity player, Operation<Void> original) {
        var click = new PlayerEvent.SlotClick(syncId, slotId, button, actionType, player.currentScreenHandler);

        if (EventFlow.post(click).isCanceled())
            return;

        if (syncId != player.currentScreenHandler.syncId)
            original.call(syncId, slotId, button, actionType, player);
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
    @Inject(method = "syncSelectedSlot", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayNetworkHandler;sendPacket(Lnet/minecraft/network/packet/Packet;)V", shift = At.Shift.BEFORE))
    public void overrideSelectedSlotSync(CallbackInfo ci) {
        EventFlow.post(new InventoryEvent.HotbarSlot.Update(lastSelectedSlot));
    }

    @WrapMethod(method = "updateBlockBreakingProgress")
    private boolean updateBlockBreakingProgressPre(BlockPos pos, Direction direction, Operation<Boolean> original) {
        var event = EventFlow.post(new PlayerEvent.Breaking.Update(pos, direction, currentBreakingProgress));

        if (event.isCanceled())
            return false;

        currentBreakingProgress = event.getProgress();

        return original.call(pos, direction);
    }

    @WrapMethod(method = "cancelBlockBreaking")
    private void cancelBlockBreakingPre(Operation<Void> original) {
        if (!EventFlow.post(new PlayerEvent.Breaking.Cancel(currentBreakingProgress)).isCanceled())
            original.call();
    }

    @WrapMethod(method = "createPlayer(Lnet/minecraft/client/world/ClientWorld;Lnet/minecraft/stat/StatHandler;Lnet/minecraft/client/recipebook/ClientRecipeBook;)Lnet/minecraft/client/network/ClientPlayerEntity;")
    private ClientPlayerEntity wrapCreatePlayer(ClientWorld world, StatHandler statHandler, ClientRecipeBook recipeBook, Operation<ClientPlayerEntity> original) {
        var player = original.call(world, statHandler, recipeBook);
        InventoryManager.INSTANCE.setScreenHandler(player.playerScreenHandler);
        return player;
    }
}
