
package com.minato.mixin.render;

import com.minato.module.modules.render.XRay;
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
