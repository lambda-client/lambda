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

package com.lambda.module.hud

import com.lambda.config.applyEdits
import com.lambda.config.groups.FormatterSettings
import com.lambda.gui.dsl.ImGuiBuilder
import com.lambda.module.HudModule
import com.lambda.module.hud.Coordinates.formatter
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.FormattingUtils.format
import com.lambda.util.NamedEnum
import com.lambda.util.extension.dimensionName
import com.lambda.util.extension.isNether
import com.lambda.util.math.Vec2d
import com.lambda.util.math.netherCoord
import com.lambda.util.math.overworldCoord

@Suppress("unused")
object Coordinates : HudModule(
	name = "Coordinates",
	description = "Show your coordinates",
	tag = ModuleTag.HUD,
) {
	enum class Group(override val displayName: String) : NamedEnum {
		CurrentDimension("Current Dimension"),
		OtherDimension("Other Dimension"),
	}

	private val showDimension by setting("Show Dimension Name", true)
	private val showBiome by setting("Show Biome Name", true)
	private val showCurrentDimensionOnly by setting("Show Current Dimension Only", true)

	private const val CURRENT_DIMENSION_TAB = "Current Dimension"
	@Tab(CURRENT_DIMENSION_TAB) private val formatter = settingBlock(FormatterSettings(this)) {
		applyEdits {
			::timeFormat.edit { hide() }
		}
	}

	private val formatter = FormatterSettings(c = this, baseGroup = arrayOf(Group.CurrentDimension)).apply {
		applyEdits {
			::timeFormat.edit { hide() }
		}
	}
//	private val otherFormatter = FormatterSettings(this, Page.OtherDimension).apply {
//		::timeFormat.edit { hide() }
//		::group.edit { defaultValue(FormatterConfig.TupleGrouping.SquareBrackets) }
//	}

	override fun ImGuiBuilder.buildLayout() {
		runSafe {
			val position = player.pos.format(formatter)
			val otherDimensionPos = // ToDo: The system has forced my hand, too bad!. We need to find a way to allow duplicate setting names.
				if (world.isNether) player.overworldCoord.let { Vec2d(it.x, it.z) }.format(formatter.locale, formatter.separator, "[", "]", formatter.precision)
				else player.netherCoord.let { Vec2d(it.x, it.z) }.format(formatter.locale, formatter.separator, "[", "]", formatter.precision)

			val text =
				if (showCurrentDimensionOnly) position
				else "$position $otherDimensionPos"

			val withDimension =
				if (showDimension) "$text ${world.dimensionName}"
				else text

			val withBiome =
				if (showBiome) "$withDimension in ${beautifyBiome(world.getBiome(player.blockPos).idAsString)}"
				else withDimension
			textCopyable(withBiome)
		}
	}

	fun beautifyBiome(biome: String): String = biome
		.substringAfterLast(':')
		.replace('_', ' ')
		.split(' ')
		.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
