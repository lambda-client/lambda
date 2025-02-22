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

import com.lambda.module.modules.client.LambdaMoji;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.text.OrderedText;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(ChatHud.class)
public class ChatHudMixin {
    /**
     * Redirects the chat HUD text rendering to apply custom text parsing and color adjustments.
     *
     * <p>This method intercepts calls to {@code DrawContext#drawTextWithShadow} during chat rendering.
     * It processes the text using {@code LambdaMoji.INSTANCE.parse} to incorporate custom formatting (e.g., emoji support),
     * overrides the x-coordinate by rendering at x = 0, and adjusts the text color by combining a white base (0xFFFFFF)
     * with an alpha value derived from the provided {@code color} parameter.
     *
     * @param text the text to be rendered, processed for custom formatting
     * @param x the original x-coordinate (ignored as rendering occurs at x = 0)
     * @param y the y-coordinate for rendering the text
     * @param color the original text color used to compute the alpha channel for the final rendered color
     * @return the result of the underlying text rendering call
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    int redirectRenderCall(DrawContext instance, TextRenderer textRenderer, OrderedText text, int x, int y, int color) {
        return instance.drawTextWithShadow(textRenderer, LambdaMoji.INSTANCE.parse(text, x, y, color), 0, y, 16777215 + (color << 24));
    }
}
