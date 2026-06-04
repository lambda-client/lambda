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
import com.lambda.brigadier.argument.greedyString
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.ConfigLoader
import com.lambda.config.SettingLayer
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching

//ToDo: Make this command add and remove paths when configs are created or removed.
// Brigadier doesn't allow for removing paths by default, so that's something we might have to look into creating our own small system for.
object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg", "settings", "setting"),
    usage = "config <save | load | set | reset> <config> <setting> [value]",
    description = "Save or load configuration files, or set/reset any settings value",
    examples = listOf("config save", "config load", "config set HighwayTools Pavement_Material minecraft:obsidian")
) {
    override fun CommandBuilder.create() {
        required(literal("save")) {
            executeWithResult {
                ConfigLoader.configCategories.forEach { config ->
                    config.trySaveToFile(true)
                }
                this@ConfigCommand.info("Saved ${ConfigLoader.configCategories.size} config files.")
                return@executeWithResult success()
            }
        }
        required(literal("load")) {
            executeWithResult {
                ConfigLoader.configCategories.forEach { config ->
                    config.tryLoadFromFile()
                }
                this@ConfigCommand.info("Loaded ${ConfigLoader.configCategories.size} config files.")
                return@executeWithResult success()
            }
        }
        required(literal("reset")) {
            required(string("config")) { configArg ->
                suggests { _, builder ->
                    suggestMatching(ConfigLoader.configs.map { it.commandName }, builder)
                }
                required(greedyString("setting")) { settingArg ->
                    suggests { context, builder ->
	                    val config = ConfigLoader.configByCommandName(configArg(context).value()) ?: return@suggests null
	                    val suggestions = mutableListOf<String>()
                        config.forEachSetting { path, single ->
                            val settingLit =
                                if (path.isEmpty()) single.setting.name
                                else "${path.joinToString("->") { it.commandName }}->${single.setting.commandName}"
                            suggestions.add(settingLit)
                        }
                        suggestMatching(suggestions, builder)
                    }
                    executeWithResult {
                        val config = ConfigLoader.configByCommandName(configArg().value()) ?: return@executeWithResult failure("Config not found.")
                        var currentMultiple: SettingLayer.Multiple = config.settingLayers
                        val fullSettingPath = settingArg().value().split("->")
                        fullSettingPath.dropLast(1).forEach { path ->
                            currentMultiple = currentMultiple.layers
                                .asSequence()
                                .filterIsInstance<SettingLayer.Multiple>()
                                .find { it.name == path } ?: return@executeWithResult failure("Setting not found.")
                        }
                        val settingLayer = currentMultiple.layers
                            .asSequence()
                            .filterIsInstance<SettingLayer.Single<*, *>>()
                            .find { it.setting.commandName == fullSettingPath.last() } ?: return@executeWithResult failure("Setting not found.")
                        settingLayer.setting.reset()
                        success()
                    }
                }
            }
        }
        required(literal("set")) {
            ConfigLoader.configs.forEach { config ->
                required(literal(config.commandName)) {
                    config.forEachSetting { path, single ->
                        val settingLit =
                            if (path.isEmpty()) single.setting.commandName
                            else "${path.joinToString("->") { it.commandName }}->${single.setting.commandName}"
                        required(literal(settingLit)) {
                            with(single.setting) { buildCommand(registry) }
                        }
                    }
                }
            }
        }
    }
}
