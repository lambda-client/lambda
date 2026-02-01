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

package com.lambda.mixin.items;

import com.lambda.module.modules.render.Search;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.BarrierBlock;
import net.minecraft.block.BlockRenderType;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(BarrierBlock.class)
public class BarrierBlockMixin {
    /**
     * Modifies barrier block render type to {@link BlockRenderType#MODEL} when {@link Search} is enabled and {@link Search#getBarrier()} is true
     */
    @ModifyReturnValue(method = "getRenderType", at = @At("RETURN"))
    private BlockRenderType modifyGetRenderType(BlockRenderType original, BlockState state) {
        if (Search.INSTANCE.isEnabled()
                && Search.getBarrier()
                && state.getBlock() == Blocks.BARRIER
        ) return BlockRenderType.MODEL;
        return original;
    }
}
