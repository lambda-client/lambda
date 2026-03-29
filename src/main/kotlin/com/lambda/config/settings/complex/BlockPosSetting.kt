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

import com.google.gson.reflect.TypeToken
import com.lambda.brigadier.argument.integer
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Setting
import com.lambda.config.SettingCore
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.math.BlockPos

/**
 * @see [com.lambda.config.Configurable]
 */
class BlockPosSetting(defaultValue: BlockPos) : SettingCore<BlockPos>(
	defaultValue,
	TypeToken.get(BlockPos::class.java).type
) {
	context(setting: Setting<*, BlockPos>)
    override fun ImGuiBuilder.buildLayout() {
        inputVec3i(setting.name, value) { value = it.blockPos }
        lambdaTooltip(setting.description)
    }

	context(setting: Setting<*, BlockPos>)
    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(integer("X", -30000000, 30000000)) { x ->
            required(integer("Y", -64, 319)) { y ->
                required(integer("Z", -30000000, 30000000)) { z ->
                    execute {
                        setting.trySetValue(BlockPos(x().value(), y().value(), z().value()))
                    }
                }
            }
        }
    }
}
