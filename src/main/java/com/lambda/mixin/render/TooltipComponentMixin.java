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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.tooltip.TooltipComponent;
import net.minecraft.item.tooltip.TooltipData;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(TooltipComponent.class)
public interface TooltipComponentMixin {
    @WrapMethod(method = "of(Lnet/minecraft/item/tooltip/TooltipData;)Lnet/minecraft/client/gui/tooltip/TooltipComponent;")
    private static TooltipComponent of(TooltipData tooltipData, Operation<TooltipComponent> original) {
        if (ContainerPreview.INSTANCE.isEnabled() &&
                tooltipData instanceof ContainerPreview.ContainerComponent containerComponent)
            return containerComponent;

        if (MapPreview.INSTANCE.isEnabled() && tooltipData instanceof MapPreview.MapComponent mapComponent)
            return mapComponent;

        return original.call(tooltipData);
    }
}
