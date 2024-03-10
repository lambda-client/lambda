package com.lambda.common.mixin;

import com.lambda.common.event.EventFlow;
import com.lambda.common.event.events.KeyPressEvent;
import net.minecraft.client.Keyboard;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Keyboard.class)
public class KeyboardMixin {
    @Inject(method = "onKey", at = @At("HEAD"))
    void onKey(long window, int key, int scancode, int action, int modifiers, CallbackInfo ci) {
        if (key <= 0) return;
        if (action != 1) return;
        EventFlow.post(new KeyPressEvent(key));
    }
}
