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

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerInteractionManager;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import static net.minecraft.entity.player.PlayerInventory.isValidHotbarIndex;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @Shadow @Final private DefaultedList<ItemStack> main;

    @Shadow @Final public PlayerEntity player;

    @Inject(method = "getMainStacks", at = @At(value = "HEAD"), cancellable = true)
    public void handleSpoofedMainHandStack(CallbackInfoReturnable<ItemStack> cir) {
        MinecraftClient mc = MinecraftClient.getInstance();
        ClientPlayerInteractionManager interaction = mc.interactionManager;

        if (player != mc.player || interaction == null) return;

        int actualSlot = interaction.lastSelectedSlot;

        cir.setReturnValue(
                isValidHotbarIndex(actualSlot) ? main.get(actualSlot) : ItemStack.EMPTY
        );
    }
}
