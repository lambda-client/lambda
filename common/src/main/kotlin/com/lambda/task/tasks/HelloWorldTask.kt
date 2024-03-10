package com.lambda.task.tasks

import com.lambda.Lambda.LOG
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.task.Task
import kotlinx.coroutines.delay

class HelloWorldTask : Task<Unit>() {
    init {
        listener<TickEvent.Pre> {
            LOG.info("${this@HelloWorldTask::class.simpleName}: Ticking")
        }
    }

    override suspend fun SafeContext.onAction() {
        LOG.info("Hello, World!")
        delay(250)
        LOG.info("Bye, World! Action completed")
    }
}