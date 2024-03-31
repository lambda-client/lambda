package com.lambda.task

abstract class OptionalTask<Result>(
    val predicate: () -> Boolean
) : Task<Result>() {
    companion object {
        fun <T> (suspend () -> T).toOptionalTask(
            predicate: () -> Boolean
        ) = object : OptionalTask<T>(predicate) {
            override suspend fun onAction(): T = this@toOptionalTask()
        }
    }
}