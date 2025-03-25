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

package com.lambda.pathing.dstar

import com.lambda.util.world.FastVector
import java.util.*

/**
 * Priority queue for D* Lite 3D.
 * Supports: topKey, top, pop, insertOrUpdate, and remove.
 */
class PriorityQueueDStar {
    private val pq = PriorityQueue<Pair<FastVector, Key>>(compareBy { it.second })
    private val vertexToKey = mutableMapOf<FastVector, Key>()

    fun isEmpty() = pq.isEmpty()

    fun topKey(): Key {
        return if (pq.isEmpty()) Key(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
        else pq.peek().second
    }

    fun top() = pq.peek()?.first

    fun pop(): FastVector? {
        if (pq.isEmpty()) return null
        val (v, _) = pq.poll()
        vertexToKey.remove(v)
        return v
    }

    fun insertOrUpdate(v: FastVector, key: Key) {
        val oldKey = vertexToKey[v]
        if (oldKey == null || oldKey != key) {
            remove(v)
            vertexToKey[v] = key
            pq.add(Pair(v, key))
        }
    }

    fun remove(v: FastVector) {
        val oldKey = vertexToKey[v] ?: return
        vertexToKey.remove(v)
        pq.remove(Pair(v, oldKey))
    }
}