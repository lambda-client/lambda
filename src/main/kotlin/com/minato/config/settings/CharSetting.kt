
package com.minato.config.settings

import com.minato.brigadier.CommandResult.Companion.failure
import com.minato.brigadier.CommandResult.Companion.success
import com.minato.brigadier.argument.value
import com.minato.brigadier.argument.word
import com.minato.brigadier.executeWithResult
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

/**
 * @see [com.minato.config.Config]
 */
class CharSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<CharSetting, Char>,
	defaultValue: Char,
	visibility: () -> Boolean
) : Setting<Char>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(word(name)) { parameter ->
			executeWithResult {
				val char = parameter().value().firstOrNull() ?: return@executeWithResult failure("Can't parse char type")
				trySetValue(char)
				return@executeWithResult success()
			}
		}
	}
}