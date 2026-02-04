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

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.util.NamedEnum
import java.awt.Color

/**
 * SettingGroup for line configuration.
 * Provides individual settings for line colors, width, and dash patterns.
 */
class WorldLineSettings(
    prefix: String,
    c: Configurable,
    vararg baseGroup: NamedEnum,
    override val visibility: () -> Boolean = { true },
) : SettingGroup(c), LineConfig {
    private enum class Group(override val displayName: String) : NamedEnum {
        Color("Color"),
        Dash("Dash")
    }

    val widthSetting by c.setting("${prefix}Line Width", 5, 1..50, 1, "The width of the line", visibility = visibility).group(*baseGroup).index()
    override val width get() = widthSetting * 0.001f

    override val startColor by c.setting("${prefix}Start Color", Color.WHITE, "The color at the start of the line", visibility = visibility).group(*baseGroup, Group.Color).index()
    override val endColor by c.setting("${prefix}End Color", Color.WHITE, "The color at the end of the line", visibility = visibility).group(*baseGroup, Group.Color).index()

    override val dashEnabled by c.setting("${prefix}Dashed", false, "Enable dashed line pattern", visibility = visibility).group(*baseGroup, Group.Dash).index()
    val dashLengthSetting by c.setting("${prefix}Dash Length", 50, 1..200, 1, "Length of each dash") { visibility() && dashEnabled }.group(*baseGroup, Group.Dash).index()
    override val dashLength get() = dashLengthSetting * 0.01f
    val gapLengthSetting by c.setting("${prefix}Gap Length", 25, 1..200, 1, "Length of gaps between dashes") { visibility() && dashEnabled }.group(*baseGroup, Group.Dash).index()
    override val gapLength get() = gapLengthSetting * 0.01f
    override val animated by c.setting("${prefix}Animated", true, "Animate the dash pattern") { visibility() && dashEnabled }.group(*baseGroup, Group.Dash).index()
    val dashOffsetSetting by c.setting("${prefix}Dash Offset", 0, 0..100, 1, "Offset of the dash pattern") { visibility() && dashEnabled && !animated }.group(*baseGroup, Group.Dash).index()
    override val dashOffset get() = dashOffsetSetting * 0.01f
    val animationSpeedSetting by c.setting("${prefix}Animation Speed", 30, -100..100, 1, "Speed of dash animation (negative = reverse)") { visibility() && dashEnabled && animated }.group(*baseGroup, Group.Dash).index()
    override val animationSpeed get() = animationSpeedSetting * 0.1f
}
