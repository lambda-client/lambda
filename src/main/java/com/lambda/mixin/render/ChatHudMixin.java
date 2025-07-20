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
     * Draws emojis at the given chat position
     * <pre>{@code
     * context.getMatrices().translate(0.0F, 0.0F, 50.0F);
     * context.drawTextWithShadow(this.client.textRenderer, visible.content(), 0, y, 16777215 + (u << 24));
     * context.getMatrices().pop();
     * }</pre>
     */
    @Redirect(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
    int redirectRenderCall(DrawContext instance, TextRenderer textRenderer, OrderedText text, int x, int y, int color) {
        return instance.drawTextWithShadow(textRenderer, LambdaMoji.INSTANCE.parse(text, x, y, color), 0, y, 16777215 + (color << 24));
    }
}
