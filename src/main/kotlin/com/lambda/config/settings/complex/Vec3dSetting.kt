/*
 * Copyright 2025 Lambda
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
import com.lambda.brigadier.argument.double
import com.lambda.brigadier.argument.value
import com.lambda.brigadier.execute
import com.lambda.brigadier.required
import com.lambda.config.AbstractSetting
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.util.extension.CommandBuilder
import net.minecraft.command.CommandRegistryAccess
import net.minecraft.util.math.Vec3d

class Vec3dSetting(
    override val name: String,
    defaultValue: Vec3d,
    description: String,
    visibility: () -> Boolean,
) : AbstractSetting<Vec3d>(
    defaultValue,
    TypeToken.get(Vec3d::class.java).type,
    description,
    visibility
) {
    override fun ImGuiBuilder.buildLayout() {
        inputVec3d(name, ::value as Vec3d)
        lambdaTooltip(description)
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
