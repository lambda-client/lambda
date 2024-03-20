package com.lambda.mixin;

import com.lambda.module.modules.movement.Sprint;
import net.minecraft.client.option.KeyBinding;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Objects;

@Mixin(KeyBinding.class)
public class KeyBindingMixin {
    @Inject(method = "isPressed", at = @At("HEAD"), cancellable = true)
    void autoSprint(CallbackInfoReturnable<Boolean> cir) {
        KeyBinding instance = (KeyBinding) (Object) this;
        if (!Objects.equals(instance.getTranslationKey(), "key.sprint")) return;

        if (Sprint.INSTANCE.isEnabled()) cir.setReturnValue(true);
    }
}
