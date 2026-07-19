@file:Suppress("unused")

package com.minato.module.modules.client

import com.minato.Minato.mc

/**
 * FpsManager — Singleton quản lý FPS tracking + quyết định tối ưu.
 *
 * ### Nguyên lý
 * - Dùng [MinecraftClient.currentFps] có sẵn thay vì tính toán thủ công
 * - Cung cấp các quyết định: skip frame, adjust render distance, adjust chunk rate
 * - Tất cả methods đều thread-safe (chỉ đọc từ render thread)
 *
 * ### Thresholds
 * | Mức | FPS | Hành động |
 * |-----|-----|-----------|
 * | Normal | ≥ targetFps | Không làm gì |
 * | Low | < targetFps | Giảm render distance, giảm chunk rate |
 * | Critical | < 20 | Skip 1/2 frame |
 * | Extreme | < 10 | Skip 2/3 frame, giảm mạnh render distance |
 */
object FpsManager {

    // ── Configuration (set từ PerformanceOptimizer module) ──

    /** Target FPS tối thiểu */
    var targetFps: Int = 60

    /** Bật frame skip? */
    var frameSkipEnabled: Boolean = true

    /** Bật dynamic render distance? */
    var renderDistanceEnabled: Boolean = true

    /** Render distance tối thiểu cho phép */
    var minRenderDistance: Int = 4

    /** Render distance tối đa */
    var maxRenderDistance: Int = 16

    /** Bật lazy chunk rebuild? */
    var chunkRebuildEnabled: Boolean = true

    /** Chunk rebuild rate tối thiểu */
    var minChunkRate: Int = 4

    /** Chunk rebuild rate tối đa (khi FPS cao) */
    var maxChunkRate: Int = 64

    // ── Internal state ──

    /** Đếm frame để skip theo chu kỳ */
    private var frameCounter = 0

    /** Render distance hiện tại (khi dynamic) */
    private var effectiveRenderDistance = 12

    // ── Public API ──

    /** FPS hiện tại từ MinecraftClient */
    val currentFps: Int get() = mc.currentFps

    /** FPS có đang thấp không? */
    val isFpsLow: Boolean get() = currentFps < targetFps && currentFps > 0

    /** FPS có đang critical không? (< 20) */
    val isFpsCritical: Boolean get() = currentFps in 1..20

    /** FPS có đang extreme không? (< 10) */
    val isFpsExtreme: Boolean get() = currentFps in 1..9

    /** FPS bình thường (≥ target)? */
    val isFpsNormal: Boolean get() = currentFps >= targetFps || currentFps <= 0

    /**
     * Có nên skip frame này không?
     * - Critical (< 20 FPS): skip 1/2 frame
     * - Extreme (< 10 FPS): skip 2/3 frame
     */
    fun shouldSkipFrame(): Boolean {
        if (!frameSkipEnabled) return false
        val fps = currentFps
        if (fps <= 0) return false
        if (fps >= targetFps) return false

        frameCounter++
        return if (fps < 10) {
            // Extreme: skip 2 out of 3 frames
            frameCounter % 3 != 0
        } else if (fps < 20) {
            // Critical: skip every other frame
            frameCounter % 2 == 0
        } else {
            false
        }
    }

    /**
     * Lấy render distance đã điều chỉnh dựa trên FPS.
     * @param original Render distance gốc từ Options
     */
    fun getAdjustedRenderDistance(original: Int): Int {
        if (!renderDistanceEnabled) return original
        val fps = currentFps
        if (fps <= 0 || fps >= targetFps) return original.coerceAtMost(maxRenderDistance)

        val adjusted = when {
            fps < 10 -> (original * 0.35).toInt().coerceIn(minRenderDistance, original)
            fps < 20 -> (original * 0.5).toInt().coerceIn(minRenderDistance, original)
            fps < 30 -> (original * 0.65).toInt().coerceIn(minRenderDistance, original)
            fps < targetFps -> (original * 0.8).toInt().coerceIn(minRenderDistance, original)
            else -> original
        }
        effectiveRenderDistance = adjusted
        return adjusted
    }

    /**
     * Lấy chunk rebuild rate đã điều chỉnh dựa trên FPS.
     * @param original Rate gốc từ settings
     */
    fun getAdjustedChunkRate(original: Int): Int {
        if (!chunkRebuildEnabled) return original
        val fps = currentFps
        if (fps <= 0 || fps >= targetFps) return original.coerceAtMost(maxChunkRate)

        return when {
            fps < 10 -> original.coerceAtMost(minChunkRate)
            fps < 20 -> (original * 0.3).toInt().coerceIn(minChunkRate, original)
            fps < 30 -> (original * 0.5).toInt().coerceIn(minChunkRate, original)
            fps < targetFps -> (original * 0.7).toInt().coerceIn(minChunkRate, original)
            else -> original
        }
    }

    /**
     * Reset state (gọi khi module disable)
     */
    fun reset() {
        frameCounter = 0
        effectiveRenderDistance = 12
    }
}
