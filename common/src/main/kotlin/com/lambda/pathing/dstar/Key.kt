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
 * Represents the Key used in the D* Lite algorithm.
 * It's a pair of comparable values, typically Doubles or Ints.
 * Comparison is done lexicographically as described in Field D*.
 */
data class Key(val first: Double, val second: Double) : Comparable<Key> {
    override fun compareTo(other: Key) =
        compareValuesBy(this, other, { it.first }, { it.second })

    override fun toString() = "(%.3f, %.3f)".format(first, second)

    companion object {
        val INFINITY = Key(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }
}