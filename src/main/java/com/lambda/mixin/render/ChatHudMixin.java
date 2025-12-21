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
import com.lambda.event.events.ChatEvent;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;

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
//    @WrapOperation(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;drawTextWithShadow(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/text/OrderedText;III)I"))
//    int wrapRenderCall(DrawContext instance, TextRenderer textRenderer, OrderedText text, int x, int y, int color, Operation<Integer> original) {
//        return original.call(instance, textRenderer, LambdaMoji.INSTANCE.parse(text, x, y, color), 0, y, 16777215 + (color << 24));
//    }

    @WrapMethod(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V")
    void wrapAddMessage(Text message, MessageSignatureData signatureData, MessageIndicator indicator, Operation<Void> original) {
        var event = new ChatEvent.Receive(message, signatureData, indicator);

        if (!EventFlow.post(event).isCanceled())
            original.call(event.getMessage(), event.getSignature(), event.getIndicator());
    }
}
