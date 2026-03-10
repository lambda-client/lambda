/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.mixin.render;

import com.lambda.gui.components.QuickSearch;
import com.lambda.module.modules.client.AutoUpdater;
import com.lambda.module.modules.render.ContainerPreview;
import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import org.lwjgl.glfw.GLFW;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Screen.class)
public class ScreenMixin {
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(KeyInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
            if (QuickSearch.INSTANCE.isOpen()) {
                QuickSearch.INSTANCE.close();
                cir.setReturnValue(true);
            } else if (AutoUpdater.getShowInstallModal()) {
                AutoUpdater.INSTANCE.disable();
                AutoUpdater.setShowInstallModal(false);
                cir.setReturnValue(true);
            } else if (AutoUpdater.getShowUninstallModal()) {
                AutoUpdater.INSTANCE.enable();
                AutoUpdater.setShowUninstallModal(false);
                cir.setReturnValue(true);
            }
        }
    }

    @Inject(method = "renderInGameBackground", at = @At("HEAD"), cancellable = true)
    private void injectRenderInGameBackground(DrawContext context, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoGuiShadow()) ci.cancel();
    }

    @WrapOperation(method = "renderWithTooltip", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/screen/Screen;render(Lnet/minecraft/client/gui/DrawContext;IIF)V"))
    private void wrapRender(Screen instance, DrawContext context, int mouseX, int mouseY, float deltaTicks, Operation<Void> original) {
        original.call(instance, context, mouseX, mouseY, deltaTicks);

        if (ContainerPreview.INSTANCE.isEnabled() && ContainerPreview.isLocked()) {
            ContainerPreview.renderLockedTooltip(context, MinecraftClient.getInstance().textRenderer);
        }
    }
}
