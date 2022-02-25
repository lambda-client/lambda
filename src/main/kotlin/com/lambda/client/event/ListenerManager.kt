package com.lambda.client.event

import com.lambda.client.event.listener.AsyncListener
import com.lambda.client.event.listener.Listener
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Used for storing the map of objects and their listeners
 */
object ListenerManager {

    private val listenerMap = ConcurrentHashMap<Any, CopyOnWriteArrayList<Listener<*>>>()

    private val asyncListenerMap = ConcurrentHashMap<Any, CopyOnWriteArrayList<AsyncListener<*>>>()

    /**
     * Register the [listener] to the [ListenerManager]
     *
     * @param obj object of the [listener] belongs to
     * @param listener listener to register
     */
    fun register(obj: Any, listener: Listener<*>) {
        listenerMap.getOrPut(obj, ::CopyOnWriteArrayList).add(listener)
    }

    /**
     * Register the [asyncListener] to the [ListenerManager]
     *
     * @param obj object of the [asyncListener] belongs to
     * @param asyncListener async listener to register
     */
    fun register(obj: Any, asyncListener: AsyncListener<*>) {
        asyncListenerMap.getOrPut(obj, ::CopyOnWriteArrayList).add(asyncListener)
    }

    /**
     * Unregister all listeners of this object, this can not be undone
     */
    fun unregister(obj: Any) {
        listenerMap.remove(obj)
        asyncListenerMap.remove(obj)
    }

    /**
     * Get all registered listeners of this [obj]
     *
     * @param obj object to get listeners
     *
     * @return registered listeners of [obj]
     */
    fun getListeners(obj: Any): List<Listener<*>>? = listenerMap[obj]

    /**
     * Get all registered async listeners of this [obj]
     *
     * @param obj object to get async listeners
     *
     * @return registered async listeners of [obj]
     */
    fun getAsyncListeners(obj: Any): List<AsyncListener<*>>? = asyncListenerMap[obj]

}