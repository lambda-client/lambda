package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.Ta5kBuilder
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import kotlinx.coroutines.delay

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
        @TaskCha1nBuilder
        fun Task<*>.helloWorld() = HelloWorldTask()
        @TaskCha1nBuilder
        fun SubTaskBuilder.helloWorld() = HelloWorldTask().also { tasks.add(it) }
    }
}