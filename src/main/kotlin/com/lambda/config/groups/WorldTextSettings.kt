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
import com.lambda.config.SettingGroup
import com.lambda.util.NamedEnum
import java.awt.Color

class WorldTextSettings(
	c: Config,
	vararg baseGroup: NamedEnum,
	prefix: String = "",
	override val visibility: () -> Boolean = { true },
) : SettingGroup(c), TextConfig {
	enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Outline("Outline"),
		Glow("Glow"),
		Shadow("Shadow")
	}

	override val textColor by c.setting("${prefix}Text Color", Color.WHITE, "The main text color", visibility = visibility).group(*baseGroup, Group.General).index()
	val sizeSetting by c.setting("${prefix}Text Size", 5, 1..50, 1, visibility = visibility).group(*baseGroup, Group.General).index()
	override val size get() = sizeSetting * 0.1f

	override val outlineEnabled by c.setting("${prefix}Outline", false, "Enable text outline", visibility = visibility).group(*baseGroup, Group.Outline).index()
	override val outlineColor by c.setting("${prefix}Outline Color", Color.BLACK, "Color of the outline") { visibility() && outlineEnabled }.group(*baseGroup, Group.Outline).index()
	override val outlineWidth by c.setting("${prefix}Outline Width", 0.1f, 0f..0.4f, 0.005f, "Width of the outline") { visibility() && outlineEnabled }.group(*baseGroup, Group.Outline).index()

	override val glowEnabled by c.setting("${prefix}Glow", false, "Enable text glow effect", visibility = visibility).group(*baseGroup, Group.Glow).index()
	override val glowColor by c.setting("${prefix}Glow Color", Color.WHITE, "Color of the glow") { visibility() && glowEnabled }.group(*baseGroup, Group.Glow).index()
	override val glowRadius by c.setting("${prefix}Glow Radius", 0.2f, 0f..0.5f, 0.01f, "Radius of the glow effect") { visibility() && glowEnabled }.group(*baseGroup, Group.Glow).index()

	override val shadowEnabled by c.setting("${prefix}Shadow", true, "Enable text shadow", visibility = visibility).group(*baseGroup, Group.Shadow).index()
	override val shadowColor by c.setting("${prefix}Shadow Color", Color(0, 0, 0, 180), "Color of the shadow") { visibility() && shadowEnabled }.group(*baseGroup, Group.Shadow).index()
	override val shadowOffset by c.setting("${prefix}Shadow Offset", 0.05f, 0f..0.5f, 0.005f, "Distance of shadow from text") { visibility() && shadowEnabled }.group(*baseGroup, Group.Shadow).index()
	override val shadowAngle by c.setting("${prefix}Shadow Angle", 135f, 0f..360f, 1f, "Angle of the shadow") { visibility() && shadowEnabled }.group(*baseGroup, Group.Shadow).index()
	override val shadowSoftness by c.setting("${prefix}Shadow Softness", 0f, 0f..0.5f, 0.01f, "Softness of shadow edges") { visibility() && shadowEnabled }.group(*baseGroup, Group.Shadow).index()
}
