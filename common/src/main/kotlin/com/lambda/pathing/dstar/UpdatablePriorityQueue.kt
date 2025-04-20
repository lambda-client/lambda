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

import java.util.*
import kotlin.NoSuchElementException
import kotlin.collections.HashMap

/**
 * A Priority Queue implementation supporting efficient updates and removals,
 * suitable for algorithms like D* Lite.
 *
 * @param V The type of the values (vertices/states) stored in the queue.
 * @param K The type of the keys (priorities) used for ordering, must be Comparable.
 */
class UpdatablePriorityQueue<V, K : Comparable<K>> {

    // Internal data class to hold value-key pairs within the Java PriorityQueue
    private data class Entry<V, K : Comparable<K>>(val value: V, var key: K) : Comparable<Entry<V, K>> {
        override fun compareTo(other: Entry<V, K>): Int = this.key.compareTo(other.key)
    }

    // The core priority queue storing Entry objects, ordered by key
    private val queue = PriorityQueue<Entry<V, K>>()
    // HashMap to map values to their corresponding Entry objects for quick access
    private val entryMap = HashMap<V, Entry<V, K>>()

    /**
     * Inserts a vertex/value 's' into the priority queue 'U' with priority 'k'.
     * Does nothing if the value already exists with the same key.
     * Updates the key if the value exists with a different key.
     * Corresponds to U.Insert(s, k) and parts of U.Update(s, k).
     *
     * @param value The value (vertex) to insert.
     * @param key The priority key associated with the value.
     */
    fun insert(value: V, key: K) {
        if (entryMap.containsKey(value)) {
            update(value, key) // Handle as an update if it already exists
        } else {
            val entry = Entry(value, key)
            entryMap[value] = entry
            queue.add(entry)
        }
    }

    /**
     * Changes the priority of vertex 's' in priority queue 'U' to 'k'.
     * Corresponds to U.Update(s, k).
     * It does nothing if the current priority of vertex s already equals k.
     *
     * @param value The value (vertex) whose key needs updating.
     * @param newKey The new priority key.
     * @throws NoSuchElementException if the value is not found in the queue.
     */
    fun update(value: V, newKey: K) {
        val entry = entryMap[value] ?: throw NoSuchElementException("Value not found in priority queue for update.")

        if (entry.key == newKey) {
            return // Key is the same, do nothing as per description
        }

        // Standard PriorityQueue doesn't support direct update.
        // We remove the old entry and add a new one with the updated key.
        queue.remove(entry)
        entry.key = newKey // Update the key in the existing entry object
        queue.add(entry) // Re-add the updated entry
    }

    /**
     * Removes vertex 's' from priority queue 'U'.
     * Corresponds to U.Remove(s).
     *
     * @param value The value (vertex) to remove.
     * @return True if the value was removed, false otherwise.
     */
    fun remove(value: V): Boolean {
        val entry = entryMap.remove(value)
        return if (entry != null) {
            queue.remove(entry)
        } else {
            false
        }
    }

    /**
     * Deletes the vertex with the smallest priority in priority queue 'U' and returns the vertex.
     * Corresponds to U.Pop().
     *
     * @return The value (vertex) with the smallest key.
     * @throws NoSuchElementException if the queue is empty.
     */
    fun pop(): V {
        if (isEmpty()) throw NoSuchElementException("Priority queue is empty.")
        val entry = queue.poll()
        entryMap.remove(entry.value)
        return entry.value
    }

    /**
     * Returns a vertex with the smallest priority of all vertices in priority queue 'U'.
     * Corresponds to U.Top().
     *
     * @return The value (vertex) with the smallest key.
     * @throws NoSuchElementException if the queue is empty.
     */
    fun top(): V {
        if (isEmpty()) throw NoSuchElementException("Priority queue is empty.")
        return queue.peek().value
    }

    /**
     * Returns the smallest priority of all vertices in priority queue 'U'.
     * Corresponds to U.TopKey().
     * Returns a representation of infinity if the queue is empty (specific to D* Lite context).
     *
     * @param infinityKey The key value representing infinity (e.g., DStarLiteKey.INFINITY).
     * @return The smallest key, or infinityKey if the queue is empty.
     */
    fun topKey(infinityKey: K) =
        if (isEmpty()) {
            infinityKey
        } else {
            queue.peek().key
        }

    /**
     * Checks if the priority queue contains the specified value (vertex).
     *
     * @param value The value to check for.
     * @return True if the value is present, false otherwise.
     */
    operator fun contains(value: V) = entryMap.containsKey(value)

    /**
     * Checks if the priority queue is empty.
     *
     * @return True if the queue contains no elements, false otherwise.
     */
    fun isEmpty() = queue.isEmpty()

    /**
     * Returns the number of elements in the priority queue.
     *
     * @return The size of the queue.
     */
    fun size() = queue.size

    /**
     * Removes all elements from the priority queue.
     * Corresponds to U <- empty set.
     */
    fun clear() {
        queue.clear()
        entryMap.clear()
    }
}