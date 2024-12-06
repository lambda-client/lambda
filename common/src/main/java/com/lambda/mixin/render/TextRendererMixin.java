/*
 * Copyright 2024 Lambda
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

import com.lambda.Lambda;
import com.lambda.graphics.renderer.gui.font.LambdaAtlas;
import com.lambda.graphics.renderer.gui.font.LambdaEmoji;
import com.lambda.module.modules.client.LambdaMoji;
import com.lambda.module.modules.client.RenderSettings;
import com.lambda.util.math.Vec2d;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import org.joml.Matrix4f;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Overwrite;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.List;

@Mixin(TextRenderer.class)
public abstract class TextRendererMixin {
    @Shadow
    protected abstract int drawInternal(String text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumers, TextRenderer.TextLayerType layerType, int backgroundColor, int light, boolean mirror);

    @Shadow
    protected abstract int drawInternal(OrderedText text, float x, float y, int color, boolean shadow, Matrix4f matrix, VertexConsumerProvider vertexConsumerProvider, TextRenderer.TextLayerType layerType, int backgroundColor, int light);

    /**
     * @author Edouard127
     * @reason xx
     */
    @Overwrite
    public int draw(
            String text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextRenderer.TextLayerType layerType,
            int backgroundColor,
            int light,
            boolean rightToLeft
    ) {
        if (LambdaMoji.INSTANCE.isDisabled()) return this.drawInternal(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light, rightToLeft);

        return this.drawInternal(neoLambda$parseEmojisAndRender(text, x, y), x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light, rightToLeft);
    }

    /**
     * @author Edouard127
     * @reason xx
     */
    @Overwrite
    public int draw(
            OrderedText text,
            float x,
            float y,
            int color,
            boolean shadow,
            Matrix4f matrix,
            VertexConsumerProvider vertexConsumers,
            TextRenderer.TextLayerType layerType,
            int backgroundColor,
            int light
    ) {
        if (LambdaMoji.INSTANCE.isDisabled()) return this.drawInternal(text, x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light);

        StringBuilder builder = new StringBuilder();
        text.accept((index, style, c) -> {
            builder.appendCodePoint(c);
            return true;
        });

        return this.drawInternal(
                Text.literal(neoLambda$parseEmojisAndRender(builder.toString(), x, y)).asOrderedText(),
                x, y, color, shadow, matrix, vertexConsumers, layerType, backgroundColor, light
        );
    }

    @Unique
    private String neoLambda$parseEmojisAndRender(String raw, float x, float y) {
        List<String> emojis = LambdaEmoji.Twemoji.parse(raw);

        for (String emoji : emojis) {
            String constructed = ":" + emoji + ":";
            int index = raw.indexOf(constructed);

            if (LambdaAtlas.INSTANCE.get(RenderSettings.INSTANCE.getEmojiFont(), emoji) == null ||
                    index == -1) continue;

            int height = Lambda.getMc().textRenderer.fontHeight;
            int width = Lambda.getMc().textRenderer.getWidth(raw.substring(0, index));

            LambdaMoji.INSTANCE.push(constructed, new Vec2d(x + width, y + (float) height / 2));

            // Replace the emoji with whitespaces depending on the player's settings
            raw = raw.replaceFirst(constructed, neoLambda$getReplacement());
        }

        return raw;
    }

    @Unique
    private String neoLambda$getReplacement() {
        int emojiWidth = (int) (((double) Lambda.getMc().textRenderer.fontHeight / 2 / Lambda.getMc().textRenderer.getWidth(" ")) * LambdaMoji.INSTANCE.getScale());
        return " ".repeat(emojiWidth);
    }
}
