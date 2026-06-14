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

import com.lambda.module.modules.render.XRay;
import net.caffeinemc.mods.sodium.client.model.light.data.LightDataAccess;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.BlockRenderView;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

@Mixin(value = LightDataAccess.class, remap = false)
public class SodiumLightDataAccessMixin {
    @Shadow
    protected BlockRenderView level;

    @Shadow
    @Final
    private BlockPos.Mutable pos;

    @ModifyVariable(method = "compute", at = @At(value = "TAIL"), name = "bl")
    private int modifyLight(int value) {
        if (XRay.INSTANCE.isEnabled()) {
            final var block = level.getBlockState(pos).getBlock();
            if (XRay.getBlockSelection().contains(block)) return 0xFFF;
        }

        return value;
    }
}
