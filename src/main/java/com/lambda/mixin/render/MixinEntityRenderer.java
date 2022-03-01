package com.lambda.mixin.render;

import com.lambda.client.event.LambdaEventBus;
import com.lambda.client.event.events.RenderOverlayEvent;
import com.lambda.client.module.modules.movement.ElytraFlight;
import com.lambda.client.module.modules.player.BlockInteraction;
import com.lambda.client.module.modules.render.CameraClip;
import com.lambda.client.module.modules.render.NoRender;
import com.lambda.client.util.Wrapper;
import net.minecraft.client.renderer.EntityRenderer;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.RayTraceResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = EntityRenderer.class, priority = Integer.MAX_VALUE)
public class MixinEntityRenderer {

    @Inject(method = "updateCameraAndRender", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiIngame;renderGameOverlay(F)V", shift = At.Shift.AFTER))
    public void updateCameraAndRender(float partialTicks, long nanoTime, CallbackInfo ci) {
        LambdaEventBus.INSTANCE.post(new RenderOverlayEvent());
    }

    @ModifyVariable(method = "orientCamera", at = @At(value = "STORE", ordinal = 0), ordinal = 0)
    public RayTraceResult orientCameraStoreRayTraceBlocks(RayTraceResult value) {
        if (CameraClip.INSTANCE.isEnabled()) {
            return null;
        } else {
            return value;
        }
    }

    @ModifyVariable(method = "orientCamera", at = @At(value = "STORE", ordinal = 0), ordinal = 3)
    public double orientCameraAtStore(double value) {
        if (CameraClip.INSTANCE.isEnabled()) {
            return CameraClip.INSTANCE.getDistance();
        } else {
            return value;
        }
    }

    @Inject(method = "displayItemActivation", at = @At(value = "HEAD"), cancellable = true)
    public void displayItemActivation(ItemStack stack, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getTotems()) {
            ci.cancel();
        }
    }

    @Inject(method = "setupFog", at = @At(value = "RETURN"))
    public void setupFog(int startCoords, float partialTicks, CallbackInfo callbackInfo) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getFog()) {
            GlStateManager.disableFog();
        }
    }

    @Inject(method = "hurtCameraEffect", at = @At("HEAD"), cancellable = true)
    public void hurtCameraEffect(float ticks, CallbackInfo ci) {
        if (NoRender.INSTANCE.isEnabled() && NoRender.INSTANCE.getHurtCamera()) {
            ci.cancel();
        }
    }

    @Inject(method = "getMouseOver", at = @At(value = "INVOKE", target = "Lnet/minecraft/entity/Entity;getPositionEyes(F)Lnet/minecraft/util/math/Vec3d;", shift = At.Shift.BEFORE), cancellable = true)
    public void getEntitiesInAABBexcluding(float partialTicks, CallbackInfo ci) {
        if (BlockInteraction.isNoEntityTraceEnabled()) {
            ci.cancel();
            Wrapper.getMinecraft().profiler.endSection();
        }
    }

    @Inject(method = "orientCamera", at = @At("RETURN"))
    private void orientCameraStoreEyeHeight(float partialTicks, CallbackInfo ci) {
        if (!ElytraFlight.INSTANCE.shouldSwing()) return;
        Entity entity = Wrapper.getMinecraft().getRenderViewEntity();
        if (entity != null) {
            GlStateManager.translate(0.0f, entity.getEyeHeight() - 0.4f, 0.0f);
        }
    }
}
