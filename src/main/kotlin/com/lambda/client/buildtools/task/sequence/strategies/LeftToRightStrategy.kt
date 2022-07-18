package com.lambda.client.buildtools.task.sequence.strategies

import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.sequence.TaskSequenceStrategy

object LeftToRightStrategy : TaskSequenceStrategy {
    override fun getNextTask(tasks: List<BuildTask>): BuildTask? {
        return tasks.firstOrNull()
    }
}