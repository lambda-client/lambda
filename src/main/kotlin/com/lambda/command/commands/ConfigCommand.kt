/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.command.commands

import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.ConfigLoader
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching

object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg", "settings", "setting"),
    usage = "config <save | load | set> <config> <setting> <value>",
    description = "Save or load configuration files, or set any settings value",
    examples = listOf("config save", "config load", "config set HighwayTools Pavement_Material minecraft:obsidian")
) {
    override fun CommandBuilder.create() {
        required(literal("save")) {
            executeWithResult {
                ConfigLoader.configCategories.forEach { config ->
                    config.trySaveToFile(true)
                }
                this@ConfigCommand.info("Saved ${ConfigLoader.configCategories.size} configuration files.")
                return@executeWithResult success()
            }
        }
        required(literal("load")) {
            executeWithResult {
                ConfigLoader.configCategories.forEach { config ->
                    config.tryLoadFromFile()
                }
                this@ConfigCommand.info("Loaded ${ConfigLoader.configCategories.size} configuration files.")
                return@executeWithResult success()
            }
        }
        required(literal("reset")) {
            required(string("config")) { config ->
                suggests { _, builder ->
                    suggestMatching(ConfigLoader.configs.map { it.commandName }, builder)
                }
                required(string("setting")) { setting ->
                    suggests { ctx, builder ->
                        val conf = config(ctx).value()
                        ConfigLoader.configByName(conf)?.let { config ->
                            suggestMatching(config.settingContainers.map { it.commandName }, builder)
                        } ?: builder.buildFuture()
                    }
                    executeWithResult {
                        val confName = this.config().value()
                        val settingName = setting().value()
                        val config = ConfigLoader.configByCommandName(confName)
                            ?: return@executeWithResult failure("$confName is not a valid config.")
                        val setting = ConfigLoader.settingByCommandName(config, settingName)
                            ?: return@executeWithResult failure("$settingName is not a valid setting for $confName.")
                        setting.reset()
                        return@executeWithResult success()
                    }
                }
            }
        }
        required(literal("set")) {
            ConfigLoader.configs.forEach { config ->
                required(literal(config.commandName)) {
                    config.settingContainers.forEach { setting ->
                        required(literal(setting.commandName)) {
                            with(setting) {
                                buildCommand(registry)
                            }
                        }
                    }
                }
            }
        }
    }
}
