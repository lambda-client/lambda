@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.quality

import com.minato.module.modules.render.swinganimation.QualityPreset

/**
 * AutoQualityDetector — tự động phát hiện quality preset dựa trên FPS.
 *
 * ### Nguyên lý
 * 1. Theo dõi frame durations qua 1 sliding window (mặc định 300 frames ≈ 5s ở 60fps)
 * 2. Tính FPS trung bình từ window
 * 3. So sánh với thresholds để chọn preset
 * 4. Hysteresis: Không đổi preset quá thường xuyên (cooldown 2 giây)
 *
 * ### Thread Safety
 * - Tất cả state được update từ render thread (qua TickEvent)
 * - Không cần lock
 *
 * ### Thresholds
 * | FPS Range   | Preset |
 * |-------------|--------|
 * | >= 120      | ULTRA  |
 * | >= 60       | HIGH   |
 * | >= 30       | MEDIUM |
 * | < 30        | LOW    |
 */
object AutoQualityDetector {

    private const val FPS_ULTRA = 120
    private const val FPS_HIGH = 60
    private const val FPS_MEDIUM = 30

    /** Cooldown giữa các lần đổi preset (nanos) */
    private const val SWITCH_COOLDOWN_NANOS = 2_000_000_000L  // 2 giây

    /** Kích thước sliding window (số frame) */
    private val WINDOW_SIZE = 300  // ~5s ở 60fps

    // ── Runtime state ───────────────────────────────────────

    /** Sliding window của frame durations (nanos) */
    private val frameDurations = mutableListOf<Long>()

    /** Timestamp của frame trước (nanoTime) */
    private var lastFrameTime = 0L

    /** Thời điểm lần cuối đổi preset */
    private var lastSwitchTime = 0L

    /** Preset hiện tại */
    private var _effectivePreset: QualityPreset = QualityPreset.MEDIUM

    /** Frame counter */
    private var frameCount = 0

    // ── Public API ──────────────────────────────────────────

    /** Preset được detect tự động */
    val effectivePreset: QualityPreset
        get() = _effectivePreset

    /** FPS trung bình (làm tròn) */
    val averageFps: Int
        get() {
            if (frameDurations.isEmpty()) return 0
            val avgDuration = frameDurations.average()
            return if (avgDuration > 0) (1_000_000_000.0 / avgDuration).toInt() else 0
        }

    /** Đã có đủ dữ liệu để detect chưa? */
    val hasEnoughData: Boolean
        get() = frameDurations.size >= 60  // cần ít nhất 1 giây dữ liệu

    // ── Tick (gọi mỗi frame từ TickEvent) ───────────────────

    /**
     * Gọi mỗi frame từ TickEvent.
     * Record frame duration và cập nhật preset tự động.
     *
     * @param currentTimeNanos System.nanoTime() hiện tại
     */
    fun tick(currentTimeNanos: Long) {
        if (lastFrameTime == 0L) {
            lastFrameTime = currentTimeNanos
            frameCount++
            return
        }

        val duration = currentTimeNanos - lastFrameTime
        lastFrameTime = currentTimeNanos

        frameDurations.add(duration)

        // Trim window
        while (frameDurations.size > WINDOW_SIZE) {
            frameDurations.removeFirstOrNull()
        }

        frameCount++

        // Chỉ evaluate mỗi 20 frame (tránh CPU waste)
        if (frameCount % 20 != 0) return
        if (frameDurations.size < 10) return  // chưa đủ data

        evaluate()
    }

    /**
     * Reset toàn bộ state.
     */
    fun reset() {
        frameDurations.clear()
        lastFrameTime = 0L
        _effectivePreset = QualityPreset.MEDIUM
        lastSwitchTime = 0L
        frameCount = 0
    }

    // ── Internal ────────────────────────────────────────────

    private fun evaluate() {
        val fps = averageFps
        val targetPreset = getTargetPreset(fps)
        val now = System.nanoTime()

        if (targetPreset != _effectivePreset && (now - lastSwitchTime) > SWITCH_COOLDOWN_NANOS) {
            _effectivePreset = targetPreset
            lastSwitchTime = now
        }
    }

    private fun getTargetPreset(fps: Int): QualityPreset = when {
        fps >= FPS_ULTRA -> QualityPreset.ULTRA
        fps >= FPS_HIGH  -> QualityPreset.HIGH
        fps >= FPS_MEDIUM -> QualityPreset.MEDIUM
        else              -> QualityPreset.LOW
    }

    /**
     * Trả về tên preset + FPS để hiển thị trong panel.
     */
    fun getFormattedInfo(): String {
        val preset = _effectivePreset
        val fps = averageFps
        return "AUTO [${preset.name}] @ ${fps}FPS"
    }
}
