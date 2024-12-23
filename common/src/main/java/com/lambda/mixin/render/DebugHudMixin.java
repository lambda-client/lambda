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

import com.lambda.task.TaskFlow;
import com.lambda.util.DebugInfoHud;
import net.minecraft.client.gui.hud.DebugHud;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

@Mixin(DebugHud.class)
public class DebugHudMixin {
    @Inject(method = "getRightText", at = @At(value = "TAIL"))
    private void onGetRightText(CallbackInfoReturnable<List<String>> cir) {
        DebugInfoHud.addDebugInfo(cir.getReturnValue());
    }

    @Inject(method = "getLeftText", at = @At(value = "TAIL"))
    private void onGetLeftText(CallbackInfoReturnable<List<String>> cir) {
        cir.getReturnValue().addAll(List.of(TaskFlow.INSTANCE.toString().split("\n")));
    }
}
