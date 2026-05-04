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
import com.lambda.config.Group
import com.lambda.config.SettingBlock
import java.awt.Color

class WorldLineSettings(override val c: Config) : SettingBlock, LineConfig {
    companion object {
        private const val COLOR_GROUP = "Color"
        private const val DASH_GROUP = "Dash"
    }

    val distanceScaling by c.setting("Distance Scaling", true, "Line width stays constant on screen regardless of distance")
    val worldWidthSetting by c.setting("Width", 5, 1..50, 1) { !distanceScaling }
    val screenWidthSetting by c.setting("Screen Width", 20, 1..100, 1, "Line width in screen-space (stays constant size)") { distanceScaling }

    override val width: Float get() =
        if (distanceScaling) -screenWidthSetting * 0.00005f
        else worldWidthSetting * 0.001f

    @Group(COLOR_GROUP) override val startColor by c.setting("Start Color", Color.WHITE, "The color at the start of the line")
    @Group(COLOR_GROUP) override val endColor by c.setting("End Color", Color.WHITE, "The color at the end of the line")

    @Group(DASH_GROUP) override val dashEnabled by c.setting("Dashed", false, "Enable dashed line pattern")
    @Group(DASH_GROUP) val dashLengthSetting by c.setting("Dash Length", 50, 1..200, 1, "Length of each dash") { dashEnabled }
    override val dashLength get() = dashLengthSetting * 0.01f
    @Group(DASH_GROUP) val gapLengthSetting by c.setting("Gap Length", 25, 1..200, 1, "Length of gaps between dashes") { dashEnabled }
    override val gapLength get() = gapLengthSetting * 0.01f
    @Group(DASH_GROUP) override val animated by c.setting("Animated", true, "Animate the dash pattern") { dashEnabled }
    @Group(DASH_GROUP) val dashOffsetSetting by c.setting("Dash Offset", 0, 0..100, 1, "Offset of the dash pattern") { dashEnabled && !animated }
    override val dashOffset get() = dashOffsetSetting * 0.01f
    @Group(DASH_GROUP) val animationSpeedSetting by c.setting("Animation Speed", 30, -100..100, 1, "Speed of dash animation (negative = reverse)") { dashEnabled && animated }
    override val animationSpeed get() = animationSpeedSetting * 0.1f
}
