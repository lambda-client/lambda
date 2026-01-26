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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
import org.spongepowered.asm.mixin.Mixin;

import java.util.List;

@Mixin(ScreenHandler.class)
public class ScreenHandlerMixin {
    @WrapMethod(method = "updateSlotStacks")
    private void onUpdateSlotStacksHead(int revision, List<ItemStack> stacks, ItemStack cursorStack, Operation<Void> original) {
        original.call(revision, stacks, cursorStack);
        EventFlow.post(new InventoryEvent.FullUpdate(revision, stacks, cursorStack));
    }
}
