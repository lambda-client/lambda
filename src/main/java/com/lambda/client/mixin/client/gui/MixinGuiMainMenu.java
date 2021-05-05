package com.lambda.client.mixin.client.gui;

import com.lambda.client.LambdaMod;
import com.lambda.client.gui.mc.LambdaGuiPluginManager;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.util.text.TextFormatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;

@Mixin(GuiMainMenu.class)
public class MixinGuiMainMenu extends GuiScreen {

    @Shadow private GuiButton realmsButton;

    @Inject(method = "initGui", at = @At("TAIL"), cancellable = true)
    public void initGui(CallbackInfo ci) {
        buttonList.removeIf(button -> button.id == 14);
        realmsButton = addButton(new GuiButton(9001, width / 2 + 2, height / 4 + 48 + 24 * 2, 98, 20, "Lambda"));
    }

    @Inject(method = "drawScreen", at = @At("TAIL"), cancellable = true)
    public void drawText(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        FontRenderer fr = mc.fontRenderer;
        fr.drawStringWithShadow(TextFormatting.WHITE + LambdaMod.NAME + " " + TextFormatting.GRAY + LambdaMod.VERSION, 2, 2, 0x808080);
        fr.drawString(TextFormatting.GRAY + LambdaMod.WEBSITE_LINK,  2, 12, 0xffffff);
    }

    @Inject(method = "actionPerformed", at = @At("TAIL"), cancellable = true)
    protected void actionPerformed(GuiButton button, CallbackInfo ci) throws IOException {
        if (button.id == 9001) {
            mc.displayGuiScreen(new LambdaGuiPluginManager(this));
        } else {
            super.actionPerformed(button);
        }
    }
}