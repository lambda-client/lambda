package com.lambda.command.commands

import com.lambda.brigadier.*
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.command.CommandManager.register
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.client.DiscordRPC

object RpcCommand : LambdaCommand {
    override val name = "rpc"

    init {
        register(name) {
            required(literal("join")) {
                required(word("id")) { id ->
                    execute {
                        DiscordRPC.join(id().value())
                    }
                }
            }

            required(literal("accept")) {
                execute {
                    DiscordRPC.accept()
                }
            }
        }
    }
}
