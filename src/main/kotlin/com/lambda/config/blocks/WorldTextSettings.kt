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
import com.lambda.config.Group
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.complex.colorSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.config.settings.numeric.intSetting
import java.awt.Color

class WorldTextSettings(override val c: Config) : TextConfig, ConfigBlock {
	companion object {
		private const val OUTLINE_GROUP = "Outline"
		private const val GLOW_GROUP = "Glow"
		private const val SHADOW_GROUP = "Shadow"
	}

	override val textColor by c.colorSetting("Text Color", Color.WHITE, "The main text color")
	val sizeSetting by c.intSetting("Text Size", 5, 1..50, 1)
	override val size get() = sizeSetting * 0.1f

	@Group(OUTLINE_GROUP) override val outlineEnabled by c.boolSetting("Outline", false, "Enable text outline")
	@Group(OUTLINE_GROUP) override val outlineColor by c.colorSetting("Outline Color", Color.BLACK, "Color of the outline") { outlineEnabled }
	@Group(OUTLINE_GROUP) override val outlineWidth by c.floatSetting("Outline Width", 0.1f, 0f..0.4f, 0.005f, "Width of the outline") { outlineEnabled }

	@Group(GLOW_GROUP) override val glowEnabled by c.boolSetting("Glow", false, "Enable text glow effect")
	@Group(GLOW_GROUP) override val glowColor by c.colorSetting("Glow Color", Color.WHITE, "Color of the glow") { glowEnabled }
	@Group(GLOW_GROUP) override val glowRadius by c.floatSetting("Glow Radius", 0.2f, 0f..0.5f, 0.01f, "Radius of the glow effect") { glowEnabled }

	@Group(SHADOW_GROUP) override val shadowEnabled by c.boolSetting("Shadow", true, "Enable text shadow")
	@Group(SHADOW_GROUP) override val shadowColor by c.colorSetting("Shadow Color", Color(0, 0, 0, 180), "Color of the shadow") { shadowEnabled }
	@Group(SHADOW_GROUP) override val shadowOffset by c.floatSetting("Shadow Offset", 0.05f, 0f..0.5f, 0.005f, "Distance of shadow from text") { shadowEnabled }
	@Group(SHADOW_GROUP) override val shadowAngle by c.floatSetting("Shadow Angle", 135f, 0f..360f, 1f, "Angle of the shadow") { shadowEnabled }
	@Group(SHADOW_GROUP) override val shadowSoftness by c.floatSetting("Shadow Softness", 0f, 0f..0.5f, 0.01f, "Softness of shadow edges") { shadowEnabled }
}
