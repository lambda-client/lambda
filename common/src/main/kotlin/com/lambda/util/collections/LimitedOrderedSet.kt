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