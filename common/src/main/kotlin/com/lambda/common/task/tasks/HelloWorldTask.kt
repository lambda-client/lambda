package com.lambda.common.task.tasks

import com.lambda.common.Lambda.LOG
import com.lambda.common.context.SafeContext
import com.lambda.common.event.events.TickEvent
import com.lambda.common.event.listener.SafeListener.Companion.listener
import com.lambda.common.task.Task
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
