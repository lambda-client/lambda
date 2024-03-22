package com.lambda.mixin;

import com.lambda.util.DebugInfoHud;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "getRightText", at = @At(value = "TAIL"))
    private void onGetRightText(CallbackInfoReturnable<List<String>> cir) {
        DebugInfoHud.addDebugInfo(cir.getReturnValue());
    }
}
