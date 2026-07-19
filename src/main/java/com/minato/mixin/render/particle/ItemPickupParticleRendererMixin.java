
package com.minato.mixin.render.particle;

import com.minato.module.modules.render.NoRender;
import net.minecraft.client.particle.ItemPickupParticle;
import net.minecraft.client.particle.ItemPickupParticleRenderer;
import net.minecraft.client.particle.NoRenderParticleRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.Submittable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ItemPickupParticleRenderer.class)
public class ItemPickupParticleRendererMixin {
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void injectRender(Frustum frustum, Camera camera, float tickProgress, CallbackInfoReturnable<Submittable> cir) {
        if (NoRender.shouldOmitParticle(ItemPickupParticle.class)) cir.setReturnValue(NoRenderParticleRenderer.EMPTY);
    }
}
