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

import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.Config
import com.lambda.config.entries.Setting
import com.lambda.config.entries.SettingEntryLayer
import com.lambda.gui.component.NumberInput
import com.lambda.gui.core.observe
import com.lambda.gui.settings.Labeled
import com.lambda.gui.settings.NumberFieldWidth
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.math.Vec3d

class Vec3dSetting(
    name: String,
    description: String,
    config: Config,
    layer: SettingEntryLayer<Vec3dSetting, Vec3d>,
    visibility: () -> Boolean,
    defaultValue: Vec3d
) : Setting<Vec3d>(name, description, defaultValue, layer, config, visibility) {
    /** A field for each coordinate */
    @Composable
    override fun compose() {
        val vec by observe { value }
        Labeled(name) {
            NumberInput(vec.x, { value = Vec3d(it, vec.y, vec.z) }, Modifier.width(NumberFieldWidth))
            NumberInput(vec.y, { value = Vec3d(vec.x, it, vec.z) }, Modifier.width(NumberFieldWidth))
            NumberInput(vec.z, { value = Vec3d(vec.x, vec.y, it) }, Modifier.width(NumberFieldWidth))
        }
    }

    override fun CommandBuilder.buildCommand(registry: CommandRegistryAccess) {
        required(double("X", -30000000.0, 30000000.0)) { x ->
            required(double("Y", -64.0, 255.0)) { y ->
                required(double("Z", -30000000.0, 30000000.0)) { z ->
                    execute {
                        trySetValue(Vec3d(x().value(), y().value(), z().value()))
                    }
                }
            }
        }
    }
}