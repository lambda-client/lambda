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

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.graphics.outline.GlowPosition
import com.lambda.graphics.outline.OutlineMode
import com.lambda.graphics.outline.OutlineStyle
import com.lambda.graphics.shader.CustomShaderSet
import com.lambda.graphics.shader.CustomShaders
import java.awt.Color

class OutlineSettings(override val c: Config) : OutlineConfig, ConfigBlock {
	override val outlineMode by c.setting(
		"Outline Style",
		OutlineMode.Both,
		"How the outline glow and line are combined."
	)

	override val fillOpacity by c.setting(
		"Fill Opacity",
		0.3f,
		0.0f..1.0f,
		0.01f,
		"Opacity of the filled mask. Set to 0 to disable fill."
	)

	override val lineWidth by c.setting(
		"Line Width",
		1.0f,
		0.5f..5.0f,
		0.05f,
		"Width of the crisp outline."
	) { outlineMode.usesLine() }

	override val lineIntensity by c.setting(
		"Line Intensity",
		1.0f,
		0.0f..4.0f,
		0.05f,
		"Multiplier for the crisp outline."
	) { outlineMode.usesLine() }

	override val glowPosition by c.setting(
		"Glow Position",
		GlowPosition.Outset,
		"Where glow is rendered relative to the mask."
	) { outlineMode.usesGlow() }

	override val glowMultiplier by c.setting(
		"Glow Multiplier",
		1.0f,
		0.0f..10.0f,
		0.05f,
		"Multiplier for the glow effect."
	) { outlineMode.usesGlow() }

	override val glowPasses by c.setting(
		"Glow Passes",
		2,
		1..4,
		1,
		"Number of glow passes applied to the glow."
	) { outlineMode.usesGlow() }

	override val glowOffset by c.setting(
		"Glow Offset",
		4.0f,
		0.0f..15.0f,
		0.05f,
		"Texel offset for each glow pass."
	) { outlineMode.usesGlow() }

	override val glowResolution by c.setting(
		"Glow Resolution",
		1.0f,
		0.125f..1.0f,
		0.025f,
		"Resolution scale used for glow."
	) { outlineMode.usesGlow() }

	override val glowDownsample by c.setting(
		"Glow Downsample",
		1.0f,
		0.25f..1.0f,
		0.05f,
		"Per-pass resolution downsample factor."
	) { outlineMode.usesGlow() }

	override val customShader by c.setting(
		"Custom Shader",
		CustomShaderSet.NONE,
		CustomShaders.OUTLINE::getOptions,
		"Uses a custom outline fragment shader from lambda/shaders/outline."
	)

	fun toStyle(color: Color) =
		OutlineStyle(
			color = color,
			lineWidth = lineWidth,
			glowMultiplier = glowMultiplier,
			glowPasses = glowPasses,
			glowOffset = glowOffset,
			lineIntensity = lineIntensity,
			outlineMode = outlineMode,
			glowPosition = glowPosition,
			fillOpacity = fillOpacity,
			glowResolution = glowResolution,
			glowDownsample = glowDownsample,
			customShader = customShader,
		)
}
