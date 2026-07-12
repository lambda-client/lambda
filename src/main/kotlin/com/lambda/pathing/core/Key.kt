/*
 * Copyright 2026 Lambda
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

package com.lambda.pathing.core

/**
 * Lexicographic priority key used by D* Lite.
 */
data class Key(
    val first: Double,
    val second: Double,
) : Comparable<Key> {
    /** This comparison sits in every heap sift; avoid compareValuesBy/property-reference overhead. */
    override fun compareTo(other: Key): Int {
        val firstComparison = first.compareTo(other.first)
        return if (firstComparison != 0) firstComparison else second.compareTo(other.second)
    }

    override fun toString() = "(%.3f, %.3f)".format(first, second)

    companion object {
        val INFINITY = Key(Double.POSITIVE_INFINITY, Double.POSITIVE_INFINITY)
    }
}
