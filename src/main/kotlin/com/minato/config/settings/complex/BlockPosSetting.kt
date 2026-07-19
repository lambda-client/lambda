
package com.minato.config.settings.complex

import com.minato.Minato.mc
import com.minato.brigadier.argument.integer
import com.minato.brigadier.argument.value
import com.minato.brigadier.execute
import com.minato.brigadier.required
import com.minato.config.Config
import com.minato.config.entries.Setting
import com.minato.config.entries.SettingEntryLayer
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.util.BlockUtils.blockPos
import com.minato.util.CommunicationUtils.info
import com.minato.util.extension.CommandBuilder
import com.minato.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.math.BlockPos

class BlockPosSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingEntryLayer<BlockPosSetting, BlockPos>,
	visibility: () -> Boolean,
	defaultValue: BlockPos
) : Setting<BlockPos>(name, description, defaultValue, layer, config, visibility) {
	override fun ImGuiBuilder.buildLayout() {
		button("Set##$name") {
			mc.crosshairTarget?.blockResult?.blockPos?.let {
				value = it
			} ?: info("No block under crosshair")
		}
		minatoTooltip("Set the coordinates to the block you are currently looking at")
		sameLine()
		treeNode(name, id = name) {
			inputVec3i("##$name", value) { value = it.blockPos }
		}
		minatoTooltip(description)
	}

	override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
		required(integer("X", -30000000, 30000000)) { x ->
			required(integer("Y", -64, 319)) { y ->
				required(integer("Z", -30000000, 30000000)) { z ->
					execute {
						trySetValue(BlockPos(x().value(), y().value(), z().value()))
					}
				}
			}
		}
	}
}