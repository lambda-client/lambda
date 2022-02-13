package com.lambda.client.event.eventbus

import com.lambda.client.event.listener.AsyncListener

interface IAsyncEventBus : IEventBus {

    /**
     * A map for events and their subscribed listeners
     *
     * <Event, Set<Listener>>
     */
    val subscribedListenersAsync: MutableMap<Class<*>, MutableSet<AsyncListener<*>>>

    /**
     * Called when putting a new set to the map
     */
    fun newSetAsync(): MutableSet<AsyncListener<*>>

}