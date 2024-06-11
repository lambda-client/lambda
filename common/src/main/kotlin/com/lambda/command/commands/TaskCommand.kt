package com.lambda.command.commands

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.task.RootTask
import com.lambda.util.Communication.info
import com.lambda.util.primitives.extension.CommandBuilder

object TaskCommand : LambdaCommand(
    name = "task",
    usage = "task <cancel>",
    description = "Control tasks"
) {
    override fun CommandBuilder.create() {
        required(literal("cancel")) {
            execute {
                this@TaskCommand.info("Cancelling all tasks")
                RootTask.cancel()
            }
        }
    }
}