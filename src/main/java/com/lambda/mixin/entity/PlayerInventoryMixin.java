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

import com.lambda.interaction.request.hotbar.HotbarManager;
import com.lambda.interaction.request.hotbar.HotbarRequest;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.entity.player.PlayerInventory;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PlayerInventory.class)
public class PlayerInventoryMixin {
    @SuppressWarnings({"MixinAnnotationTarget", "UnresolvedMixinReference"})
    @ModifyExpressionValue(method = "*", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerInventory;selectedSlot:I", opcode = Opcodes.GETFIELD))
    private int modifySelectedSlot(int original) {
        final HotbarRequest hotbarRequest = HotbarManager.INSTANCE.getActiveRequest();
        if (hotbarRequest == null) return original;
        return hotbarRequest.getSlot();
    }

    @Inject(method = "getSelectedSlot", at = @At("HEAD"), cancellable = true)
    private void redirectGetSelectedSlot(CallbackInfoReturnable<Integer> cir) {
        final HotbarRequest hotbarRequest = HotbarManager.INSTANCE.getActiveRequest();
        if (hotbarRequest == null) return;
        cir.setReturnValue(hotbarRequest.getSlot());
    }
}
