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
import com.lambda.config.SettingBlock
import com.lambda.graphics.outline.OutlineStyle
import com.lambda.util.NamedEnum
import java.awt.Color

class OutlineSettings(override val c: Config) : SettingBlock {
	val thicknessSetting by c.setting("Line Width", 25, 1..100, 1, "The width of the outline")
	val thickness get() = thicknessSetting * 0.00005f

	val glowIntensitySetting by c.setting("Glow Intensity", 50, 0..100, 1, "Intensity of the outline glow")
	val glowIntensity get() = glowIntensitySetting * 0.01f

	val glowRadiusSetting by c.setting("Glow Radius", 20, 0..100, 1, "Radius of the outline glow")
	val glowRadius get() = glowRadiusSetting * 0.00005f

	val fill by c.setting("Fill", true, "Fill the entity silhouette")

	val fillOpacitySetting by c.setting("Fill Opacity", 10, 0..100, 1, "Opacity of the fill") { fill }
	val fillOpacity get() = fillOpacitySetting * 0.01f

	fun toStyle(color: Color) = OutlineStyle(
		color = color,
		thickness = thickness,
		glowIntensity = glowIntensity,
		glowRadius = glowRadius,
		fill = fill,
		fillOpacity = fillOpacity
	)
}
