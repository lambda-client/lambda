package com.lambda.client.event.listener

import com.lambda.client.event.ListenerManager
import com.lambda.client.event.eventbus.IAsyncEventBus


/**
 * Default priority for listeners
 */
const val DEFAULT_PRIORITY = 0

/**
 * Create and register a new async listener for this object
 * Must be used with Kotlinx Coroutine and a implementation of [IAsyncEventBus]
 *
 * @param T type of the target event
 * @param function action to perform when this listener gets called by event bus
 */
inline fun <reified T : Any> Any.asyncListener(noinline function: suspend (T) -> Unit) {
    this.asyncListener(T::class.java, function)
}

/**
 * Create and register a new async listener for this object
 * Must be used with Kotlinx Coroutine and a implementation of [IAsyncEventBus]
 *
 * @param T type of the target event
 * @param clazz class of the target event
 * @param function action to perform when this listener gets called by event bus
 */
fun <T : Any> Any.asyncListener(clazz: Class<T>, function: suspend (T) -> Unit) {
    ListenerManager.register(this, AsyncListener(this, clazz, function))
}

/**
 * Create and register a new listener for this object
 *
 * @param T type of the target event
 * @param priority priority of this listener when calling by event bus
 * @param function action to perform when this listener gets called by event bus
 */
inline fun <reified T : Any> Any.listener(priority: Int = DEFAULT_PRIORITY, noinline function: (T) -> Unit) {
    this.listener(priority, T::class.java, function)
}

/**
 * Create and register a new listener for this object
 *
 * @param T type of the target event
 * @param clazz class of the target event
 * @param priority priority of this listener when calling by event bus
 * @param function action to perform when this listener gets called by event bus
 */
fun <T : Any> Any.listener(priority: Int = DEFAULT_PRIORITY, clazz: Class<T>, function: (T) -> Unit) {
    ListenerManager.register(this, Listener(this, clazz, priority, function))
}

/**
 * Implementation of [AbstractListener] with suspend block
 * Must be used with Kotlinx Coroutine and a implementation of [IAsyncEventBus]
 */
class AsyncListener<T : Any>(
    owner: Any,
    override val eventClass: Class<T>,
    override val function: suspend (T) -> Unit
) : AbstractListener<T, suspend (T) -> Unit>(owner) {
    override val priority: Int = DEFAULT_PRIORITY
}

/**
 * Basic implementation of [AbstractListener]
 */
class Listener<T : Any>(
    owner: Any,
    override val eventClass: Class<T>,
    override val priority: Int,
    override val function: (T) -> Unit
) : AbstractListener<T, (T) -> Unit>(owner)


