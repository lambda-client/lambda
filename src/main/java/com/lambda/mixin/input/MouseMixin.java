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

package com.lambda.mixin.input;

import com.lambda.event.EventFlow;
import com.lambda.event.events.MouseEvent;
import com.lambda.module.modules.render.Zoom;
import com.lambda.util.math.Vec2d;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import net.minecraft.client.Mouse;
import net.minecraft.client.option.SimpleOption;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow private double x;

    @Shadow private double y;

    @WrapMethod(method = "onMouseButton(JIII)V")
    private void onMouseButton(long window, int button, int action, int mods, Operation<Void> original) {
        if (!EventFlow.post(new MouseEvent.Click(button, action, mods)).isCanceled())
            original.call(window, button, action, mods);
    }

    @WrapMethod(method = "onMouseScroll(JDD)V")
    private void onMouseScroll(long window, double horizontal, double vertical, Operation<Void> original) {
        Vec2d delta = new Vec2d(horizontal, vertical);

        if (!EventFlow.post(new MouseEvent.Scroll(delta)).isCanceled())
            original.call(window, horizontal, vertical);
    }

    @WrapMethod(method = "onCursorPos(JDD)V")
    private void onCursorPos(long window, double x, double y, Operation<Void> original) {
        if (x + y == this.x + this.y) return;

        Vec2d position = new Vec2d(x, y);

        if (!EventFlow.post(new MouseEvent.Move(position)).isCanceled())
            original.call(window, x, y);
    }

    @ModifyExpressionValue(method = "updateMouse", at = @At(value = "FIELD", target = "Lnet/minecraft/client/option/GameOptions;smoothCameraEnabled:Z"))
    private boolean modifySmoothCameraEnabled(boolean original) {
        if (Zoom.INSTANCE.isEnabled() && Zoom.getSmoothMovement()) return true;
        else return original;
    }

    @ModifyExpressionValue(method = "updateMouse", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/option/SimpleOption;getValue()Ljava/lang/Object;", ordinal = 0))
    private Object modifyGetValue(Object original) {
        if (Zoom.INSTANCE.isEnabled()) return ((Double) original) / Zoom.getTargetZoom();
        else return original;
    }
}
