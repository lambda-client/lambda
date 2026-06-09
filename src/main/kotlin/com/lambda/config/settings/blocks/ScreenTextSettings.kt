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

package com.lambda.config.settings.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import java.awt.Color

class ScreenTextSettings(override val c: Config) : TextConfig, ConfigBlock {
    companion object {
        private const val OutlineGroup = "Outline"
        private const val GlowGroup = "Glow"
        private const val ShadowGroup = "Shadow"
    }

    override val textColor by c.setting("Text Color", Color.WHITE, "The main text color")
    val sizeSetting by c.setting("Text Size", 18, 1..50, 1)
    override val size get() = sizeSetting * 0.001f

    @Group(OutlineGroup) override val outlineEnabled by c.setting("Outline", false, "Enable text outline")
    @Group(OutlineGroup) override val outlineColor by c.setting("Outline Color", Color.BLACK, "Color of the outline") { outlineEnabled }
    @Group(OutlineGroup) override val outlineWidth by c.setting("Outline Width", 0.1f, 0f..0.4f, 0.005f, "Width of the outline") { outlineEnabled }

    @Group(GlowGroup) override val glowEnabled by c.setting("Glow", false, "Enable text glow effect")
    @Group(GlowGroup) override val glowColor by c.setting("Glow Color", Color.WHITE, "Color of the glow") { glowEnabled }
    @Group(GlowGroup) override val glowRadius by c.setting("Glow Radius", 0.2f, 0f..0.5f, 0.01f, "Radius of the glow effect") { glowEnabled }

    @Group(ShadowGroup) override val shadowEnabled by c.setting("Shadow", true, "Enable text shadow")
    @Group(ShadowGroup) override val shadowColor by c.setting("Shadow Color", Color(0, 0, 0, 180), "Color of the shadow") { shadowEnabled }
    @Group(ShadowGroup) override val shadowOffset by c.setting("Shadow Offset", 0.05f, 0f..0.5f, 0.005f, "Distance of shadow from text") { shadowEnabled }
    @Group(ShadowGroup) override val shadowAngle by c.setting("Shadow Angle", 135f, 0f..360f, 1f, "Angle of the shadow") { shadowEnabled }
    @Group(ShadowGroup) override val shadowSoftness by c.setting("Shadow Softness", 0f, 0f..0.5f, 0.01f, "Softness of shadow edges") { shadowEnabled }
}
