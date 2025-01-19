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

/**
 * A utility class to manage time-based operations, such as delays and periodic tasks.
 */
class SimpleTimer {
    /**
     * Stores the timestamp of the last timing event.
     */
    private var lastTiming = 0L

    /**
     * Checks if the specified amount of time has passed since the last timing event.
     *
     * @param time the time interval in milliseconds to check.
     * @return `true` if the current time exceeds `lastTiming + time`, `false` otherwise.
     */
    fun timePassed(time: Long): Boolean =
        currentTime - lastTiming > time

    /**
     * Checks if the specified time has passed and resets the timer if true.
     *
     * @param time the time interval in milliseconds to check.
     * @return `true` if the time has passed and the timer was reset, `false` otherwise.
     */
    fun delayIfPassed(time: Long): Boolean =
        timePassed(time).apply {
            if (this) reset()
        }

    /**
     * Executes a given block of code if the specified time has passed since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param time the time interval in milliseconds to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the time has passed.
     */
    fun runIfPassed(time: Long, reset: Boolean = true, block: () -> Unit) =
        timePassed(time).apply {
            if (!this) return@apply
            if (reset) reset()

            block()
        }

    /**
     * Executes a given block of code if the specified time has not passed yet since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param time the time interval in milliseconds to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the time has passed.
     */
    fun runIfNotPassed(time: Long, reset: Boolean = true, block: () -> Unit) =
        timePassed(time).apply {
            if (this) return@apply
            if (reset) reset()

            block()
        }

    /**
     * Executes a given block of code in safe context if the specified time has passed since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param time the time interval in milliseconds to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the time has passed.
     */
    fun runSafeIfPassed(time: Long, reset: Boolean = true, block: SafeContext.() -> Unit) =
        timePassed(time).also { passed ->
            if (!passed) return@also

            runSafe {
                if (reset) reset()
                block()
            }
        }

    /**
     * Executes a given block of code in safe context if the specified time has not passed yet since the last timing event.
     * Optionally resets the timer after execution.
     *
     * @param time the time interval in milliseconds to check.
     * @param reset whether to reset the timer after running the block. Defaults to `true`.
     * @param block the code block to execute if the time has passed.
     */
    fun runSafeIfNotPassed(time: Long, reset: Boolean = true, block: SafeContext.() -> Unit) =
        timePassed(time).also { passed ->
            if (passed) return@also

            runSafe {
                if (reset) reset()
                block()
            }
        }

    /**
     * Resets the timer by updating the last timing event to the current time.
     *
     * @param additionalDelay an additional delay in milliseconds to add to the current time. Defaults to `0`.
     */
    fun reset(additionalDelay: Long = 0L) {
        lastTiming = currentTime + additionalDelay
    }

    companion object {
        private val currentTime get() = System.currentTimeMillis()
    }
}