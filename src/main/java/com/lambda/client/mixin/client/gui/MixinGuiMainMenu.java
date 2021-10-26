package com.lambda.client.mixin.client.gui;

import com.lambda.client.LambdaMod;
import com.lambda.client.gui.mc.LambdaGuiPluginManager;
import com.lambda.client.module.modules.client.MenuShader;
import com.lambda.client.util.WebUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    @Shadow private GuiButton realmsButton;
    private int widthVersion;
    private int widthVersionRest;

    @Inject(method = "initGui", at = @At("RETURN"))
    public void initGui$Inject$RETURN(CallbackInfo ci) {
        buttonList.removeIf(button -> button.id == 14);
        realmsButton = addButton(new GuiButton(9001, width / 2 + 2, height / 4 + 48 + 24 * 2, 98, 20, "Lambda"));
        MenuShader.reset();
    }

    @Inject(method = "actionPerformed", at = @At("HEAD"), cancellable = true)
    protected void actionPerformed$Inject$HEAD(GuiButton button, CallbackInfo ci) {
        if (button.id == 9001) {
            mc.displayGuiScreen(new LambdaGuiPluginManager(this));
            ci.cancel();
        }
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    public void drawScreen$Inject$RETURN(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        FontRenderer fr = fontRenderer;
        String slogan = TextFormatting.WHITE + LambdaMod.NAME + " " + TextFormatting.GRAY + LambdaMod.VERSION;
        String version;
        if (WebUtils.INSTANCE.isLatestVersion()) {
            version = "";
        } else {
            version = TextFormatting.DARK_RED + " Update Available! (" + WebUtils.INSTANCE.getLatestVersion() + ")";
        }
        String combined = slogan + version;
        drawString(fr, combined, width - fr.getStringWidth(combined) - 2, this.height - 20, -1);

        widthVersion = fr.getStringWidth(version);
        widthVersionRest = width - widthVersion - 2;
        if (mouseX > widthVersionRest && mouseX < widthVersionRest + widthVersion && mouseY > height - 20 && mouseY < height - 10 && Mouse.isInsideWindow()) {
            drawRect(widthVersionRest, height - 11, widthVersion + widthVersionRest, height - 10, -1);
        }
    }

    @Inject(method = "mouseClicked", at = @At("RETURN"))
    public void mouseClicked$Inject$RETURN(int mouseX, int mouseY, int mouseButton, CallbackInfo ci) {
        if (mouseX > widthVersionRest && mouseX < widthVersionRest + widthVersion && mouseY > height - 20 && mouseY < height - 10) {
            WebUtils.INSTANCE.openWebLink(LambdaMod.DOWNLOAD_LINK);
        }
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V"))
    private void drawScreen$Redirect$INVOKE$drawGradientRect(GuiMainMenu guiMainMenu, int left, int top, int right, int bottom, int startColor, int endColor) {
        if (MenuShader.INSTANCE.isDisabled()) {
            drawGradientRect(left, top, right, bottom, startColor, endColor);
        }
    }

    @Inject(method = "renderSkybox", at = @At("HEAD"), cancellable = true)
    private void renderSkybox$Inject$HEAD(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (MenuShader.INSTANCE.isEnabled()) {
            MenuShader.render();
            ci.cancel();
        }
    }
}
