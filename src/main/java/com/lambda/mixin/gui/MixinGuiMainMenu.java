package com.lambda.mixin.gui;

import com.lambda.client.LambdaMod;
import com.lambda.client.gui.mc.LambdaGuiIncompat;
import com.lambda.client.module.modules.client.MenuShader;
import com.lambda.client.util.KamiCheck;
import com.lambda.client.util.WebUtils;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.text.TextFormatting;
import org.lwjgl.input.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    private int widthVersion;
    private int widthVersionRest;

    @Inject(method = "initGui", at = @At("RETURN"))
    public void initGui$Inject$RETURN(CallbackInfo ci) {
        MenuShader.reset();
    }

    @Inject(method = "drawScreen", at = @At("RETURN"))
    public void drawScreen$Inject$RETURN(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (KamiCheck.INSTANCE.isKami() && !KamiCheck.INSTANCE.getDidDisplayWarning()) {
            KamiCheck.INSTANCE.setDidDisplayWarning(true);
            mc.displayGuiScreen(new LambdaGuiIncompat());
        }
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
