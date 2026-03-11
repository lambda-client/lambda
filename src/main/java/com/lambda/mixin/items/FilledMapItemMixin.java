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

package com.lambda.mixin.items;

import com.lambda.module.modules.render.MapPreview;
import net.minecraft.item.FilledMapItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;

import java.util.Optional;

@Mixin(FilledMapItem.class)
public class FilledMapItemMixin extends Item {
    public FilledMapItemMixin(Item.Settings settings) {
        super(settings);
    }

    @Override
    public Optional<TooltipData> getTooltipData(ItemStack stack) {
        return MapPreview.INSTANCE.isEnabled()
                ? Optional.of(new MapPreview.MapComponent(stack))
                : super.getTooltipData(stack);
    }
}
