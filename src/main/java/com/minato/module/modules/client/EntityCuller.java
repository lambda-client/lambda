
package com.minato.module.modules.client;

/**
 * EntityCuller — Quyết định entity nào được skip render dựa trên FPS.
 *
 * ### Entity skip logic
 * - FPS normal (≥ target): không skip
 * - FPS low (< target): skip entity ngoài khoảng cách dynamic
 * - FPS critical (< 20): skip thêm entity không quan trọng (item, experience orb, etc.)
 * - FPS extreme (< 10): chỉ render player entities
 *
 * Dùng chung với [FpsManager] và [PerformanceOptimizer].
 */
public class EntityCuller {

    /** Entity skip có bật không (set từ PerformanceOptimizer) */
    public static boolean enabled = true;

    /** Khoảng cách tối đa để render entity (blocks) */
    private static double maxRenderDistance = 64.0;

    /** Khoảng cách tối thiểu khi FPS cực thấp */
    private static final double MIN_DISTANCE = 8.0;

    /**
     * Lấy khoảng cách render tối đa dựa trên FPS hiện tại.
     * Distances aggressive hơn để cải thiện FPS rõ rệt:
     * - Normal (≥ target): 128 blocks (PvP tầm gần-trung)
     * - Light (≥ 30 FPS): 32 blocks
     * - Moderate (≥ 20 FPS): 24 blocks
     * - Critical (≥ 10 FPS): 16 blocks
     * - Extreme (< 10 FPS): 8 blocks (chỉ render entities gần nhất)
     *
     * @return Khoảng cách (blocks). Entity ngoài khoảng này sẽ bị skip.
     */
    public static double getMaxRenderDistance() {
        if (!enabled) return 256.0;  // Effectively unlimited
        int fps = FpsManager.INSTANCE.getCurrentFps();
        if (fps <= 0) return 256.0;

        double maxDist;
        if (fps >= FpsManager.INSTANCE.getTargetFps()) {
            maxDist = 128.0;   // Normal: 128 blocks đủ cho PvP tầm gần-trung
        } else if (fps >= 30) {
            maxDist = 32.0;    // Light FPS drop
        } else if (fps >= 20) {
            maxDist = 24.0;    // Moderate
        } else if (fps >= 10) {
            maxDist = 16.0;    // Critical
        } else {
            maxDist = MIN_DISTANCE;  // Extreme: 8 blocks
        }

        maxRenderDistance = maxDist;
        return maxDist;
    }

    /**
     * Kiểm tra xem entity có nên bị skip khi FPS critical không.
     * Ở FPS critical, skip các entity không quan trọng (item, xp orb, etc.)
     *
     * @param entityClass Class của entity cần kiểm tra
     * @return true nên skip
     */
    public static boolean shouldSkipNonEssential(Class<?> entityClass) {
        if (!enabled) return false;
        int fps = FpsManager.INSTANCE.getCurrentFps();
        if (fps >= 20) return false;  // Only skip essentials when critical+

        String name = entityClass.getSimpleName();
        // Skip non-essential entities when FPS is very low
        if (fps < 10) {
            // Extreme: only render players
            return !name.contains("Player");
        }
        // Critical (10-19): skip items, xp, arrows, etc.
        return name.contains("ItemEntity")
            || name.contains("ExperienceOrb")
            || name.contains("ArrowEntity")
            || name.contains("SpectralArrowEntity")
            || name.contains("TridentEntity")
            || name.contains("FireworkRocketEntity");
    }

    public static void reset() {
        maxRenderDistance = 64.0;
    }
}
