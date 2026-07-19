
package com.minato.mixin.render.blockentity;

import com.minato.module.modules.render.NoRender;
import net.minecraft.client.render.block.entity.MobSpawnerBlockEntityRenderer;
import net.minecraft.client.render.command.OrderedRenderCommandQueue;
import net.minecraft.client.render.entity.EntityRenderManager;
import net.minecraft.client.render.entity.state.EntityRenderState;
import net.minecraft.client.render.state.CameraRenderState;
import net.minecraft.client.util.math.MatrixStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(MobSpawnerBlockEntityRenderer.class)
public class MobSpawnerBlockEntityRendererMixin {
    @Inject(method = "renderDisplayEntity", at = @At("HEAD"), cancellable = true)
    private static void injectRender(MatrixStack matrices, OrderedRenderCommandQueue queue, EntityRenderState state, EntityRenderManager entityRenderDispatcher, float rotation, float scale, CameraRenderState cameraRenderState, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.getNoSpawnerMob()) ci.cancel();
    }
}
