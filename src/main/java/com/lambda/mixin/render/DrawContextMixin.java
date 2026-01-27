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

import com.lambda.module.modules.render.ContainerPreview;
import com.lambda.module.modules.render.MapPreview;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.MapRenderState;
import net.minecraft.component.DataComponentTypes;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.item.tooltip.TooltipData;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.Optional;

/*
Map slot rendering code
Original source: https://github.com/Crec0/map-in-slot
Copyright (c) 2022 Crec0
Licensed under MIT License
 */
@Mixin(DrawContext.class)
public abstract class DrawContextMixin {
    @Shadow
    @Final
    MinecraftClient client;

    @Unique
    private final MapRenderState mapRenderState = new MapRenderState();

    @Shadow
    public abstract Matrix3x2fStack getMatrices();

    @Shadow
    public abstract void drawMap(MapRenderState mapRenderState);

    @Inject(method = "drawStackOverlay(Lnet/minecraft/client/font/TextRenderer;Lnet/minecraft/item/ItemStack;IILjava/lang/String;)V", at = @At(value = "TAIL"))
    private void injectDrawMap(TextRenderer textRenderer, ItemStack stack, int i, int j, String string, CallbackInfo ci) {
        if (MapPreview.INSTANCE.isDisabled() || !MapPreview.getShowInSlot()) return;

        if (!stack.isOf(Items.FILLED_MAP)) return;

        var mapId = stack.get(DataComponentTypes.MAP_ID);
        var savedData = FilledMapItem.getMapState(mapId, client.world);

        if (savedData == null) return;

        this.getMatrices().pushMatrix();
        this.getMatrices().translate(i, j);
        this.getMatrices().scale(0.125F, 0.125F);

        client.getMapRenderer().update(mapId, savedData, this.mapRenderState);
        this.drawMap(this.mapRenderState);

        this.getMatrices().popMatrix();
    }

    @Inject(method = "drawTooltip(Lnet/minecraft/client/font/TextRenderer;Ljava/util/List;Ljava/util/Optional;IILnet/minecraft/util/Identifier;)V", at = @At("HEAD"), cancellable = true)
    private void onDrawTooltip(TextRenderer textRenderer, List<Text> text, Optional<TooltipData> data, int x, int y, @Nullable Identifier texture, CallbackInfo ci) {
        if (!ContainerPreview.INSTANCE.isEnabled()) return;

        if (ContainerPreview.isRenderingSubTooltip()) return;

        if (ContainerPreview.isLocked()) {
            ci.cancel();
            ContainerPreview.renderLockedTooltip((DrawContext)(Object)this, textRenderer);
            return;
        }

        if (data.isPresent() && data.get() instanceof ContainerPreview.ContainerComponent component) {
            ci.cancel();
            ContainerPreview.renderShulkerTooltip((DrawContext)(Object)this, textRenderer, component, x, y);
        }
    }
}
