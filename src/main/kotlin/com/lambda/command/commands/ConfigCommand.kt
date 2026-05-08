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

import com.google.gson.JsonParser
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.Config
import com.lambda.config.Config.SettingLayer
import com.lambda.config.ConfigLoader
import com.lambda.config.Setting
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import com.mojang.brigadier.context.CommandContext
import net.minecraft.command.CommandSource
import net.minecraft.command.CommandSource.suggestMatching

object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg", "settings", "setting"),
    usage = "config <save | load | set | reset> <config> [group/tab...] <setting> [value]",
    description = "Save or load configuration files, or set/reset any settings value",
    examples = listOf("config save", "config load", "config set HighwayTools Pavement_Material minecraft:obsidian")
) {
    private const val MaxPathDepth = 6

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
            required(string("config")) { configArg ->
                suggests { _, builder ->
                    suggestMatching(ConfigLoader.configs.map { it.commandName }, builder)
                }
                buildResetPath(0)
            }
        }
        required(literal("set")) {
            required(string("config")) { configArg ->
                suggests { _, builder ->
                    suggestMatching(ConfigLoader.configs.map { it.commandName }, builder)
                }
                buildSetPath(0)
            }
        }
    }
}
