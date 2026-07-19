
package com.minato.mixin.render;

import com.minato.Minato;
import com.minato.module.modules.render.Freecam;
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
        var camera = Minato.getMc().gameRenderer.getCamera();
        return ChunkSectionPos.from(camera.getBlockPos());
    }
}
