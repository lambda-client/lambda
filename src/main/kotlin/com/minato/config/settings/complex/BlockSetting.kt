
package com.minato.config.settings.complex

import com.minato.brigadier.argument.blockState
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.extension.CommandBuilder
import net.minecraft.block.Block
import net.minecraft.command.CommandRegistryAccess

class BlockSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<BlockSetting, Block>,
	visibility: () -> Boolean,
	defaultValue: Block
) : Setting<Block>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(blockState(name, registry)) { argument ->
			execute {
				trySetValue(argument().value().blockState.block)
			}
		}
	}
}