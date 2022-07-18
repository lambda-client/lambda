package com.lambda.client.buildtools.task.sequence.strategies

import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.sequence.TaskSequenceStrategy

object OriginStrategy : TaskSequenceStrategy {
    override fun getNextTask(tasks: List<BuildTask>): BuildTask? {
        val sortedTasks = tasks.sortedWith(compareBy { it.priority })

        return sortedTasks.firstOrNull()
    }
}