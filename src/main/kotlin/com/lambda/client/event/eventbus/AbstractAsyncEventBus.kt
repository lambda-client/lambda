package com.lambda.client.event.eventbus

import com.lambda.client.event.ListenerManager

/**
 * [IAsyncEventBus] with some basic implementation
 * Must be used with Kotlinx Coroutine and overridden [post] method
 */
abstract class AbstractAsyncEventBus : AbstractEventBus(), IAsyncEventBus {
    override fun subscribe(objs: Any) {
        super.subscribe(objs)

        ListenerManager.getAsyncListeners(objs)?.forEach {
            subscribedListenersAsync.getOrPut(it.eventClass, ::newSetAsync).add(it)
        }
    }

    override fun unsubscribe(objs: Any) {
        super.unsubscribe(objs)

        ListenerManager.getAsyncListeners(objs)?.forEach {
            subscribedListenersAsync[it.eventClass]?.remove(it)
        }
    }
}