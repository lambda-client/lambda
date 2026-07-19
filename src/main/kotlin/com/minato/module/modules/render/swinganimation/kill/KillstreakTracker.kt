@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation.kill

/**
 * KillstreakTracker — theo dõi số kill liên tiếp không chết.
 *
 * Streak labels tập trung tại [KillConfirmHud.formatStreakLabel].
 * Tracker chỉ quản lý số đếm + timeout, không duplicate labels.
 *
 * Reset về 0 khi:
 * - Player tự chết (gọi [onPlayerDeath])
 * - Quá thời gian [STREAK_TIMEOUT_MS] kể từ kill cuối
 */
object KillstreakTracker {
    /** Thời gian tối đa giữa 2 kill để streak không reset (ms) */
    private const val STREAK_TIMEOUT_MS = 10000L

    /** Streak hiện tại */
    var currentStreak: Int = 0
        private set

    /** Best streak trong session */
    var bestStreak: Int = 0
        private set

    /** Timestamp kill cuối cùng (ms) */
    private var lastKillTime = 0L

    /**
     * Đăng ký một kill mới.
     * @return streak hiện tại sau khi increment
     */
    fun registerKill(): Int {
        val now = System.currentTimeMillis()

        // Reset streak nếu quá timeout
        if (now - lastKillTime > STREAK_TIMEOUT_MS) {
            currentStreak = 0
        }

        currentStreak++
        lastKillTime = now

        if (currentStreak > bestStreak) {
            bestStreak = currentStreak
        }

        return currentStreak
    }

    /** Reset streak về 0 (gọi khi player chết) */
    fun onPlayerDeath() {
        currentStreak = 0
    }

    /** Reset toàn bộ */
    fun reset() {
        currentStreak = 0
        bestStreak = 0
        lastKillTime = 0L
    }
}
