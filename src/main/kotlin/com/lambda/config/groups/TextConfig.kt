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
import com.lambda.graphics.mc.RenderBuilder
import java.awt.Color

interface TextConfig : SettingBlock {
	val size: Float
	val textColor: Color

	val outlineEnabled: Boolean
	val outlineColor: Color
	val outlineWidth: Float

	val glowEnabled: Boolean
	val glowColor: Color
	val glowRadius: Float

	val shadowEnabled: Boolean
	val shadowColor: Color
	val shadowOffset: Float
	val shadowAngle: Float
	val shadowSoftness: Float

	fun getSDFStyle(): RenderBuilder.SDFStyle {
		val outline = if (outlineEnabled) RenderBuilder.SDFOutline(outlineColor, outlineWidth) else null
		val glow = if (glowEnabled) RenderBuilder.SDFGlow(glowColor, glowRadius) else null
		val shadow = if (shadowEnabled) RenderBuilder.SDFShadow(shadowColor, shadowOffset, shadowAngle, shadowSoftness) else null

		return RenderBuilder.SDFStyle(textColor, outline, glow, shadow)
	}
}