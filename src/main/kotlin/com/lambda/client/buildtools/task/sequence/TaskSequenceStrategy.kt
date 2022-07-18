package com.lambda.client.buildtools.task.sequence

import com.lambda.client.buildtools.task.BuildTask

interface TaskSequenceStrategy {
    fun getNextTask(tasks: List<BuildTask>): BuildTask?
}