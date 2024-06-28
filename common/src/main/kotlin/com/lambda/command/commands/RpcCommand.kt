package com.lambda.command.commands

import com.lambda.brigadier.*
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.command.LambdaCommand
import com.lambda.module.modules.client.DiscordRPC
import com.lambda.util.primitives.extension.CommandBuilder

object RpcCommand : LambdaCommand(
    name = "rpc",
    description = "Discord Rich Presence commands.",
    usage = "rpc <join [id] | accept>"
) {
    override fun CommandBuilder.create() {
        required(literal("join")) {
            required(word("id")) { id ->
                execute {
                    DiscordRPC.join(id().value())
                }
            }
        }

        required(literal("accept")) {
            execute {
                DiscordRPC.join()
            }
        }
    }
}
