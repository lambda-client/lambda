
package com.minato.mixin.render;

import com.minato.graphics.outline.OutlineHandler;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.client.render.block.entity.BlockEntityRenderer;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(BlockEntityRenderer.class)
public interface BlockEntityRendererMixin<T extends BlockEntity> {

    @Inject(method = "isInRenderDistance", at = @At("HEAD"), cancellable = true)
    default void forceOutlineRenderDistance(T blockEntity, Vec3d pos, CallbackInfoReturnable<Boolean> cir) {
        if (OutlineHandler.shouldCapture(blockEntity.getPos())) {
            cir.setReturnValue(true);
        }
    }
}
