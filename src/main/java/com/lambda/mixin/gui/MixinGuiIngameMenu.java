package com.lambda.mixin.gui;

import com.lambda.client.gui.mc.LambdaGuiAntiDisconnect;
import com.lambda.client.module.modules.misc.AntiDisconnect;
import com.lambda.client.util.Wrapper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiMultiplayer;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngameMenu.class)
public class MixinGuiIngameMenu extends GuiScreen {
    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    public void actionPerformed(GuiButton button, CallbackInfo callbackInfo) {
        switch (button.id) {
            case 1:
                if (AntiDisconnect.INSTANCE.isEnabled()) {
                    Wrapper.getMinecraft().displayGuiScreen(new LambdaGuiAntiDisconnect());
                    callbackInfo.cancel();
                }
                break;
            case Integer.MIN_VALUE:
                Wrapper.getMinecraft().displayGuiScreen(new GuiMultiplayer(this));
                break;
            default:
                break;
        }
    }

    @Inject(method = "initGui", at = @At("RETURN"))
    public void initGui(CallbackInfo ci) {
        if (!mc.isSingleplayer()) {
            GuiButton openToLanButton = buttonList.remove(4);
            buttonList.add(new GuiButton(Integer.MIN_VALUE, openToLanButton.x, openToLanButton.y, openToLanButton.width, openToLanButton.height, "Server List"));
        }
    }
}
