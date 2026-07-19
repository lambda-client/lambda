
package com.minato.mixin.render;

import com.minato.module.modules.render.ContainerPreview;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void onMouseClicked(Click click, boolean doubled, CallbackInfoReturnable<Boolean> cir) {
        if (ContainerPreview.INSTANCE.isEnabled() && ContainerPreview.isLocked()) {
            if (ContainerPreview.isMouseOverLockedTooltip((int) click.x(), (int) click.y())) {
                cir.setReturnValue(true);
            }
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
