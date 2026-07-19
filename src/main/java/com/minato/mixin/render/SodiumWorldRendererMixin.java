
package com.minato.mixin.render;

import com.minato.module.modules.render.NoRender;
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
