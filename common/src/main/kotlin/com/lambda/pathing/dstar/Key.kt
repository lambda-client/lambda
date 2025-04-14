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

/**
 * A Key is a pair (k1, k2) that is used to order vertices in the priority queue.
 * They are compared lexicographically.
 */
data class Key(val k1: Double, val k2: Double) : Comparable<Key> {
    override fun compareTo(other: Key) =
        when {
            this.k1 < other.k1 -> -1
            this.k1 > other.k1 -> 1
            this.k2 < other.k2 -> -1
            this.k2 > other.k2 -> 1
            else -> 0
        }
}