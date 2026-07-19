
package com.minato.mixin.render.particle;

import com.minato.module.modules.client.ParticleLimiter;
import com.minato.module.modules.client.PerformanceOptimizer;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * ParticleManagerMixin — Giới hạn tổng số particle được spawn khi FPS thấp.
 *
 * Inject vào ParticleManager.addParticle() để discard particle mới
 * nếu đã vượt quá ngưỡng cho phép (dựa trên FPS hiện tại).
 */
@Mixin(ParticleManager.class)
public class ParticleManagerMixin {

    @Inject(method = "addParticle(Lnet/minecraft/client/particle/Particle;)V", at = @At("HEAD"), cancellable = true)
    private void limitParticles(Particle particle, CallbackInfo ci) {
        if (!PerformanceOptimizer.INSTANCE.isEnabled()) return;

        // 1. Specific particle type filtering (firework, explosion, potion)
        if (ParticleLimiter.shouldDiscardByType(particle.getClass().getSimpleName())) {
            ci.cancel();
            return;
        }

        // 2. Per-frame particle budget cap
        if (ParticleLimiter.shouldDiscard()) {
            ci.cancel();
        }
    }
}
