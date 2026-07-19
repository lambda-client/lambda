@file:Suppress("unused")

package com.minato.module.modules.render.swinganimation

/**
 * Generic object pool — giảm GC pressure bằng cách tái sử dụng objects.
 *
 * ### Usage
 * ```kotlin
 * val pool = ObjectPool<ActiveSwingEffect>(maxSize = 30) {
 *     ActiveSwingEffect(SwingContext(...), ...)
 * }
 *
 * // Lấy từ pool (hoặc tạo mới nếu pool rỗng):
 * val effect = pool.obtain { ActiveSwingEffect(ctx, historyPoints) }
 *
 * // Trả về pool khi không dùng nữa:
 * pool.free(effect)  // gọi effect.reset() tự động
 * ```
 */
class ObjectPool<T : Any>(
    /** Kích thước tối đa của pool */
    private val maxSize: Int = 30,
    /** Factory fallback để tạo instance mới khi pool rỗng */
    private val factory: () -> T = { throw IllegalStateException("Pool empty and no factory provided") },
    /** Callback reset — gọi khi trả object về pool */
    private val resetFn: (T) -> Unit = {},
) {
    private val pool = ArrayDeque<T>(maxSize)

    /** Số objects hiện có trong pool */
    val size: Int get() = pool.size

    /** Tổng số objects đã tạo (theo dõi allocation) */
    var totalCreated: Int = 0
        private set

    /**
     * Lấy một instance từ pool.
     * Nếu pool rỗng, tạo mới qua [factory].
     * Nếu pool không rỗng, lấy instance cũ (đã được reset).
     */
    fun obtain(fallbackFactory: (() -> T)? = null): T {
        val instance = pool.removeFirstOrNull()
        if (instance != null) return instance

        totalCreated++
        return (fallbackFactory ?: factory)()
    }

    /**
     * Trả instance về pool.
     * Gọi [resetFn] trên instance trước khi lưu.
     * Nếu pool đã đầy, instance bị discard (GC sẽ thu hồi).
     */
    fun free(instance: T) {
        resetFn(instance)
        if (pool.size < maxSize) {
            pool.addLast(instance)
        }
    }

    /** Xóa toàn bộ pool */
    fun clear() {
        pool.clear()
    }
}
