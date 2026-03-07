package com.lambda.mixin.render;

import com.lambda.graphics.outline.OutlineManager;
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
        if (OutlineManager.INSTANCE.shouldCapture(blockEntity.getPos())) {
            cir.setReturnValue(true);
        }
    }
}
