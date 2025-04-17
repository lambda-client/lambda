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

package com.lambda.mixin.input;

import com.lambda.event.EventFlow;
import com.lambda.event.events.MouseEvent;
import com.lambda.util.math.Vec2d;
import net.minecraft.client.Mouse;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Mouse.class)
public class MouseMixin {
    @Shadow private double x;

    @Shadow private double y;

    @Inject(method = "onMouseButton(JIII)V", at = @At("HEAD"), cancellable = true)
    private void onMouseButton(long window, int button, int action, int mods, CallbackInfo ci) {
        Vec2d position = new Vec2d(x, y);

        if (EventFlow.post(new MouseEvent.Click(button, action, mods, position)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onMouseScroll(JDD)V", at = @At("HEAD"), cancellable = true)
    private void onMouseScroll(long window, double horizontal, double vertical, CallbackInfo ci) {
        Vec2d delta = new Vec2d(horizontal, vertical);

        if (EventFlow.post(new MouseEvent.Scroll(delta)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "onCursorPos(JDD)V", at = @At("HEAD"), cancellable = true)
    private void onCursorPos(long window, double x, double y, CallbackInfo ci) {
        if (x + y == this.x + this.y) return;

        Vec2d position = new Vec2d(x, y);

        if (EventFlow.post(new MouseEvent.Move(position)).isCanceled()) {
            ci.cancel();
        }
    }
}
