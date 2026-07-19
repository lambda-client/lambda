
package com.minato.command.commands

import com.minato.brigadier.argument.literal
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.task.RootTask
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder

object TaskCommand : MinatoCommand(
    name = "task",
    usage = "task <cancel|clear>",
    description = "Control tasks"
) {
    override fun CommandBuilder.create() {
        required(literal("cancel")) {
            execute {
                this@TaskCommand.info("Cancelling all tasks")
                RootTask.cancel()
            }
        }

        required(literal("clear")) {
            execute {
                this@TaskCommand.info("Clearing all tasks")
                RootTask.cancel()
            }
        }
    }
}
