@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.combo

/**
 * SpearComboTracker — theo dõi combo 3 đòn cho Spear weapon.
 *
 * ### Combo Stages
 * | Stage | Move | Duration | Radius | Arc |
 * |-------|------|:--------:|:------:|:---:|
 * | 1 | Thrust | 110ms | 1.1 blocks | 0° (thẳng) |
 * | 2 | Side Sweep | 170ms | 1.3 blocks | 100° |
 * | 3 | Spin Slash | 260ms | 1.6 blocks | 360° |
 *
 * ### Reset
 * - Auto reset về 0 nếu quá [comboWindowMs] kể từ đòn cuối
 * - Reset sau khi hoàn thành Spin Slash (stage 3)
 * - Reset thủ công qua [reset()]
 */
class SpearComboTracker(
    /** Cửa sổ thời gian tối đa giữa các đòn (ms) */
    var comboWindowMs: Int = 600,
) {
    /** Stage hiện tại: 0 = none, 1 = Thrust, 2 = Sweep, 3 = Spin Slash */
    var currentStage: Int = 0
        private set

    /** Timestamp đòn cuối cùng (nanoTime) */
    private var lastHitTime = 0L

    /** Đã hoàn thành full combo? */
    var isComboComplete: Boolean = false
        private set

    /** Đang trong combo? */
    val isInCombo: Boolean get() = currentStage > 0 && !isTimedOut

    /** Đã timeout? */
    val isTimedOut: Boolean
        get() = lastHitTime > 0L &&
                System.nanoTime() - lastHitTime > comboWindowMs * 1_000_000L

    /**
     * Đăng ký một hit mới.
     * @return stage mới (1-3)
     */
    fun registerHit(): Int {
        val now = System.nanoTime()

        // Reset nếu timeout
        if (isTimedOut) {
            currentStage = 0
        }

        // Reset nếu đã hoàn thành combo 3
        if (isComboComplete) {
            currentStage = 0
            isComboComplete = false
        }

        currentStage++
        lastHitTime = now

        // Stage 3 là đòn cuối
        if (currentStage >= 3) {
            isComboComplete = true
        }

        return currentStage
    }

    /** Reset combo về 0 */
    fun reset() {
        currentStage = 0
        lastHitTime = 0L
        isComboComplete = false
    }

    /** Thời gian đòn hiện tại đã trôi qua (0..1) dựa trên duration */
    fun getStageProgress(weaponType: SpearStage): Float {
        val elapsed = System.nanoTime() - lastHitTime
        val duration = weaponType.durationNanos
        return (elapsed.toFloat() / duration).coerceIn(0f, 1f)
    }

    /** Lấy thông tin stage hiện tại */
    fun getCurrentStageInfo(): SpearStage = when (currentStage) {
        1 -> SpearStage.THRUST
        2 -> SpearStage.SIDE_SWEEP
        3 -> SpearStage.SPIN_SLASH
        else -> SpearStage.THRUST
    }
}

/**
 * Thông số cho từng stage của spear combo.
 */
enum class SpearStage(
    /** Duration (nanos) */
    val durationNanos: Long,
    /** Radius của trail (blocks) */
    val radius: Float,
    /** Arc angle (độ) */
    val arcDegrees: Float,
    /** Độ dày trail */
    val trailWidth: Float,
    /** Màu sắc gradient (ARGB) - sáng dần theo combo */
    val colorStart: Int,
    val colorEnd: Int,
) {
    THRUST(110_000_000L, 1.1f, 0f, 0.08f, 0xFFA0E8B0.toInt(), 0xFF60C080.toInt()),
    SIDE_SWEEP(170_000_000L, 1.3f, 100f, 0.12f, 0xFF70D090.toInt(), 0xFF30A060.toInt()),
    SPIN_SLASH(260_000_000L, 1.6f, 360f, 0.18f, 0xFF40B070.toInt(), 0xFF108040.toInt());

    companion object {
        fun getLabel(stage: Int): String = when (stage) {
            1 -> "THRUST"
            2 -> "SWEEP"
            3 -> "SPIN SLASH"
            else -> ""
        }
    }
}
