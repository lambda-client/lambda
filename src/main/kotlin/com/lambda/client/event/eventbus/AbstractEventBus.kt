package com.lambda.client.event.eventbus

import com.lambda.client.event.ListenerManager
import com.lambda.client.event.listener.Listener

/**
 * [IEventBus] with some basic implementation
 */
abstract class AbstractEventBus : IEventBus {
    override fun subscribe(objs: Any) {
        ListenerManager.getListeners(objs)?.forEach {
            subscribedListeners.getOrPut(it.eventClass, ::newSet).add(it)
        }
    }

    override fun unsubscribe(objs: Any) {
        ListenerManager.getListeners(objs)?.forEach {
            subscribedListeners[it.eventClass]?.remove(it)
        }
    }

    override fun post(event: Any) {
        subscribedListeners[event.javaClass]?.let {
            @Suppress("UNCHECKED_CAST") // IDE meme
            for (listener in it) (listener as Listener<Any>).function.invoke(event)
        }
    }
}