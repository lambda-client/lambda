
package com.minato.config.blocks

import com.minato.graphics.mc.LineDashStyle
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