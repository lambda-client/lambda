package com.lambda.client.mixin.client.gui;

import com.lambda.client.gui.mc.LambdaGuiAntiDisconnect;
import com.lambda.client.gui.mc.LambdaGuiPluginManager;
import com.lambda.client.module.modules.misc.AntiDisconnect;
import com.lambda.client.util.Wrapper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;

@Mixin(GuiIngameMenu.class)
public class MixinGuiIngameMenu extends GuiScreen {

    @Inject(method = "initGui", at = @At("RETURN"))
    public void initGui(CallbackInfo ci) {
        GuiButton removeMe = null;
        for (GuiButton button: buttonList) {
            if (button.id == 7 && !button.enabled) {
                removeMe = button;
            }
        }
        if (removeMe != null) {
            buttonList.add(new GuiButton(11000, width / 2 - 100, height / 4 + 72 + -16, "Lambda"));
            buttonList.remove(removeMe);
        }
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    public void actionPerformed(GuiButton button, CallbackInfo callbackInfo) {
        if (button.id == 1) {
            if (AntiDisconnect.INSTANCE.isEnabled()) {
                Wrapper.getMinecraft().displayGuiScreen(new LambdaGuiAntiDisconnect());

                callbackInfo.cancel();
            }
        } else if (button.id == 11000) {
            mc.displayGuiScreen(new LambdaGuiPluginManager(this));
        }
    }
}
