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