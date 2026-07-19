
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.config.Group
import java.awt.Color

class ScreenLineSettings(override val c: Config) : LineConfig, ConfigBlock {
	companion object {
		private const val COLOR_GROUP = "Color"
		private const val DASH_GROUP = "Dash"
	}

	val widthSetting by c.setting("Width", 20, 1..100, 1, "The width of the line")
	override val width get() = widthSetting * 0.00005f

	@Group(COLOR_GROUP) override val startColor by c.setting("Start Color", Color.WHITE, "The color at the start of the line")
	@Group(COLOR_GROUP) override val endColor by c.setting("End Color", Color.WHITE, "The color at the end of the line")

	@Group(DASH_GROUP) override val dashEnabled by c.setting("Dashed", false, "Enable dashed line pattern")
	@Group(DASH_GROUP) val dashLengthSetting by c.setting("Dash Length", 30, 1..50, 1, "Length of each dash") { dashEnabled }
	override val dashLength get() = dashLengthSetting * 0.001f
	@Group(DASH_GROUP) val gapLengthSetting by c.setting("Gap Length", 15, 1..50, 1, "Length of gaps between dashes") { dashEnabled }
	override val gapLength get() = gapLengthSetting * 0.001f
	@Group(DASH_GROUP) override val animated by c.setting("Animated", true, "Animate the dash pattern") { dashEnabled }
	@Group(DASH_GROUP) val dashOffsetSetting by c.setting("Dash Offset", 0, 0..100, 1, "Offset of the dash pattern") { dashEnabled && !animated }
	override val dashOffset get() = dashOffsetSetting * 0.01f
	@Group(DASH_GROUP) val animationSpeedSetting by c.setting("Animation Speed", 30, -100..100, 1, "Speed of dash animation (negative = reverse)") { dashEnabled && animated }
	override val animationSpeed get() = animationSpeedSetting * 0.1f
}
