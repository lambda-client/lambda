/*
 * Copyright 2025 Lambda
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

import com.lambda.event.EventFlow;
import com.lambda.event.events.HudRenderEvent;
import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "renderNauseaOverlay", at = @At("HEAD"), cancellable = true)
    private void injectRenderNauseaOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoNausea())
            ci.cancel();
    }

    @Inject(method = "renderPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void injectRenderPortalOverlay(DrawContext context, float nauseaStrength, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoPortalOverlay())
            ci.cancel();
    }

    @Inject(method = "renderVignetteOverlay", at = @At("HEAD"), cancellable = true)
    private void injectRenderVignetteOverlay(DrawContext context, Entity entity, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoVignette())
            ci.cancel();
    }

    @Inject(method = "renderStatusEffectOverlay", at = @At("HEAD"), cancellable = true)
    private void injectRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoStatusEffects())
            ci.cancel();
    }

    @Inject(method = "renderSpyglassOverlay", at = @At("HEAD"), cancellable = true)
    private void injectRenderSpyglassOverlay(DrawContext context, float scale, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoSpyglassOverlay())
            ci.cancel();
    }

    @ModifyArgs(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V"))
    private void modifyRenderOverlayArgs(Args args) {
        if (!((Identifier) args.get(1)).getPath().contains("pumpkin"))
            return;
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoPumpkinOverlay()) {
            args.set(2, 0f);
        }
    }

    @ModifyExpressionValue(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getFrozenTicks()I"))
    private int modifyIsFirstPerson(int original) {
        return (NoRender.INSTANCE.isEnabled() && NoRender.getNoPowderedSnowOverlay()) ? 0 : original;
    }

    @Inject(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V", at = @At("HEAD"), cancellable = true)
    private void injectRenderScoreboardSidebar(DrawContext drawContext, ScoreboardObjective objective,
            CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoScoreBoard())
            ci.cancel();
    }

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true)
    private void injectRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoCrosshair())
            ci.cancel();
    }

    /**
     * Fire HudRenderEvent at the end of HUD rendering to allow Lambda modules
     * to render items and other GUI elements using the valid DrawContext.
     */
    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderEnd(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        EventFlow.post(new HudRenderEvent(context));
    }
}
