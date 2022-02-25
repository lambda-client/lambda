package com.lambda.client.event.listener

import com.lambda.client.commons.interfaces.Nameable
import java.lang.ref.WeakReference
import java.util.concurrent.atomic.AtomicInteger
import kotlin.reflect.KProperty

abstract class AbstractListener<E : Any, F>(owner: Any) : IListener<E, F> {
    final override val id: Int = listenerId.getAndIncrement()
    final override val owner: Any? by WeakReference(owner)
    final override val ownerName: String = if (owner is Nameable) owner.name else owner.javaClass.simpleName

    operator fun <T> WeakReference<T>.getValue(thisRef: Any?, property: KProperty<*>) = get()

    override fun compareTo(other: IListener<*, *>): Int {
        val result = priority.compareTo(other.priority)
        return if (result != 0) result
        else id.compareTo(other.id) // :monkey: code for getting around TreeSet duplicated check
    }

    override fun equals(other: Any?): Boolean {
        return this === other
            || (other is IListener<*, *>
            && other.eventClass == this.eventClass
            && other.id == this.id)
    }

    override fun hashCode(): Int {
        return 31 * eventClass.hashCode() + id.hashCode()
    }

    companion object {
        private val listenerId = AtomicInteger(Int.MIN_VALUE)
    }
}