package com.lambda.mixin.render;

import com.lambda.event.EventFlow;
import com.lambda.event.events.ScreenEvent;
import com.lambda.event.events.ScreenHandlerEvent;
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
        EventFlow.post(new ScreenHandlerEvent.Loaded(revision, stacks, cursorStack));
    }
}
