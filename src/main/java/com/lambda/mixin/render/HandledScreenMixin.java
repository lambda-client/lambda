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
import com.lambda.module.modules.render.ContainerPreview;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.screen.ScreenHandler;
import net.minecraft.screen.slot.Slot;
import net.minecraft.screen.slot.SlotActionType;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenMixin<T extends ScreenHandler> {
    @Final
    @Shadow
    protected T handler;

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerPreview.INSTANCE.isEnabled() && ContainerPreview.isLocked()) {
            if (ContainerPreview.isMouseOverLockedTooltip((int) click.x(), (int) click.y())) {
                cir.setReturnValue(true);
            }
        }
    }

    @WrapMethod(method = "onMouseClick(Lnet/minecraft/screen/slot/Slot;IILnet/minecraft/screen/slot/SlotActionType;)V")
    private void onMouseClickSlotPre(Slot slot, int slotId, int button, SlotActionType actionType, Operation<Void> original) {
        if (!EventFlow.post(new InventoryEvent.SlotAction.Click(handler, slot, slotId, button, actionType)).isCanceled()) {
            int slotIdFinal = slot == null ? slotId : slot.id;
            original.call(slot, slotIdFinal, button, actionType);
        }
    }

    @Inject(method = "mouseReleased", at = @At("HEAD"), cancellable = true)
    private void onMouseReleased(Click click, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerPreview.INSTANCE.isEnabled() && ContainerPreview.isLocked()) {
            if (ContainerPreview.isMouseOverLockedTooltip((int) click.x(), (int) click.y())) {
                cir.setReturnValue(true);
            }
        }
    }
}
