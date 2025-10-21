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

package com.lambda.mixin.render;

import com.lambda.event.EventFlow;
import com.lambda.event.events.InventoryEvent;
import com.lambda.module.modules.player.EasyTrash;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {
    @Inject(method = "updateSlotStacks", at = @At("TAIL"))
    private void onUpdateSlotStacksHead(int revision, List<ItemStack> stacks, ItemStack cursorStack, CallbackInfo ci) {
        EventFlow.post(new InventoryEvent.FullUpdate(revision, stacks, cursorStack));
    }

    @WrapMethod(method = "internalOnSlotClick(IILnet/minecraft/screen/slot/SlotActionType;Lnet/minecraft/entity/player/PlayerEntity;)V")
    private void onInternalOnSlotClick(int slotIndex, int button, SlotActionType actionType, PlayerEntity player, Operation<Void> original) {
        if (EasyTrash.onClick((ScreenHandler) (Object) this, slotIndex, button, actionType, player)) {
            original.call(slotIndex, button, actionType, player);
        }
    }
}
