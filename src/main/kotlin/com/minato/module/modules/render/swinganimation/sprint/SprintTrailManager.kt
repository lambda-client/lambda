@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.sprint

import com.minato.Minato.mc
import net.minecraft.util.math.Vec3d
import kotlin.math.sqrt

/**
 * SprintTrailManager — quản lý trạng thái sprint và velocity,
 * cung cấp dữ liệu cho các layer hiệu ứng (dust, speed lines, vignette, afterimage).
 *
 * ### Usage
 * ```kotlin
 * SprintTrailManager.update()  // gọi mỗi tick
 * val intensity = SprintTrailManager.speedIntensity  // 0.0..1.0
 * ```
 */
object SprintTrailManager {

    /** Đang sprint? */
    var isSprinting = false
        private set

    /** Horizontal speed (blocks/tick) */
    var horizontalSpeed = 0.0
        private set

    /** Speed intensity 0.0..1.0 (normalized: 0 = stand, 1 = sprint velocity ~0.286) */
    var speedIntensity = 0f
        private set

    /** For smoothing transition */
    private var smoothIntensity = 0f
    private const val SMOOTH_FACTOR = 0.15f

    /** Vị trí chân player qua các tick gần nhất (cho afterimage) */
    private val _positionHistory = ArrayDeque<Vec3d>(20)
    val positionHistory: List<Vec3d> get() = _positionHistory.toList()

    /** Đã khởi tạo? */
    private var initialized = false

    /** Reset về 0 */
    fun reset() {
        isSprinting = false
        horizontalSpeed = 0.0
        speedIntensity = 0f
        smoothIntensity = 0f
        _positionHistory.clear()
        initialized = false
    }

    /**
     * Gọi mỗi tick từ TickEvent.Pre để cập nhật trạng thái.
     */
    fun update(sprinting: Boolean) {
        val player = mc.player ?: return

        // Update sprint state
        isSprinting = sprinting

        // Tính horizontal speed
        val vel = player.velocity
        horizontalSpeed = sqrt(vel.x * vel.x + vel.z * vel.z)

        // Speed intensity: 0..1  (max sprint speed ~0.286 blocks/tick)
        val rawIntensity = (horizontalSpeed / 0.286).coerceIn(0.0, 1.0).toFloat()

        // Smooth transition
        if (!initialized) {
            smoothIntensity = rawIntensity
            initialized = true
        }
        smoothIntensity += (rawIntensity - smoothIntensity) * SMOOTH_FACTOR
        speedIntensity = smoothIntensity

        // Position history for afterimage
        if (isSprinting && speedIntensity > 0.3f) {
            val pos = player.pos
            // Only record if moved enough
            if (_positionHistory.isEmpty() || _positionHistory.last().squaredDistanceTo(pos) > 0.04) {
                _positionHistory.addLast(pos)
                if (_positionHistory.size > 20) {
                    _positionHistory.removeFirst()
                }
            }
        }

    }
}
