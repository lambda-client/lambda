
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.config.Group
import java.awt.Color

class WorldTextSettings(override val c: Config) : TextConfig, ConfigBlock {
	companion object {
		private const val OUTLINE_GROUP = "Outline"
		private const val GLOW_GROUP = "Glow"
		private const val SHADOW_GROUP = "Shadow"
	}

	override val textColor by c.setting("Text Color", Color.WHITE, "The main text color")
	val sizeSetting by c.setting("Text Size", 5, 1..50, 1)
	override val size get() = sizeSetting * 0.1f

	@Group(OUTLINE_GROUP) override val outlineEnabled by c.setting("Outline", false, "Enable text outline")
	@Group(OUTLINE_GROUP) override val outlineColor by c.setting("Outline Color", Color.BLACK, "Color of the outline") { outlineEnabled }
	@Group(OUTLINE_GROUP) override val outlineWidth by c.setting("Outline Width", 0.1f, 0f..0.4f, 0.005f, "Width of the outline") { outlineEnabled }

	@Group(GLOW_GROUP) override val glowEnabled by c.setting("Glow", false, "Enable text glow effect")
	@Group(GLOW_GROUP) override val glowColor by c.setting("Glow Color", Color.WHITE, "Color of the glow") { glowEnabled }
	@Group(GLOW_GROUP) override val glowRadius by c.setting("Glow Radius", 0.2f, 0f..0.5f, 0.01f, "Radius of the glow effect") { glowEnabled }

	@Group(SHADOW_GROUP) override val shadowEnabled by c.setting("Shadow", true, "Enable text shadow")
	@Group(SHADOW_GROUP) override val shadowColor by c.setting("Shadow Color", Color(0, 0, 0, 180), "Color of the shadow") { shadowEnabled }
	@Group(SHADOW_GROUP) override val shadowOffset by c.setting("Shadow Offset", 0.05f, 0f..0.5f, 0.005f, "Distance of shadow from text") { shadowEnabled }
	@Group(SHADOW_GROUP) override val shadowAngle by c.setting("Shadow Angle", 135f, 0f..360f, 1f, "Angle of the shadow") { shadowEnabled }
	@Group(SHADOW_GROUP) override val shadowSoftness by c.setting("Shadow Softness", 0f, 0f..0.5f, 0.01f, "Softness of shadow edges") { shadowEnabled }
}
