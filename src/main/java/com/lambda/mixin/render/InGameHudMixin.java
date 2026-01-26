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

import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.entity.Entity;
import net.minecraft.scoreboard.ScoreboardObjective;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyArgs;
import org.spongepowered.asm.mixin.injection.invoke.arg.Args;

@Mixin(InGameHud.class)
public class InGameHudMixin {
    @WrapMethod(method = "renderNauseaOverlay")
    private void injectRenderNauseaOverlay(DrawContext context, float nauseaStrength, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoNausea())
            original.call(context, nauseaStrength);
    }

    @WrapMethod(method = "renderPortalOverlay")
    private void injectRenderPortalOverlay(DrawContext context, float nauseaStrength, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoPortalOverlay())
            original.call(context, nauseaStrength);
    }

    @WrapMethod(method = "renderVignetteOverlay")
    private void injectRenderVignetteOverlay(DrawContext context, Entity entity, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoVignette())
            original.call(context, entity);
    }

    @WrapMethod(method = "renderStatusEffectOverlay")
    private void injectRenderStatusEffectOverlay(DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoStatusEffects())
            original.call(context, tickCounter);
    }

    @WrapMethod(method = "renderSpyglassOverlay")
    private void injectRenderSpyglassOverlay(DrawContext context, float scale, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoSpyglassOverlay())
            original.call(context, scale);
    }

    @ModifyArgs(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/hud/InGameHud;renderOverlay(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/util/Identifier;F)V"))
    private void modifyRenderOverlayArgs(Args args) {
        if (!((Identifier) args.get(1)).getPath().contains("pumpkin")) return;
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoPumpkinOverlay()) {
            args.set(2, 0f);
        }
    }

    @ModifyExpressionValue(method = "renderMiscOverlays", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/network/ClientPlayerEntity;getFrozenTicks()I"))
    private int modifyIsFirstPerson(int original) {
        return (NoRender.INSTANCE.isEnabled() && NoRender.getNoPowderedSnowOverlay()) ? 0 : original;
    }

    @WrapMethod(method = "renderScoreboardSidebar(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/scoreboard/ScoreboardObjective;)V")
    private void injectRenderScoreboardSidebar(DrawContext context, ScoreboardObjective objective, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoScoreBoard())
            original.call(context, objective);
    }

    @WrapMethod(method = "renderCrosshair")
    private void injectRenderCrosshair(DrawContext context, RenderTickCounter tickCounter, Operation<Void> original) {
        if (NoRender.INSTANCE.isDisabled() || !NoRender.getNoCrosshair())
            original.call(context, tickCounter);
    }
}
