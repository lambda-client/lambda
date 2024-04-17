package com.lambda.task

import com.lambda.threading.taskContext
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import java.util.concurrent.ConcurrentHashMap

object TaskRegistry {
    val registry = ConcurrentHashMap<Task<*>, Job>()

    val taskFlow = MutableSharedFlow<Task<*>>(
        extraBufferCapacity = 1000,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    suspend fun run(task: Task<*>) {
        taskFlow.emit(task)
    }

    fun tryRun(task: Task<*>) {
        taskFlow.tryEmit(task)
    }

    fun cancel(task: Task<*>) {
        task.cancel()
        registry[task]?.cancel()
        registry.remove(task)
    }

    init {
        taskContext {
            taskFlow.collect { task ->
//                val pending = taskFlow.replayCache.joinToString { it.name }
//                task.info("Executing task: ${task.name} ${if (pending.isNotEmpty()) "(Pending: $pending)" else ""}")
                val job = taskContext {
                    task.execute()
                }
                registry[task] = job
                job.join()
                registry.remove(task)
//                task.info("Task completed: ${task.name} after ${task.age}ms")
            }
        }
    }
}