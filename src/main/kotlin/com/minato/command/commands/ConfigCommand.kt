
package com.minato.command.commands

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.greedyString
import com.minato.brigadier.argument.literal
import com.minato.brigadier.argument.string
import com.minato.brigadier.argument.value
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.command.MinatoCommand
import com.minato.config.ConfigLoader
import com.minato.config.EntryLayer
import com.minato.config.entries.Setting
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandSource.suggestMatching

//ToDo: Make this command add and remove paths when configs are created or removed.
// Brigadier doesn't allow for removing paths by default, so that's something we might have to look into creating our own small system for.
object ConfigCommand : MinatoCommand(
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
                        config.settingLayers.forEachEntry { path, single ->
                            val settingLit =
                                if (path.isEmpty()) single.entry.name
                                else "${path.joinToString("->") { it.commandName }}->${single.entry.commandName}"
                            suggestions.add(settingLit)
                        }
                        suggestMatching(suggestions, builder)
                    }
                    executeWithResult {
                        val config = ConfigLoader.configByCommandName(configArg().value()) ?: return@executeWithResult failure("Config not found.")
                        var currentMultiple: EntryLayer.Multiple<Setting<*>> = config.settingLayers
                        val fullSettingPath = settingArg().value().split("->")
                        fullSettingPath.dropLast(1).forEach { path ->
                            currentMultiple = currentMultiple.layers
                                .asSequence()
                                .filterIsInstance<EntryLayer.Multiple<Setting<*>>>()
                                .find { it.name == path } ?: return@executeWithResult failure("Setting not found.")
                        }
                        val entryLayer = currentMultiple.layers
                            .asSequence()
                            .filterIsInstance<EntryLayer.Single<Setting<*>>>()
                            .find { it.entry.commandName == fullSettingPath.last() } ?: return@executeWithResult failure("Setting not found.")
                        entryLayer.entry.reset()
                        success()
                    }
                }
            }
        }
        required(literal("set")) {
            ConfigLoader.configs.forEach { config ->
                required(literal(config.commandName)) {
                    config.settingLayers.forEachEntry { path, single ->
                        val settingLit =
                            if (path.isEmpty()) single.entry.commandName
                            else "${path.joinToString("->") { it.commandName }}->${single.entry.commandName}"
                        required(literal(settingLit)) {
                            with(single.entry) { buildCommand(registry) }
                        }
                    }
                }
            }
        }
    }
}
