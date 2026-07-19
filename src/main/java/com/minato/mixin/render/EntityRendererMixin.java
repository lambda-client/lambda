
package com.minato.mixin.render;

import com.minato.graphics.outline.OutlineHandler;
import com.minato.module.modules.client.EntityCuller;
import com.minato.module.modules.client.PerformanceOptimizer;
import com.minato.module.modules.render.NoRender;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderer.class)
public class EntityRendererMixin {
    @Inject(method = "shouldRender(Lnet/minecraft/entity/Entity;Lnet/minecraft/client/render/Frustum;DDD)Z", at = @At("HEAD"), cancellable = true)
    private void injectShouldRender(Entity entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        // 1. NoRender check — early return if omitted
        if (NoRender.shouldOmitEntity(entity)) {
            cir.cancel();
            return;
        }
        // 2. Outline capture check — early return if captured
        if (OutlineHandler.shouldCapture(entity.getId())) {
            cir.setReturnValue(true);
            return;
        }
        // 3. PerformanceOptimizer: FPS-based entity culling — only runs if neither 1 nor 2 triggered
        if (PerformanceOptimizer.INSTANCE.isEnabled() && EntityCuller.enabled) {
            double dist = Math.sqrt(x * x + y * y + z * z);
            double maxDist = EntityCuller.getMaxRenderDistance();
            if (dist > maxDist) {
                cir.setReturnValue(false);
                return;
            }
            // Skip non-essential entities at critical FPS
            if (EntityCuller.shouldSkipNonEssential(entity.getClass())) {
                cir.setReturnValue(false);
            }
        }
    }

    @Inject(method = "renderLabelIfPresent", at = @At("HEAD"), cancellable = true)
    private void injectRenderLabelIfPresent(EntityRenderState state, MatrixStack matrices, OrderedRenderCommandQueue queue, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoNametags()) ci.cancel();
    }
}
