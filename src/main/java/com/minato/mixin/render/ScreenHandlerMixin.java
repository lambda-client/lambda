
package com.minato.mixin.render;

import com.minato.event.EventFlow;
import com.minato.event.events.InventoryEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.screen.ScreenHandler;
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
}
