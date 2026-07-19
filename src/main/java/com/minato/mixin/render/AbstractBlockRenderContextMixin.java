
package com.minato.mixin.render;

import com.minato.module.modules.render.XRay;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import net.caffeinemc.mods.sodium.client.render.model.AbstractBlockRenderContext;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractBlockRenderContext.class)
public class AbstractBlockRenderContextMixin {
    @Shadow
    protected BlockState state;

    @ModifyReturnValue(method = "shouldDrawSide", at = @At("RETURN"))
    private boolean modifyShouldDrawSide(boolean original) {
        if (XRay.INSTANCE.isEnabled() && XRay.getBlockSelection().contains(state.getBlock()) && XRay.getOpacity() < 100)
            return true;
        return original;
    }
}
