package com.lambda.task

object RootTask : Task<Unit>() {
    init {
        name = "RootTask"
    }

    fun addInfo(debugText: MutableList<String>) {
        debugText.add("")
        debugText.addAll(info.string.split("\n"))
    }
}