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

package com.lambda.mixin.baritone;

import baritone.utils.player.BaritonePlayerController;
import com.lambda.Lambda;
import com.lambda.module.modules.player.FastBreak;
import com.lambda.module.modules.player.PacketMine;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = BaritonePlayerController.class, remap = false)
public class BaritonePlayerControllerMixin {
    @Inject(method = "clickBlock", at = @At("HEAD"), cancellable = true)
    private void injectClickBlock(BlockPos loc, Direction face, CallbackInfoReturnable<Boolean> cir) {
        if (PacketMine.INSTANCE.isEnabled() || FastBreak.INSTANCE.isEnabled()) {
            var interaction = Lambda.getMc().interactionManager;
            if (interaction == null) return;
            interaction.updateBlockBreakingProgress(loc, face);
            cir.cancel();
        }
    }
}
