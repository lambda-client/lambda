package com.lambda.command.commands

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.argument.word
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.optional
import com.lambda.brigadier.required
import com.lambda.command.CommandManager.register
import com.lambda.command.LambdaCommand
import com.lambda.config.Configuration

object ConfigCommand : LambdaCommand() {
    override val name = "config"

    init {
        register(name) {
            required(word("action")) { action ->
                val actions = listOf("save", "load")

                suggests { _, builder ->
                    actions.forEach {
                        builder.suggest(it)
                    }
                    builder.buildFuture()
                }

                optional(string("config name")) { configName ->
                    suggests { _, builder ->
                        Configuration.configurations.map {
                            it.configName
                        }.forEach {
                            builder.suggest(it)
                        }
                        builder.buildFuture()
                    }

                    executeWithResult {
                        val action = action().value()
                        if (action !in actions) {
                            return@executeWithResult failure("Invalid action $action. Did you mean ${actions.joinToString()}?")
                        }

                        if (configName == null) {
                            Configuration.configurations.forEach {
                                when (action) {
                                    "save" -> it.trySave()
                                    else -> it.tryLoad()
                                }
                            }
                        } else {
                            val specificConfig = configName().value()

                            Configuration.configurations.find {
                                it.configName == specificConfig
                            }?.let {
                                when (action) {
                                    "save" -> it.trySave()
                                    else -> it.tryLoad()
                                }
                            } ?: return@executeWithResult failure("No configuration found with name $specificConfig")
                        }

                        success()
                    }
                }
            }
        }
    }
}