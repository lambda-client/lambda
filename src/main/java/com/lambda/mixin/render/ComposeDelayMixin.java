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
import kotlin.Unit;
import kotlin.jvm.functions.Function0;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * Keeps the work Compose puts off until later on the render thread.
 * <p>
 * Compose waits on AWT's event thread and then does the work right there, which in a desktop
 * application is the thread its interface lives on. Lambda's interface lives on the render
 * thread, so that work, the bookkeeping of where every element is, ran alongside a layout in
 * progress and now and then lost an element, which took the whole interface down with
 * "LayoutNode not found in RectList".
 */
@Mixin(value = Actuals_skikoKt.class, remap = false)
public class ComposeDelayMixin {
    @ModifyVariable(method = "postDelayed", at = @At("HEAD"), argsOnly = true, require = 1)
    private static Function0<Unit> runOnRenderThread(Function0<Unit> block) {
        return () -> {
            MinecraftClient.getInstance().execute(block::invoke);
            return Unit.INSTANCE;
        };
    }
}
