@file:Suppress("unused")

package com.minato.module.modules.client

import com.minato.Minato.mc
import com.minato.event.events.TickEvent
import com.minato.event.listener.SafeListener.Companion.listen
import com.minato.module.Module
import com.minato.module.tag.ModuleTag

/**
 * PerformanceOptimizer — Tự động tối ưu FPS cho máy yếu.
 *
 * ### Tính năng
 * 1. **Frame Skip**: Tự động skip render frame khi FPS thấp (< target FPS)
 *    → Inject vào [MinecraftClientMixin.onLoopTick]
 * 2. **Dynamic Render Distance**: Tự động giảm render distance option khi FPS thấp
 *    → Gọi trực tiếp `mc.options.getViewDistance().setValue()` mỗi tick
 * 3. **Lazy Chunk Rebuild**: Giảm chunk rebuild rate khi FPS thấp
 *    → [FpsManager.getAdjustedChunkRate] dùng trong [ChunkedRenderer]
 *
 * Tích hợp với [FpsManager] để cung cấp state cho các mixin.
 */
object PerformanceOptimizer : Module(
    name = "Performance Optimizer",
    description = "Tự động tối ưu FPS: skip frame, giảm render distance, giảm chunk rebuild khi FPS thấp",
    tag = ModuleTag.CLIENT,
    enabledByDefault = false,
) {
    // ── FPS Target ──
    private val targetFps by setting("Target FPS", 60, 20..144, 5, "FPS mục tiêu tối thiểu")
    private val autoOptimize by setting("Auto Optimize", true, "Tự động kích hoạt tối ưu khi FPS thấp")

    // ── Frame Skip ──
    private val frameSkipEnabled by setting("Frame Skip", true, "Skip frame khi FPS quá thấp") { autoOptimize }

    // ── Render Distance ──
    private val dynamicRenderDistance by setting("Dynamic Render Distance", true, "Tự động giảm render distance khi FPS thấp") { autoOptimize }
    private val minRenderDistance by setting("Min Render Distance", 4, 2..16, 1, "Render distance tối thiểu") { dynamicRenderDistance }
    private val maxRenderDistance by setting("Max Render Distance", 12, 4..32, 2, "Render distance tối đa") { dynamicRenderDistance }

    // ── Chunk Rebuild ──
    private val lazyChunkRebuild by setting("Lazy Chunk Rebuild", true, "Giảm chunk rebuild rate khi FPS thấp") { autoOptimize }
    private val minChunkRate by setting("Min Chunk Rate", 4, 1..32, 1, "Chunk rebuild tối thiểu mỗi tick") { lazyChunkRebuild }

    // ── Entity Render Skip ──
    private val entitySkip by setting("Entity Skip", true, "Skip render entity ở xa khi FPS thấp") { autoOptimize }
    private val blockEntitySkip by setting("BlockEntity Skip", true, "Skip render block entity (sign, banner, chest...) khi FPS thấp") { autoOptimize }
    private val particleCap by setting("Particle Cap", true, "Giới hạn particle khi FPS thấp") { autoOptimize }
    private val _selectiveParticle by setting("Selective Particle Skip", true, "Skip firework/explosion/potion particles khi FPS thấp") { autoOptimize }

    /** Public accessor cho HUD */
    val selectiveParticleCulling: Boolean get() = isEnabled && _selectiveParticle

    /** Render distance gốc (để restore khi disable) */
    private var originalRenderDistance = -1

    /** Distance mà module đã set lần cuối — phát hiện user thay đổi thủ công */
    private var lastSetRenderDistance = -1

    /** Counter để throttle render distance check (20 ticks ~ 1 giây) */
    private var renderDistTickCounter = 0
    private val RENDER_DIST_CHECK_INTERVAL = 20

    init {
        onEnable {
            syncSettings()
            // Lưu render distance gốc
            originalRenderDistance = mc.options.viewDistance.value
            lastSetRenderDistance = originalRenderDistance
        }

        onDisable {
            FpsManager.reset()
            EntityCuller.enabled = false
            EntityCuller.reset()
            BlockEntityCuller.enabled = false
            BlockEntityCuller.reset()
            ParticleLimiter.enabled = false
            ParticleLimiter.reset()
            // Restore render distance về gốc
            if (originalRenderDistance > 0) {
                mc.options.viewDistance.setValue(originalRenderDistance)
            }
            renderDistTickCounter = 0
        }

        // Reset particle counter mỗi frame (trước khi render)
        listen<TickEvent.Render.Pre> {
            if (!isEnabled) return@listen
            ParticleLimiter.resetFrameCounter()
        }

        // Kiểm tra/cập nhật render distance — throttle mỗi 20 ticks (~1 giây)
        listen<TickEvent.Player.Pre> {
            if (!isEnabled) return@listen
            if (!dynamicRenderDistance) return@listen

            val fps = mc.currentFps
            if (fps <= 0) return@listen

            // ── Throttle ──
            renderDistTickCounter++
            if (renderDistTickCounter % RENDER_DIST_CHECK_INTERVAL != 0) return@listen

            val currentDist = mc.options.viewDistance.value

            // ── Phát hiện user thay đổi thủ công ──
            // Nếu currentDist khác lastSetRenderDistance, user đã tự chỉnh → cập nhật original
            if (currentDist != lastSetRenderDistance && lastSetRenderDistance > 0) {
                originalRenderDistance = currentDist
                lastSetRenderDistance = currentDist
                // User đã tự set, không cần adjust nữa
                return@listen
            }

            val adjustedDist = FpsManager.getAdjustedRenderDistance(currentDist)

            if (adjustedDist != currentDist) {
                mc.options.viewDistance.setValue(adjustedDist)
                lastSetRenderDistance = adjustedDist
            } else if (fps >= targetFps && currentDist < originalRenderDistance && originalRenderDistance > 0) {
                // Phục hồi dần về render distance gốc
                val restored = (currentDist + 1).coerceAtMost(originalRenderDistance)
                mc.options.viewDistance.setValue(restored)
                lastSetRenderDistance = restored
            }
        }
    }

    private fun syncSettings() {
        FpsManager.targetFps = targetFps
        FpsManager.frameSkipEnabled = frameSkipEnabled
        FpsManager.renderDistanceEnabled = dynamicRenderDistance
        FpsManager.minRenderDistance = minRenderDistance
        FpsManager.maxRenderDistance = maxRenderDistance
        FpsManager.chunkRebuildEnabled = lazyChunkRebuild
        FpsManager.minChunkRate = minChunkRate
        EntityCuller.enabled = entitySkip
        BlockEntityCuller.enabled = blockEntitySkip
        ParticleLimiter.enabled = particleCap
        ParticleLimiter.selectiveEnabled = _selectiveParticle
    }
}
