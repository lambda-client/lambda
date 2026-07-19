
package com.minato.module.modules.render

import com.minato.config.Tab
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import net.minecraft.world.World

object Weather : Module(
	name = "Weather",
	description = "Modifies the client side weather",
	tag = ModuleTag.RENDER
) {
	private const val OVERWORLD_TAB = "Overworld"
	private const val NETHER_TAB = "Nether"
	private const val END_TAB = "End"

	@Tab(OVERWORLD_TAB) @JvmStatic val overworldMode by setting("Overworld Mode", WeatherMode.Clear)
	@Tab(OVERWORLD_TAB) @JvmStatic val overrideSnow by setting("Override Snow", false) { overworldMode == WeatherMode.Rain }
	@Tab(NETHER_TAB) @JvmStatic val netherMode by setting("Nether Mode", WeatherMode.Clear)
	@Tab(END_TAB) @JvmStatic val endMode by setting("End Mode", WeatherMode.Clear)

	@JvmStatic fun getWeatherMode() =
		runSafe {
			val dimension = world.registryKey
			when (dimension) {
				World.OVERWORLD -> overworldMode
				World.NETHER -> netherMode
				else -> endMode
			}
		} ?: WeatherMode.Clear

	enum class WeatherMode {
		Clear,
		Rain,
		Thunder,
		Snow
	}
}