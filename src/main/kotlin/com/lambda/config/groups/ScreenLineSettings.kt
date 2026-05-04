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

package com.lambda.config.groups

import com.lambda.config.Config
import com.lambda.config.Config.Group
import com.lambda.config.Group
import com.lambda.config.SettingBlock
import java.awt.Color

class ScreenLineSettings(override val c: Config) : SettingBlock, LineConfig {
	companion object {
		private const val GROUP_COLOR = "Color"
		private const val GROUP_DASH = "Dash"
	}

	val widthSetting by c.setting("Width", 20, 1..100, 1, "The width of the line")
	override val width get() = widthSetting * 0.00005f

	@Group(GROUP_COLOR) override val startColor by c.setting("Start Color", Color.WHITE, "The color at the start of the line")
	@Group(GROUP_COLOR) override val endColor by c.setting("End Color", Color.WHITE, "The color at the end of the line")

	@Group(GROUP_DASH) override val dashEnabled by c.setting("Dashed", false, "Enable dashed line pattern")
	@Group(GROUP_DASH) val dashLengthSetting by c.setting("Dash Length", 30, 1..50, 1, "Length of each dash") { dashEnabled }
	override val dashLength get() = dashLengthSetting * 0.001f
	@Group(GROUP_DASH) val gapLengthSetting by c.setting("Gap Length", 15, 1..50, 1, "Length of gaps between dashes") { dashEnabled }
	override val gapLength get() = gapLengthSetting * 0.001f
	@Group(GROUP_DASH) override val animated by c.setting("Animated", true, "Animate the dash pattern") { dashEnabled }
	@Group(GROUP_DASH) val dashOffsetSetting by c.setting("Dash Offset", 0, 0..100, 1, "Offset of the dash pattern") { dashEnabled && !animated }
	override val dashOffset get() = dashOffsetSetting * 0.01f
	@Group(GROUP_DASH) val animationSpeedSetting by c.setting("Animation Speed", 30, -100..100, 1, "Speed of dash animation (negative = reverse)") { dashEnabled && animated }
	override val animationSpeed get() = animationSpeedSetting * 0.1f
}
