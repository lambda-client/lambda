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

package com.lambda.config.blocks

import com.lambda.graphics.mc.LineDashStyle
import java.awt.Color

interface LineConfig {
	val startColor: Color
	val endColor: Color
	val width: Float
	val dashEnabled: Boolean
	val dashLength: Float
	val gapLength: Float
	val dashOffset: Float
	val animated: Boolean
	val animationSpeed: Float

	fun getDashStyle(): LineDashStyle? =
		if (dashEnabled) LineDashStyle(dashLength, gapLength, dashOffset, animated, animationSpeed) else null
}