package com.lambda.command.commands

import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.Configuration
import com.lambda.util.primitives.extension.CommandBuilder

object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg"),
    usage = "config <save|load>",
    description = "Save or load the configuration files"
) {
    override fun CommandBuilder.create() {
        required(literal("save")) {
            execute {
                Configuration.configurations.forEach {
                    it.trySave()
                }
            }
        }
        required(literal("load")) {
            execute {
                Configuration.configurations.forEach {
                    it.tryLoad()
                }
            }
        }
    }
}