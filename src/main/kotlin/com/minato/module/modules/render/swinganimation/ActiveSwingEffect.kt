@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

import net.minecraft.util.math.Vec3d

/**
 * Quản lý vòng đời của một hiệu ứng swing đang active.
 * - [tipHistory]: ring buffer các vị trí đầu vũ khí
 * - [progress]: 0.0 → 1.0 (hoàn thành), sử dụng easing OUT_CUBIC để mượt
 * - Sử dụng [System.nanoTime] để tính duration (FPS-independent)
 *
 * ### Vấn đề tick-based cũ
 * Trước đây dùng `DURATION_TICKS = 8` với `tick()` gọi từ `TickEvent.Render.Pre`
 * (mỗi render frame). Ở 60fps, effect chỉ sống ~133ms — quá ngắn, user không thấy trail.
 * Giải pháp: time-based, effect sống đúng 350ms bất kể FPS.
 *
 * ### Easing
 * - progress: EaseOutCubic → chậm dần về cuối (giống vật lý thật)
 * - alpha: EaseOutQuad → mờ dần tự nhiên sau 40% thời gian
 */
class ActiveSwingEffect(
    /** Context của swing — mutable để hỗ trợ object pooling */
    var context: SwingContext,
    val maxHistoryPoints: Int = 12,  // theo Quality Preset
) {
    companion object {
        /** Thời gian sống của effect (nanoseconds) — ~350ms */
        const val DURATION_NS: Long = 350_000_000L
        /** Thời điểm bắt đầu fade (40% thời gian) */
        const val FADE_START_NS: Long = (DURATION_NS * 0.4f).toLong()
    }

    /** Lưu vị trí đầu vũ khí qua các frame (ring buffer) */
    private val _tipHistory = ArrayDeque<Vec3d>(maxHistoryPoints)
    val tipHistory: List<Vec3d> get() = _tipHistory.toList()

    /** Point count trong buffer */
    private var pointCount = 0

    /** Thời điểm effect được tạo (nanoseconds) */
    private var startTimeNanos = System.nanoTime()

    /** Thời gian đã sống (nanoseconds) */
    private val elapsedNs: Long get() = System.nanoTime() - startTimeNanos

    /**
     * Progress 0.0 → 1.5+ (fade phase).
     * Sử dụng EaseOutCubic để swing chậm dần tự nhiên.
     * Time-based: không phụ thuộc FPS.
     */
    val progress: Float
        get() {
            val raw = (elapsedNs.toFloat() / DURATION_NS).coerceIn(0f, 1.5f)
            return if (raw <= 1f) EaseFunctions.easeOutCubic(raw) else raw
        }

    /**
     * Alpha multiplier (fade out sau FADE_START_NS).
     * Sử dụng EaseOutQuad để mờ dần mượt.
     * Time-based: không phụ thuộc FPS.
     */
    val alpha: Float
        get() {
            if (elapsedNs <= FADE_START_NS) return 1f
            val fadeProgress = (elapsedNs - FADE_START_NS).toFloat() / (DURATION_NS - FADE_START_NS)
            return (1f - EaseFunctions.easeOutQuad(fadeProgress)).coerceAtLeast(0f)
        }

    /** Effect còn sống trong DURATION_NS (~350ms)? */
    val isAlive get() = elapsedNs <= DURATION_NS

    /** Thêm một frame position mới */
    fun addPoint(point: Vec3d) {
        if (pointCount < maxHistoryPoints) {
            _tipHistory.addLast(point)
            pointCount++
        } else {
            // Ring buffer: ghi đè cũ nhất
            _tipHistory.removeFirst()
            _tipHistory.addLast(point)
        }
    }

    /**
     * Tick effect — kiểm tra còn sống không.
     * Không cần tăng counter (time-based).
     * Trả về false nếu hết hạn.
     */
    fun tick(): Boolean {
        return isAlive
    }

    /**
     * Reset effect về trạng thái ban đầu để tái sử dụng (object pooling).
     * Set lại startTimeNanos để tính duration lại từ đầu.
     */
    fun reset() {
        _tipHistory.clear()
        pointCount = 0
        startTimeNanos = System.nanoTime()
    }

    /**
     * Re-initialize với context mới (object pooling).
     * Reset toàn bộ state + gán context mới.
     */
    fun reinit(ctx: SwingContext) {
        _tipHistory.clear()
        pointCount = 0
        startTimeNanos = System.nanoTime()
        context = ctx
    }
}
