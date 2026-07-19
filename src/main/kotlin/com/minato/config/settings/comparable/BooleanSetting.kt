
package com.minato.config.settings.comparable

import com.minato.brigadier.argument.boolean
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess

class BooleanSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<BooleanSetting, Boolean>,
	visibility: () -> Boolean,
	defaultValue: Boolean
) : Setting<Boolean>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {
		checkbox(name, ::value)
		minatoTooltip(description)
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(boolean(name)) { parameter ->
			execute {
				trySetValue(parameter().value())
			}
		}
	}
}