package com.lambda.client.mixin.client.gui;

import com.lambda.client.module.modules.client.MenuShader;
import com.lambda.client.util.graphics.ShaderSandbox;
import com.lambda.client.util.graphics.Shaders;
import net.minecraft.client.gui.GuiMainMenu;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Random;

@Mixin(GuiMainMenu.class)
public abstract class MixinGuiMainMenu extends GuiScreen {

    private long initTime;
    private static ShaderSandbox backgroundShader;
    @Shadow protected abstract void renderSkybox(int paramInt1, int paramInt2, float paramFloat);

    @Inject(method = "initGui", at = @At("RETURN"), cancellable = true)
    public void initShader(CallbackInfo info) {
        if(MenuShader.INSTANCE.getMode() == MenuShader.Mode.RANDOM) {
            Random random = new Random();
            Shaders[] shaders = Shaders.values();
            backgroundShader = new ShaderSandbox(shaders[random.nextInt(shaders.length)].get());
        } else {
            backgroundShader = new ShaderSandbox(MenuShader.INSTANCE.getShader().get());

        }
        initTime = System.currentTimeMillis();
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;renderSkybox(IIF)V"))
    private void voided(GuiMainMenu guiMainMenu, int mouseX, int mouseY, float partialTicks) {
        if (MenuShader.INSTANCE.isDisabled())
            renderSkybox(mouseX, mouseY, partialTicks);
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V", ordinal = 0))
    private void noRect1(GuiMainMenu guiMainMenu, int left, int top, int right, int bottom, int startColor, int endColor) {
        if (MenuShader.INSTANCE.isDisabled())
            drawGradientRect(left, top, right, bottom, startColor, endColor);
    }

    @Redirect(method = "drawScreen", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiMainMenu;drawGradientRect(IIIIII)V", ordinal = 1))
    private void noRect2(GuiMainMenu guiMainMenu, int left, int top, int right, int bottom, int startColor, int endColor) {
        if (MenuShader.INSTANCE.isDisabled())
            drawGradientRect(left, top, right, bottom, startColor, endColor);
    }

    @Inject(method = "drawScreen", at = @At("HEAD"), cancellable = true)
    public void drawScreenShader(int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (MenuShader.INSTANCE.isEnabled()) {
            GlStateManager.disableCull();
            backgroundShader.useShader(mc.displayWidth, mc.displayHeight, (mouseX * 2), (mouseY * 2), (float)(System.currentTimeMillis() - initTime) / 1000.0F);
            GL11.glBegin(7);
            GL11.glVertex2f(-1.0F, -1.0F);
            GL11.glVertex2f(-1.0F, 1.0F);
            GL11.glVertex2f(1.0F, 1.0F);
            GL11.glVertex2f(1.0F, -1.0F);
            GL11.glEnd();
            GL20.glUseProgram(0);
        }
    }
}

