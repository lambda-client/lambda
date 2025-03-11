/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util

import com.lambda.context.SafeContext
import com.lambda.threading.runSafe
import kotlin.time.Duration
import kotlin.time.TimeSource

/**
 * A utility class to manage time-based operations, such as delays and periodic tasks.
 */
class Timer {
    /**
     * Records the timestamp of the last timing event using a monotonic clock.
     */
    private var lastTiming = TimeSource.Monotonic.markNow()

    /**
     * Checks if the specified amount of time has passed since the last timing event.
     *
     * @param duration the time interval to check.
     * @return `true` if the current time exceeds `lastTiming + duration`, `false` otherwise.
     */
    fun timePassed(duration: Duration): Boolean =
        lastTiming.elapsedNow() > duration

    /**
     * Checks if the specified duration has passed and resets the timer if true.
     *
     * @param duration the time interval to check.
     * @return `true` if the duration has passed and the timer was reset, `false` otherwise.
     */
    fun delayIfPassed(duration: Duration): Boolean =
        timePassed(duration).apply {
            if (this) reset()
        }

    /**
     * Executes a given block of code if the specified duration has passed since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param duration the time interval to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the time has passed.
     */
    fun runIfPassed(duration: Duration, reset: Boolean = true, block: () -> Unit) =
        timePassed(duration).apply {
            if (!this) return@apply
            if (reset) reset()

            block()
        }

    /**
     * Executes a given block of code if the specified duration has not passed yet since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param duration the time interval to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the duration has not passed.
     */
    fun runIfNotPassed(duration: Duration, reset: Boolean = true, block: () -> Unit) =
        timePassed(duration).also { passed ->
            if (passed) return@also
            if (reset) reset()

            block()
        }

    /**
     * Executes a given block of code in safe context if the specified duration has passed since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param duration the time interval to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the duration has passed.
     */
    fun runSafeIfPassed(duration: Duration, reset: Boolean = true, block: SafeContext.() -> Unit) =
        timePassed(duration).also { passed ->
            if (!passed) return@also

            runSafe {
                if (reset) reset()
                block()
            }
        }

    /**
     * Executes a given block of code in safe context if the specified duration has not passed yet since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param duration the time interval to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the duration has not passed.
     */
    fun runSafeIfNotPassed(duration: Duration, reset: Boolean = true, block: SafeContext.() -> Unit) =
        timePassed(duration).also { passed ->
            if (passed) return@also

            runSafe {
                if (reset) reset()
                block()
            }
        }

    /**
     * Resets the timer by updating the last timing event to the current time.
     *
     * @param additionalDelay an additional delay to add to the current time. Defaults to `Duration.ZERO`.
     */
    fun reset(additionalDelay: Duration = Duration.ZERO) {
        lastTiming = TimeSource.Monotonic.markNow() + additionalDelay
    }
}