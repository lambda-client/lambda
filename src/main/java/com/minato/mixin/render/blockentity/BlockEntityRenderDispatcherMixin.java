
package com.minato.mixin.render.blockentity;

import com.minato.module.modules.client.BlockEntityCuller;
import com.minato.module.modules.client.PerformanceOptimizer;
import com.minato.module.modules.render.NoRender;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderManager;
import net.minecraft.client.render.block.entity.state.BlockEntityRenderState;
import net.minecraft.client.render.command.ModelCommandRenderer;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderManager.class)
public class BlockEntityRenderDispatcherMixin {
    @Inject(method = "getRenderState", at = @At("HEAD"), cancellable = true)
    private <E extends BlockEntity, S extends BlockEntityRenderState> void injectGetRenderState(E blockEntity, float tickProgress, ModelCommandRenderer.@Nullable CrumblingOverlayCommand crumblingOverlay, CallbackInfoReturnable<S> cir) {
        // NoRender manual override
        if (NoRender.shouldOmitBlockEntity(blockEntity)) {
            cir.setReturnValue(null);
            return;
        }
        // PerformanceOptimizer: FPS-based block entity culling
        if (PerformanceOptimizer.INSTANCE.isEnabled() && BlockEntityCuller.enabled) {
            String name = blockEntity.getClass().getSimpleName();
            if (BlockEntityCuller.shouldSkip(name)) {
                cir.setReturnValue(null);
            }
        }
    }
}
