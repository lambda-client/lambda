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

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lambda.Lambda.mc
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.Button
import com.lambda.gui.component.NumberInput
import com.lambda.gui.component.Tooltip
import com.lambda.gui.core.observe
import com.lambda.gui.settings.Labeled
import com.lambda.gui.settings.NUMBER_FIELD_WIDTH
import com.lambda.gui.theme.Look
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.extension.CommandBuilder
import com.lambda.util.world.raycast.RayCastUtils.blockResult
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
	@Composable
	override fun Gui() {
		val pos by observe { value.toImmutable() }
		Labeled(name) {
			NumberInput(
				pos.x,
				String::toIntOrNull,
				{ value = BlockPos(it, pos.y, pos.z) },
				Modifier.weight(1f, fill = false).width(NUMBER_FIELD_WIDTH)
			)
			NumberInput(
				pos.y,
				String::toIntOrNull,
				{ value = BlockPos(pos.x, it, pos.z) },
				Modifier.weight(1f, fill = false).width(NUMBER_FIELD_WIDTH)
			)
			NumberInput(
				pos.z,
				String::toIntOrNull,
				{ value = BlockPos(pos.x, pos.y, it) },
				Modifier.weight(1f, fill = false).width(NUMBER_FIELD_WIDTH)
			)
			Tooltip("Use the block you are looking at") {
				Button("Target", Modifier.height(Look.rowHeight)) {
					mc.crosshairTarget?.blockResult?.blockPos?.let { value = it }
						?: this@BlockPosSetting.info("No block under crosshair")
				}
			}
		}
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