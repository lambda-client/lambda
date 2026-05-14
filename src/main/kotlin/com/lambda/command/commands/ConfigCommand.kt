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

import com.lambda.brigadier.CommandResult
import com.lambda.brigadier.CommandResult.Companion.failure
import com.lambda.brigadier.CommandResult.Companion.success
import com.lambda.brigadier.argument.literal
import com.lambda.brigadier.argument.string
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.executeWithResult
import com.lambda.brigadier.required
import com.lambda.command.LambdaCommand
import com.lambda.config.Config.SettingLayer
import com.lambda.config.ConfigLoader
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import com.mojang.brigadier.builder.ArgumentBuilder
import net.minecraft.command.CommandSource.suggestMatching

object ConfigCommand : LambdaCommand(
    name = "config",
    aliases = setOf("cfg", "settings", "setting"),
    usage = "config <save | load | set | reset> <config> [group/tab...] <setting> [value]",
    description = "Save or load configuration files, or set/reset any settings value",
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
            settingArgument { layer ->
                layer.setting.reset()
                success()
            }
        }
        required(literal("set")) {
            settingArgument { layer ->
                with(layer.setting) { buildCommand(registry) }
                success()
            }
        }
    }

    private fun <S, B : ArgumentBuilder<S, *>> B.settingArgument(block: B.(SettingLayer.Single<*, *>) -> CommandResult) {
        required(string("config")) { configArg ->
            suggests { _, builder ->
                suggestMatching(ConfigLoader.configs.map { it.commandName }, builder)
            }
            executeWithResult {
                val configString = configArg().value()
                val config = ConfigLoader.configByCommandName(configString) ?: return@executeWithResult failure("Config not found")
                config.reset()
                success()
            }
            required(string("setting")) { settingArg ->
                suggests { context, builder ->
                    val configString = configArg(context).value()
                    val config = ConfigLoader.configByCommandName(configString) ?: return@suggests null
                    val suggestions = mutableListOf<String>()
                    fun forEach(layer: SettingLayer.Multiple, layerPath: List<String>) {
                        layer.layers.forEach { layer ->
                            when (layer) {
                                is SettingLayer.Single<*, *> -> suggestions.add("${layerPath.joinToString("->")}->${layer.setting.commandName}")
                                is SettingLayer.Multiple -> forEach(layer, layerPath + layer.name)
                            }
                        }
                    }

                    forEach(config.settingLayers, emptyList())
                    suggestMatching(suggestions, builder)
                }
                executeWithResult {
                    val configString = configArg().value()
                    val config = ConfigLoader.configByCommandName(configString) ?: return@executeWithResult failure("Config not found")
                    val settingString = settingArg().value()
                    val fullPath = settingString.split("->")
                    val settingName = fullPath.last()
                    var currentLayer: SettingLayer.Multiple = config.settingLayers
                    fullPath.forEachIndexed { index, layerName ->
                        if (index == fullPath.size - 1) return@forEachIndexed
                        val layer = currentLayer.layers
                            .asSequence()
                            .filterIsInstance<SettingLayer.Multiple>()
                            .find { it.name == layerName }
                        if (layer == null) return@executeWithResult failure("Config layer not found: $layerName")
                        currentLayer = layer
                    }

                    val settingLayer = currentLayer.layers
                        .asSequence()
                        .filterIsInstance<SettingLayer.Single<*, *>>()
                        .find { it.setting.commandName == settingName }
                    if (settingLayer == null) return@executeWithResult failure("Setting not found: $settingName")
                    return@executeWithResult block(settingLayer)
                }
            }
        }
    }
}
