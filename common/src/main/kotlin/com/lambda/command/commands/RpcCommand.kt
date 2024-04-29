package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.execute
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.CommandManager.register
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.client.DiscordRPC

object RpcCommand : LambdaCommand {
    override val name = "rpc"

    // TODO: What the fuck am I even doing here ??
    init {
        register(name) {
            required(word("action")) { action ->
                val actions = listOf("join", "accept")

                suggests { _, builder ->
                    actions.forEach {
                        builder.suggest(it)
                    }
                    builder.buildFuture()
                }

                executeWithResult {
                    val action = action().value()
                    if (action !in actions) {
                        return@executeWithResult CommandResult.failure("Invalid action $action. Did you mean ${actions.joinToString()}?")
                    }

                    when (action) {
                        //"join" -> DiscordRPC.join()
                        "accept" -> DiscordRPC.accept()
                    }
                    CommandResult.success()
                }
            }
        }
    }
}
