package com.lambda.client.mixin.client.gui;

import com.lambda.client.LambdaMod;
import com.lambda.client.gui.mc.LambdaAltMenu;
import net.minecraft.client.gui.GuiButton;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;

import java.io.IOException;

@Mixin({GuiMainMenu.class})
public class MixinGuiMainMenu extends GuiScreen {

    /*
    @Inject(method = "initGui", at = @At("HEAD"))
    public void initGui(CallbackInfo ci) {
            this.buttonList.add(new GuiButton(3252, 2, 24, 98, 20, "Account Manager"));
    }
    */

    @Inject(method = "drawScreen", at = @At("TAIL"), cancellable = true)
    public void drawText(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        FontRenderer fr = mc.fontRenderer;
        fr.drawStringWithShadow(LambdaMod.NAME + TextFormatting.WHITE + " Version " + TextFormatting.GRAY + LambdaMod.VERSION, 2, 2, 0xffffff);
        fr.drawStringWithShadow(TextFormatting.BLUE + LambdaMod.WEBSITE_LINK,  2, 12, 0xffffff);
    }

    /*
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 3252) {
            mc.displayGuiScreen(new LambdaAltMenu());
        } else {
            super.actionPerformed(button);
        }
    }
     */
}