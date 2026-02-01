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

package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import net.minecraft.world.World

object Weather : Module(
	name = "Weather",
	description = "Modifies the client side weather",
	tag = ModuleTag.RENDER
) {
	private enum class Group(override val displayName: String) : NamedEnum {
		Overworld("Overworld"),
		Nether("Nether"),
		End("End")
	}

	@JvmStatic
	val overworldMode by setting("Overworld Mode", WeatherMode.Clear).group(Group.Overworld)
	@JvmStatic
	val overrideSnow by setting("Override Snow", false) { overworldMode == WeatherMode.Rain }.group(Group.Overworld)
	@JvmStatic
	val netherMode by setting("Nether Mode", WeatherMode.Clear).group(Group.Nether)
	@JvmStatic
	val endMode by setting("End Mode", WeatherMode.Clear).group(Group.End)

	@JvmStatic
	fun getWeatherMode() =
		runSafe {
			val dimension = world.registryKey
			when (dimension) {
				World.OVERWORLD -> overworldMode
				World.NETHER -> netherMode
				else -> endMode
			}
		} ?: WeatherMode.Clear

	enum class WeatherMode {
		Clear,
		Rain,
		Thunder,
		Snow
	}
}