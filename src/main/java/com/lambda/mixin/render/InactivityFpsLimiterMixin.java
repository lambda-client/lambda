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

import com.lambda.gui.GuiScreen;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.InactivityFpsLimiter;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(InactivityFpsLimiter.class)
public class InactivityFpsLimiterMixin {
    @Shadow
    @Final
    private MinecraftClient client;

    @ModifyReturnValue(method = "getLimitReason", at = @At("RETURN"))
    private InactivityFpsLimiter.LimitReason liftMenuLimit(InactivityFpsLimiter.LimitReason reason) {
        if (reason == InactivityFpsLimiter.LimitReason.OUT_OF_LEVEL_MENU && client.currentScreen == GuiScreen.INSTANCE) {
            return InactivityFpsLimiter.LimitReason.NONE;
        }
        return reason;
    }
}
