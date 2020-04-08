package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.KamiMod;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu {

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    public void onActionPerformed(GuiButton btn, CallbackInfo callbackInfo) {
        if (btn.id == 1) {
            KamiMod.log.info("Single Player Clicked!");
        }
    }

}
