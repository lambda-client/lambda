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

    /**
     * Recursively registers path arguments for `reset`.
     * At each depth, a string argument suggests layer names (settings + groups/tabs).
     * If it resolves to a setting, reset it. If it could go deeper, recurse.
     */
    private fun CommandBuilder.buildResetPath(depth: Int) {
        val argName = "path$depth"
        required(string(argName)) {
            suggests { ctx, builder ->
                val layers = resolveLayersAtDepth(ctx, depth)
                layers?.forEach { layer ->
                    when (layer) {
                        is SettingLayer.Single -> builder.suggest(layer.setting.commandName)
                        is SettingLayer.Multiple -> builder.suggest(layer.commandName)
                    }
                }
                builder.buildFuture()
            }
            executeWithResult {
                val setting = resolveSettingAtDepth(this, depth + 1)
                    ?: return@executeWithResult failure("Not a valid setting path.")
                setting.reset()
                return@executeWithResult success()
            }
            if (depth < MaxPathDepth) {
                buildResetPath(depth + 1)
            }
        }
    }

    /**
     * Recursively registers path arguments for `set`.
     * Same tree-walking as reset, but at each depth where the path could resolve
     * to a setting, registers a value argument using JSON-based parsing.
     */
    private fun CommandBuilder.buildSetPath(depth: Int) {
        val argName = "path$depth"
        required(string(argName)) {
            suggests { ctx, builder ->
                val layers = resolveLayersAtDepth(ctx, depth)
                layers?.forEach { layer ->
                    when (layer) {
                        is SettingLayer.Single -> builder.suggest(layer.setting.commandName)
                        is SettingLayer.Multiple -> builder.suggest(layer.commandName)
                    }
                }
                builder.buildFuture()
            }
            // Register value argument — if the path resolves to a setting, set its value
            required(string("value")) { valueArg ->
                executeWithResult {
                    val setting = resolveSettingAtDepth(this, depth + 1)
                        ?: return@executeWithResult failure("Not a valid setting path.")
                    val valueString = valueArg(this).value()
                    val parsed = try {
                        JsonParser.parseString("\"$valueString\"")
                    } catch (_: Exception) {
                        return@executeWithResult failure("$valueString is not a valid value.")
                    }
                    val previous = setting.core.value
                    try {
                        setting.core.loadFromJson(parsed)
                    } catch (_: Exception) {
                        return@executeWithResult failure("Failed to set $valueString for ${setting.name}.")
                    }
                    this@ConfigCommand.info("Set ${setting.name} from $previous to ${setting.core.value}")
                    return@executeWithResult success()
                }
            }
            buildSetPath(depth + 1)
        }
    }

    /**
     * Resolves the [Config] from the "config" argument in the command context.
     */
    private fun resolveConfig(ctx: CommandContext<CommandSource>): Config? {
        val configName = try {
            ctx.getArgument("config", String::class.java)
        } catch (_: Exception) { return null }
        return ConfigLoader.configByCommandName(configName)
    }

    /**
     * Resolves the [SettingLayer] list at the given depth by reading
     * path0..path(depth-1) from the command context and walking the tree.
     */
    private fun resolveLayersAtDepth(ctx: CommandContext<CommandSource>, depth: Int): List<SettingLayer>? {
        val config = resolveConfig(ctx) ?: return null
        var layers: List<SettingLayer> = config.settingLayers
        for (i in 0 until depth) {
            val argValue = try {
                ctx.getArgument("path$i", String::class.java)
            } catch (_: Exception) { return null }
            val container = layers
                .filterIsInstance<SettingLayer.Multiple>()
                .find { it.commandName == argValue }
                ?: return null
            layers = container.layers
        }
        return layers
    }

    /**
     * Resolves a [Setting] by reading config + path0..path(depth-1) from context.
     * The last path argument is expected to be the setting name.
     */
    private fun resolveSettingAtDepth(ctx: CommandContext<CommandSource>, depth: Int): Setting<*, *>? {
        val config = resolveConfig(ctx) ?: return null
        var layers: List<SettingLayer> = config.settingLayers
        for (i in 0 until depth - 1) {
            val argValue = try {
                ctx.getArgument("path$i", String::class.java)
            } catch (_: Exception) { return null }
            val container = layers
                .filterIsInstance<SettingLayer.Multiple>()
                .find { it.commandName == argValue }
                ?: return null
            layers = container.layers
        }
        val settingName = try {
            ctx.getArgument("path${depth - 1}", String::class.java)
        } catch (_: Exception) { return null }
        return layers
            .filterIsInstance<SettingLayer.Single>()
            .find { it.setting.commandName == settingName }
            ?.setting
    }
}
