/*
 * Copyright 2024 Lambda
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

package com.lambda.util.collections

import java.time.Instant
import java.util.concurrent.ConcurrentLinkedQueue

class LimitedDecayQueue<E>(
    private var sizeLimit: Int,
    private var interval: Long,
) : ConcurrentLinkedQueue<Pair<E, Instant>>() {
    @Synchronized
    @JvmName("jvmAdd")
    fun add(element: E): Boolean {
        cleanUp()
        return if (size < sizeLimit) {
            add(element to Instant.now())
            true
        } else {
            false
        }
    }

    @Synchronized
    fun setMaxSize(newSize: Int) {
        sizeLimit = newSize
        cleanUp()
    }

    @Synchronized
    fun setInterval(newInterval: Long) {
        interval = newInterval
        cleanUp()
    }

    @Synchronized
    private fun cleanUp() {
        val now = Instant.now()
        while (isNotEmpty() && now.minusMillis(interval).isAfter(peek().second)) {
            poll()
        }
    }
}
