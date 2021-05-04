package com.lambda.client.mixin.client.gui;

import com.lambda.client.gui.mc.LambdaGuiAntiDisconnect;
import com.lambda.client.module.modules.misc.AntiDisconnect;
import com.lambda.client.util.Wrapper;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiIngameMenu;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiIngameMenu.class)
public class MixinGuiIngameMenu {

//    @Inject(method = "initGui", at = @At("RETURN"))
//    public void initGui(CallbackInfo ci) {
//        buttonList.removeIf(button -> button.id == 14);
//        realmsButton = addButton(new GuiButton(9001, width / 2 + 2, height / 4 + 48 + 24 * 2, 98, 20, "Lambda"));
//    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    public void actionPerformed(GuiButton button, CallbackInfo callbackInfo) {
        if (button.id == 1) {
            if (AntiDisconnect.INSTANCE.isEnabled()) {
                Wrapper.getMinecraft().displayGuiScreen(new LambdaGuiAntiDisconnect());

                callbackInfo.cancel();
            }
        }
    }
}
