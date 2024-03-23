package com.lambda.mixin.input;

import com.lambda.event.EventFlow;
import com.lambda.event.events.InputEvent;
import net.minecraft.client.input.KeyboardInput;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(KeyboardInput.class)
public class KeyboardInputMixin {
    @Inject(method = "tick", at = @At("TAIL"))
    private void onTick(boolean slowDown, float slowDownFactor, CallbackInfo ci) {
        EventFlow.post(new InputEvent());
    }
}
