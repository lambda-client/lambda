package me.zeroeightsix.kami.mixin.client;

import me.zeroeightsix.kami.KamiMod;
import me.zeroeightsix.kami.gui.mc.KamiGuiUpdateNotification;
import me.zeroeightsix.kami.util.Wrapper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu {

    //TODO: GUI doesn't display with these here displayGuiScreen calls.
    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    public void onActionPerformed(GuiButton btn, CallbackInfo callbackInfo) {
        if (!KamiMod.isLatest) {
            if (btn.id == 1) {
                KamiMod.log.debug("Singleplayer Clicked!");
                Wrapper.getMinecraft().displayGuiScreen(new KamiGuiUpdateNotification("BRuh", "fiuehf", 1));

                return;
            }

            if (btn.id == 2) {
                KamiMod.log.debug("Multiplayer Clicked!");
                Wrapper.getMinecraft().displayGuiScreen(new KamiGuiUpdateNotification("BRuh", "mult", 2));

                return;
            }
        }
    }

}
