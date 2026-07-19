
package com.minato.config.blocks

import com.minato.graphics.mc.RenderBuilder
import java.awt.Color

interface TextConfig {
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