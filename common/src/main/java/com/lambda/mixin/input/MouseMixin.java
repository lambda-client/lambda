package com.lambda.mixin.input;

import com.lambda.module.modules.player.Freecam;
import net.minecraft.client.Mouse;
import net.minecraft.client.network.ClientPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(Mouse.class)
public class MouseMixin {
    @Redirect(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;changeLookDirection(DD)V"))
    private void updateMouseChangeLookDirection(ClientPlayerEntity player, double cursorDeltaX, double cursorDeltaY) {
        if (Freecam.INSTANCE.isEnabled()) {
            Freecam.updateRotation(cursorDeltaX, cursorDeltaY);
            return;
        }

        player.changeLookDirection(cursorDeltaX, cursorDeltaY);
    }
}
