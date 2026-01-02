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

package com.lambda.mixin.render;

import com.lambda.module.modules.render.NoRender;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer;
import net.caffeinemc.mods.sodium.client.util.FogParameters;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(SodiumWorldRenderer.class)
public class SodiumWorldRendererMixin {
    @Unique
    private final FogParameters NO_FOG = new FogParameters(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f);

    @ModifyExpressionValue(method = "drawChunkLayer(Lnet/minecraft/client/render/BlockRenderLayerGroup;Lnet/caffeinemc/mods/sodium/client/render/chunk/ChunkRenderMatrices;DDDLnet/minecraft/client/gl/GpuSampler;)V", at = @At(value = "FIELD", target = "Lnet/caffeinemc/mods/sodium/client/render/SodiumWorldRenderer;lastFogParameters:Lnet/caffeinemc/mods/sodium/client/util/FogParameters;", opcode = Opcodes.GETFIELD))
    private FogParameters modifyFogParameters(FogParameters original) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoTerrainFog()) return NO_FOG;
        return original;
    }
}
