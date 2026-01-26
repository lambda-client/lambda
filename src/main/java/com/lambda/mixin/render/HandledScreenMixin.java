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
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(HandledScreen.class)
public class HandledScreenMixin {
    @WrapMethod(method = "mouseClicked")
    private boolean onMouseClicked(Click click, boolean doubled, Operation<Boolean> original) {
        if (ContainerPreview.INSTANCE.isDisabled() || !ContainerPreview.isLocked())
            original.call(click, doubled);

        return ContainerPreview.isMouseOverLockedTooltip((int) click.x(), (int) click.y());
    }

    @WrapMethod(method = "mouseReleased")
    private boolean onMouseReleased(Click click, Operation<Boolean> original) {
        if (ContainerPreview.INSTANCE.isDisabled() || !ContainerPreview.isLocked())
            original.call(click);

        return ContainerPreview.isMouseOverLockedTooltip((int) click.x(), (int) click.y());
    }
}
