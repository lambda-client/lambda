
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.graphics.outline.OutlineStyle
import java.awt.Color

class OutlineSettings(override val c: Config) : OutlineConfig, ConfigBlock {
	val thicknessSetting by c.setting("Line Width", 25, 1..100, 1, "The width of the outline")
	override val thickness get() = thicknessSetting * 0.00005f

	val glowIntensitySetting by c.setting("Glow Intensity", 50, 0..100, 1, "Intensity of the outline glow")
	override val glowIntensity get() = glowIntensitySetting * 0.01f

	val glowRadiusSetting by c.setting("Glow Radius", 20, 0..100, 1, "Radius of the outline glow")
	override val glowRadius get() = glowRadiusSetting * 0.00005f

	override val fill by c.setting("Fill", true, "Fill the entity silhouette")

	val fillOpacitySetting by c.setting("Fill Opacity", 10, 0..100, 1, "Opacity of the fill") { fill }
	override val fillOpacity get() = fillOpacitySetting * 0.01f

	fun toStyle(color: Color) =
		OutlineStyle(
			color = color,
			thickness = thickness,
			glowIntensity = glowIntensity,
			glowRadius = glowRadius,
			fill = fill,
			fillOpacity = fillOpacity
		)
}
