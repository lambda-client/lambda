
package com.minato.module.modules.client;

import java.util.concurrent.atomic.AtomicInteger;

/**
 * ParticleLimiter — Giới hạn số particle được render/add dựa trên FPS.
 *
 * ### Particle cap logic
 * - Billboard particles (phổ biến nhất): skip render khi FPS thấp
 * - ParticleManager.addParticle(): discard particle mới khi quá ngưỡng
 * - Thresholds dựa trên FPS hiện tại
 * - Dùng AtomicInteger cho thread safety (particle.addParticle có thể gọi từ worker threads)
 *
 * Dùng chung với [FpsManager] và [PerformanceOptimizer].
 *
 * ## Quan trọng: resetFrameCounter() PHẢI được gọi mỗi frame (từ PerformanceOptimizer render listener).
 * Nếu không, budget sẽ cạn kiệt sau vài giây và mọi particle đều bị chặn.
 */
public class ParticleLimiter {

    /** Particle cap có bật không (set từ PerformanceOptimizer) — controls per-frame budget */
    public static boolean enabled = true;

    /** Selective particle skip (firework/explosion/potion) — điều khiển bởi setting riêng */
    public static boolean selectiveEnabled = true;

    /** Particle counter trong frame hiện tại (AtomicInteger cho thread safety) */
    private static final AtomicInteger frameParticleCount = new AtomicInteger(0);

    /** Max particles per frame dựa trên FPS */
    private static int maxParticlesPerFrame = 1000;

    /** Public getter cho HUD display */
    public static int getMaxParticlesPerFrame() { return maxParticlesPerFrame; }

    /** Reset counter mỗi frame (gọi từ TickEvent.Render.Pre trong PerformanceOptimizer) */
    public static void resetFrameCounter() {
        frameParticleCount.set(0);
    }

    /**
     * Kiểm tra xem particle có nên bị discard không.
     * Gọi từ ParticleManagerMixin.addParticle().
     * Thread-safe: dùng AtomicInteger (incrementAndGet).
     *
     * @return true → discard particle (không add)
     */
    public static boolean shouldDiscard() {
        if (!enabled) return false;
        int fps = FpsManager.INSTANCE.getCurrentFps();
        if (fps <= 0 || fps >= FpsManager.INSTANCE.getTargetFps()) return false;

        // Update max particles based on FPS
        if (fps >= 30) maxParticlesPerFrame = 500;
        else if (fps >= 20) maxParticlesPerFrame = 200;
        else if (fps >= 10) maxParticlesPerFrame = 80;
        else maxParticlesPerFrame = 30;

        // Atomic increment-and-check — thread-safe
        return frameParticleCount.incrementAndGet() > maxParticlesPerFrame;
    }

    /**
     * Kiểm tra xem particle cụ thể có nên bị discard không, dựa trên type.
     * Target: firework trail, explosion smoke, potion splash — những particle heavy nhất.
     * Gọi từ ParticleManagerMixin.addParticle() TRƯỚC shouldDiscard().
     *
     * @param particleClass Simple name của particle class
     * @return true → discard particle này (không add)
     */
    public static boolean shouldDiscardByType(String particleClass) {
        if (!selectiveEnabled) return false;
        int fps = FpsManager.INSTANCE.getCurrentFps();
        if (fps <= 0 || fps >= FpsManager.INSTANCE.getTargetFps()) return false;

        // Chỉ discard specific heavy particle types khi FPS < target
        // Firework trail: rất nhiều particle nhỏ bay lâu
        if (particleClass.contains("Firework")) return true;
        if (particleClass.contains("SoulFirework")) return true;

        if (fps < 20) {
            // Critical: thêm explosion + potion
            if (particleClass.contains("Explosion")) return true;
            if (particleClass.contains("Splash")) return true;
            if (particleClass.contains("Lingering")) return true;
            if (particleClass.contains("InstantEffect")) return true;
            // Dragon breath cũng heavy
            if (particleClass.contains("DragonBreath")) return true;
        }

        if (fps < 10) {
            // Extreme: thêm campfire smoke, big smoke, portal particles
            if (particleClass.contains("Campfire")) return true;
            if (particleClass.contains("LargeSmoke")) return true;
            if (particleClass.contains("Portal")) return true;
        }

        return false;
    }

    /**
     * Kiểm tra xem Billboard particle có nên bị skip render không.
     * Gọi từ BillboardParticleMixin.render().
     *
     * @return true → skip render particle này
     */
    public static boolean shouldSkipBillboard() {
        if (!enabled) return false;
        int fps = FpsManager.INSTANCE.getCurrentFps();
        return fps > 0 && fps < 15;  // Only skip billboard render at < 15 FPS
    }

    public static void reset() {
        frameParticleCount.set(0);
        maxParticlesPerFrame = 1000;
    }
}
