package com.lambda.client.commons.collections

class CloseableList<E>(
    val list: MutableList<E> = ArrayList()
) : MutableList<E> by list {

    private var closed = false

    fun close() {
        closed = true
    }

    override fun add(element: E) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.add(element)

    override fun add(index: Int, element: E) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.add(index, element)

    override fun addAll(index: Int, elements: Collection<E>) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.addAll(index, elements)

    override fun addAll(elements: Collection<E>) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.addAll(elements)

    override fun clear() =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.clear()

    override fun remove(element: E) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.remove(element)

    override fun removeAll(elements: Collection<E>) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.removeAll(elements.toSet())

    override fun removeAt(index: Int): E =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.removeAt(index)

    override fun retainAll(elements: Collection<E>) =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.retainAll(elements.toSet())

    override fun set(index: Int, element: E): E =
        if (closed) throw IllegalAccessException("This list is immutable!")
        else list.set(index, element)

}