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

import com.lambda.Lambda;
import com.lambda.module.modules.render.MapPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.tooltip.TooltipBackgroundRenderer;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.client.gui.tooltip.TooltipPositioner;
import net.minecraft.client.item.TooltipData;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    /**
 * Renders a tooltip using the provided text renderer, tooltip components, and positioning strategy.
 *
 * <p>This shadowed method is implemented by the target class to draw tooltips at the specified coordinates.
 * The tooltip components determine the content displayed, while the positioner defines how the tooltip is positioned on the screen.</p>
 *
 * @param textRenderer the renderer used for drawing the tooltip's text
 * @param components the list of components forming the tooltip's content
 * @param x the x-coordinate for the tooltip's starting position
 * @param y the y-coordinate for the tooltip's starting position
 * @param positioner the strategy that determines the tooltip's placement
 */
@Shadow protected abstract void drawTooltip(TextRenderer textRenderer, List<TooltipComponent> components, int x, int y, TooltipPositioner positioner);

    /**
     * Overrides the default tooltip rendering to include additional components.
     * 
     * <p>This injected method converts a list of text components into tooltip components. If optional tooltip data
     * is present, it is inserted into the list, and if the currently focused item is a filled map, a map preview
     * component is added. It then calls the shadowed tooltip drawing method with the modified components and cancels
     * further execution of the original method.</p>
     *
     * @param textRenderer the renderer used to draw text
     * @param text the list of text elements to be converted into tooltip components
     * @param data an optional tooltip data element to be incorporated into the tooltip
     * @param x the x-coordinate for tooltip positioning
     * @param y the y-coordinate for tooltip positioning
     * @param ci the callback information used to cancel the original tooltip rendering
     */
    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;II)V", at = @At("HEAD"), cancellable = true)
    void drawItemTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y, CallbackInfo ci) {
        List<TooltipComponent> list = text.stream().map(Text::asOrderedText).map(TooltipComponent::of).collect(Collectors.toList());
        data.ifPresent(datax -> list.add(1, TooltipComponent.of(datax)));

        var screen = (HandledScreen) Lambda.getMc().currentScreen;
        if (screen.focusedSlot != null) {
            var stack = screen.focusedSlot.getStack();
            if (stack.isOf(Items.FILLED_MAP)) list.add(1, new MapPreview.MapComponent(stack));
        }

        drawTooltip(textRenderer, list, x, y, HoveredTooltipPositioner.INSTANCE);
        ci.cancel();
    }
}
