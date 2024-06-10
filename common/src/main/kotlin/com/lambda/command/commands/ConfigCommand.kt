package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.Configuration
import com.lambda.util.Communication.info
import com.lambda.util.primitives.extension.CommandBuilder

object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg"),
    usage = "config <save|load>",
    description = "Save or load the configuration files"
) {
    override fun CommandBuilder.create() {
        required(literal("save")) {
            executeWithResult {
                Configuration.configurations.forEach { config ->
                    config.trySave()?.let { return@executeWithResult failure(it) }
                }

                this@ConfigCommand.info("Saved ${Configuration.configurations.size} configuration files.")
                return@executeWithResult success()
            }
        }
        required(literal("load")) {
            executeWithResult {
                Configuration.configurations.forEach { config ->
                    config.tryLoad()?.let { return@executeWithResult failure(it) }
                }

                this@ConfigCommand.info("Loaded ${Configuration.configurations.size} configuration files.")
                return@executeWithResult success()
            }
        }
    }
}
