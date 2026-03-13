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

package com.lambda.util.collections

class LimitedOrderedSet<E>(private val maxSize: Int) : LinkedHashSet<E>() {
    override fun add(element: E): Boolean {
        val added = super.add(element)
        if (size > maxSize) {
            val iterator = iterator()
            while (size > maxSize && iterator.hasNext()) {
                iterator.next()
                iterator.remove()
            }
        }
        return added
    }

    override fun addAll(elements: Collection<E>): Boolean {
        var added = false
        elements.forEach { if (add(it)) added = true }
        return added
    }
}
