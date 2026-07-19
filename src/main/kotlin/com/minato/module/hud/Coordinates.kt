
package com.minato.module.hud

import com.minato.config.ConfigEditor.editSetting
import com.minato.config.Tab
import com.minato.config.blocks.FormatterSettings
import com.minato.config.withEdits
import com.minato.gui.dsl.ImGuiBuilder
import com.minato.module.HudModule
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.FormattingUtils.format
import com.minato.util.extension.dimensionName
import com.minato.util.extension.isNether
import com.minato.util.math.Vec2d
import com.minato.util.math.netherCoord
import com.minato.util.math.overworldCoord

@Suppress("unused")
object Coordinates : HudModule(
	name = "Coordinates",
	description = "Show your coordinates",
	tag = ModuleTag.HUD,
) {
	private val showDimension by setting("Show Dimension Name", true)
	private val showBiome by setting("Show Biome Name", true)
	private val showCurrentDimensionOnly by setting("Show Current Dimension Only", true)

	private const val CURRENT_DIMENSION_TAB = "Current Dimension"
	@Tab(CURRENT_DIMENSION_TAB) private val formatter by configBlock(FormatterSettings(this))
		.withEdits { ::timeFormat.editSetting { hide() } }

//	private val otherFormatter = FormatterSettings(this, Page.OtherDimension).apply {
//		::timeFormat.edit { hide() }
//		::group.edit { defaultValue(FormatterConfig.TupleGrouping.SquareBrackets) }
//	}

	override fun ImGuiBuilder.buildLayout() {
		runSafe {
			val position = player.pos.format(formatter)
			val otherDimensionPos = // ToDo: The system has forced my hand, too bad!. We need to find a way to allow duplicate setting names.
				if (world.isNether) player.overworldCoord.let { Vec2d(it.x, it.z) }.format(formatter.locale, formatter.separator, "[", "]", formatter.precision)
				else player.netherCoord.let { Vec2d(it.x, it.z) }.format(formatter.locale, formatter.separator, "[", "]", formatter.precision)

			val text =
				if (showCurrentDimensionOnly) position
				else "$position $otherDimensionPos"

			val withDimension =
				if (showDimension) "$text ${world.dimensionName}"
				else text

			val withBiome =
				if (showBiome) "$withDimension in ${beautifyBiome(world.getBiome(player.blockPos).idAsString)}"
				else withDimension
			textCopyable(withBiome)
		}
	}

	fun beautifyBiome(biome: String): String = biome
		.substringAfterLast(':')
		.replace('_', ' ')
		.split(' ')
		.joinToString(" ") { it.replaceFirstChar(Char::uppercaseChar) }
}
