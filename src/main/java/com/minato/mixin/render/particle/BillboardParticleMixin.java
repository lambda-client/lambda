
package com.minato.mixin.render.particle;

import com.minato.module.modules.client.ParticleLimiter;
import com.minato.module.modules.client.PerformanceOptimizer;
import com.minato.module.modules.render.NoRender;
import net.minecraft.client.particle.BillboardParticle;
import net.minecraft.client.particle.BillboardParticleSubmittable;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.render.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BillboardParticle.class)
public class BillboardParticleMixin {
    @Inject(method = "render(Lnet/minecraft/client/particle/BillboardParticleSubmittable;Lnet/minecraft/client/render/Camera;F)V", at = @At("HEAD"), cancellable = true)
    private void injectRender(BillboardParticleSubmittable submittable, Camera camera, float tickDelta, CallbackInfo ci) {
        if (NoRender.shouldOmitParticle((Particle) ((Object) this))) ci.cancel();

        // PerformanceOptimizer: skip billboard particle render when FPS is critically low
        if (PerformanceOptimizer.INSTANCE.isEnabled() && ParticleLimiter.shouldSkipBillboard()) {
            ci.cancel();
        }
    }
}
