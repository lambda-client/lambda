package com.lambda.pathing.core

class UpdatablePriorityQueue<V, K : Comparable<K>> {
    private val values = ArrayList<V>()
    private val keys = ArrayList<K>()
    private val indices = HashMap<V, Int>()

    fun insert(value: V, key: K) {
        val existing = indices[value]
        if (existing != null) {
            updateAt(existing, key)
            return
        }
        val newIndex = values.size
        values += value
        keys += key
        indices[value] = newIndex
        siftUp(newIndex)
    }

    fun update(value: V, key: K) {
        val index = indices[value] ?: throw NoSuchElementException("Value not found in priority queue")
        updateAt(index, key)
    }

    fun remove(value: V): Boolean {
        val index = indices.remove(value) ?: return false
        removeAt(index)
        return true
    }

    fun pop(): V {
        if (values.isEmpty()) throw NoSuchElementException("Priority queue is empty")
        val top = values[0]
        indices.remove(top)
        removeAt(0)
        return top
    }

    fun top(): V {
        if (values.isEmpty()) throw NoSuchElementException("Priority queue is empty")
        return values[0]
    }

    fun topKey(infinityKey: K): K = if (values.isEmpty()) infinityKey else keys[0]

    operator fun contains(value: V) = value in indices

    fun isEmpty() = values.isEmpty()

    fun size() = values.size

    fun clear() {
        values.clear()
        keys.clear()
        indices.clear()
    }

    private fun updateAt(index: Int, key: K) {
        val cmp = key.compareTo(keys[index])
        if (cmp == 0) return
        keys[index] = key
        if (cmp < 0) siftUp(index) else siftDown(index)
    }

    private fun removeAt(index: Int) {
        val lastIndex = values.size - 1
        if (index == lastIndex) {
            values.removeAt(lastIndex)
            keys.removeAt(lastIndex)
            return
        }
        val movedValue = values[lastIndex]
        val movedKey = keys[lastIndex]
        values.removeAt(lastIndex)
        keys.removeAt(lastIndex)
        values[index] = movedValue
        keys[index] = movedKey
        indices[movedValue] = index

        val parent = (index - 1) ushr 1
        if (index > 0 && movedKey < keys[parent]) siftUp(index) else siftDown(index)
    }

    private fun siftUp(start: Int) {
        var index = start
        val value = values[index]
        val key = keys[index]
        while (index > 0) {
            val parent = (index - 1) ushr 1
            if (key >= keys[parent]) break
            values[index] = values[parent]
            keys[index] = keys[parent]
            indices[values[index]] = index
            index = parent
        }
        values[index] = value
        keys[index] = key
        indices[value] = index
    }

    private fun siftDown(start: Int) {
        var index = start
        val size = values.size
        val value = values[index]
        val key = keys[index]
        val half = size ushr 1
        while (index < half) {
            var child = (index shl 1) + 1
            val right = child + 1
            if (right < size && keys[right] < keys[child]) {
                child = right
            }
            if (key <= keys[child]) break
            values[index] = values[child]
            keys[index] = keys[child]
            indices[values[index]] = index
            index = child
        }
        values[index] = value
        keys[index] = key
        indices[value] = index
    }
}
