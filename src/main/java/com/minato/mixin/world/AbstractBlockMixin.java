
package com.minato.mixin.world;

import com.minato.module.modules.render.XRay;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.minecraft.block.AbstractBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBlock.class)
public class AbstractBlockMixin {
    @ModifyReturnValue(method = "getAmbientOcclusionLightLevel", at = @At("RETURN"))
    private float modifyGetAmbientOcclusionLightLevel(float original) {
        if (XRay.INSTANCE.isEnabled()) return 1f;
        return original;
    }
}
