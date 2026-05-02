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

import com.lambda.config.SettingBlock
import java.awt.Color

interface EntityColorsConfig : SettingBlock {
	val useNaturalColors: Boolean
	val playerColor: Color
	val playerDistanceGradient: Boolean
	val playerDistanceColorFar: Color
	val playerDistanceColorClose: Color
	val separateFriendColor: Boolean
	val friendColor: Color
	val mobColor: Color
	val passiveColor: Color
	val vehicleColor: Color
	val projectileColor: Color
	val bossColor: Color
	val decorationColor: Color
	val blockColor: Color
	val miscColor: Color
}