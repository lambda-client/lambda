package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.Task

class HelloWorldTask @Ta5kBuilder constructor() : Task<Unit>() {
    init {
        listener<TickEvent.Pre> {
            LOG.info("$name: ticking $age")

            if (age > 20) {
                success(Unit)
            }
        }
    }

    companion object {
        @Ta5kBuilder
        fun Task<*>.helloWorld() = HelloWorldTask()
        @Ta5kBuilder
        fun SubTaskBuilder.helloWorld() = HelloWorldTask().also { tasks.add(it) }
    }
}