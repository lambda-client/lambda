
package com.minato.util

import com.minato.context.SafeContext
import com.minato.threading.runSafe
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A utility class to manage time-based operations, such as delays and periodic tasks.
 */
class Timer {
    private var lastTiming = TimeSource.Monotonic.markNow()

    fun timePassed(duration: Duration): Boolean =
        lastTiming.elapsedNow() > duration

    fun delayIfPassed(duration: Duration): Boolean =
        timePassed(duration).apply {
            if (this) reset()
        }

    fun runIfPassed(duration: Duration, reset: Boolean = true, block: () -> Unit) =
        timePassed(duration).apply {
            if (!this) return@apply
            if (reset) reset()

            block()
        }

    fun runIfNotPassed(duration: Duration, reset: Boolean = true, block: () -> Unit) =
        timePassed(duration).also { passed ->
            if (passed) return@also
            if (reset) reset()

            block()
        }

    fun runSafeIfPassed(duration: Duration, reset: Boolean = true, block: SafeContext.() -> Unit) =
        timePassed(duration).also { passed ->
            if (!passed) return@also

            runSafe {
                if (reset) reset()
                block()
            }
        }

    fun runSafeIfNotPassed(duration: Duration, reset: Boolean = true, block: SafeContext.() -> Unit) =
        timePassed(duration).also { passed ->
            if (passed) return@also

            runSafe {
                if (reset) reset()
                block()
            }
        }

    fun reset(additionalDelay: Duration = Duration.ZERO) {
        lastTiming = TimeSource.Monotonic.markNow() + additionalDelay
    }
}