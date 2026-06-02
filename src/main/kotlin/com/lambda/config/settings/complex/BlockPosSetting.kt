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

package com.lambda.config.settings.complex

import com.lambda.Lambda.mc
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.Config.SettingLayer
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.math.BlockPos

class BlockPosSetting(
	name: String,
	description: String,
	config: Config,
	layer: SettingLayer.Single<*, BlockPos>,
	visibility: () -> Boolean,
	defaultValue: BlockPos
) : Setting<BlockPos>(name, description, SettingCore(defaultValue), config, layer, visibility) {
	override fun ImGuiBuilder.buildLayout() {
		button("Set##$name") {
			mc.crosshairTarget?.blockResult?.blockPos?.let {
				value = it
			} ?: info("No block under crosshair")
		}
		lambdaTooltip("Set the coordinates to the block you are currently looking at")
		sameLine()
		treeNode(name, id = name) {
			inputVec3i("##$name", value) { value = it.blockPos }
		}
		lambdaTooltip(description)
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