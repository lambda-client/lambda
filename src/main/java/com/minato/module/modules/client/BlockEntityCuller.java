
package com.minato.module.modules.client;

/**
 * BlockEntityCuller — Skip render block entities dựa trên FPS.
 *
 * ### BlockEntity skip logic
 * - FPS normal (≥ target): không skip
 * - FPS moderate (< target): skip block entities có tên trong blockList
 * - FPS critical (< 20): skip tất cả block entities không phải functional (chest, hopper...)
 * - FPS extreme (< 10): skip tất cả block entities
 *
 * ### Functional block entities (giữ ở moderate tier):
 * Chest, Barrel, ShulkerBox, Hopper, Furnace, BrewingStand, EnchantingTable, Beacon
 * (Những block cần nhìn thấy để tương tác PvP)
 *
 * ### Non-essential block entities (skip khi FPS thấp):
 * Sign, Banner, FlowerPot, Skull, Campfire, Bell, Lectern, DecoratedPot, Beehive
 *
 * Dùng chung với [FpsManager] và [PerformanceOptimizer].
 */
public class BlockEntityCuller {

    /** BlockEntity skip có bật không (set từ PerformanceOptimizer) */
    public static boolean enabled = true;

    /**
     * Kiểm tra xem block entity có nên bị skip render không.
     *
     * @param simpleName Tên đơn giản của block entity class (vd: "ChestBlockEntity")
     * @return true → skip render block entity này
     */
    public static boolean shouldSkip(String simpleName) {
        if (!enabled) return false;
        int fps = FpsManager.INSTANCE.getCurrentFps();
        if (fps <= 0 || fps >= FpsManager.INSTANCE.getTargetFps()) return false;

        // Extreme (< 10 FPS): skip tất cả block entities
        if (fps < 10) return true;

        // Critical (< 20 FPS): skip non-essential block entities
        if (fps < 20) {
            return isNonEssential(simpleName);
        }

        // Moderate (< target FPS): chỉ skip decorative/visual block entities
        if (fps < FpsManager.INSTANCE.getTargetFps()) {
            return isDecorative(simpleName);
        }

        return false;
    }

    /**
     * Block entities decorative/visual — skip ngay khi FPS hơi thấp.
     * Những block này không ảnh hưởng gameplay.
     */
    private static boolean isDecorative(String name) {
        return name.contains("Sign")
            || name.contains("Banner")
            || name.contains("FlowerPot")
            || name.contains("Skull")
            || name.contains("Campfire")
            || name.contains("Bell")
            || name.contains("Lectern")
            || name.contains("DecoratedPot")
            || name.contains("Beehive")
            || name.contains("BeeNest")
            || name.contains("ChiseledBook")
            || name.contains("HangingSign");
    }

    /**
     * Block entities non-essential — skip khi FPS critical.
     * Giữ lại các block functional (chest, hopper, furnace, v.v.)
     */
    private static boolean isNonEssential(String name) {
        return isDecorative(name)
            || name.contains("Candle")
            || name.contains("Cauldron")
            || name.contains("Comparator")
            || name.contains("DaylightDetector")
            || name.contains("Sculk")
            || name.contains("SculkSensor")
            || name.contains("CalibratedSculk")
            || name.contains("Amethyst")
            || name.contains("SporeBlossom")
            || name.contains("HangingRoots")
            || name.contains("GlowItemFrame")
            || name.contains("ItemFrame");
    }

    public static void reset() {
        // No internal state to reset
    }
}
