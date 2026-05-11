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

import com.lambda.Lambda;
import com.lambda.module.modules.render.Freecam;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.client.render.debug.ChunkBorderDebugRenderer;
import net.minecraft.util.math.ChunkSectionPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ChunkBorderDebugRenderer.class)
public class ChunkBorderDebugRendererMixin {
    @ModifyExpressionValue(method = "render", at = @At(value = "INVOKE", target = "Lnet/minecraft/util/math/ChunkSectionPos;from(Lnet/minecraft/util/math/BlockPos;)Lnet/minecraft/util/math/ChunkSectionPos;"))
    private ChunkSectionPos modifyChunkSectionPos(ChunkSectionPos original) {
        if (Freecam.INSTANCE.isDisabled()) return original;
        var camera = Lambda.getMc().gameRenderer.getCamera();
        return ChunkSectionPos.from(camera.getBlockPos());
    }
}
