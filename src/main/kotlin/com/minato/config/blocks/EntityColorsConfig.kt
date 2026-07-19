
package com.minato.config.blocks

import java.awt.Color

interface EntityColorsConfig {
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