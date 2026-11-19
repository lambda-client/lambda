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

package com.lambda.mixin.render;

import androidx.compose.ui.Actuals_skikoKt;
import com.lambda.gui.core.RenderThread;
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = Actuals_skikoKt.class, remap = false)
public class ComposeDelayMixin {
    @Inject(method = "postDelayed", at = @At("HEAD"), cancellable = true, require = 1)
    private static void delayOnRenderThread(long delayMillis, Function0<Unit> block, CallbackInfoReturnable<Object> cir) {
        cir.setReturnValue(RenderThread.after(delayMillis, block));
    }
}
