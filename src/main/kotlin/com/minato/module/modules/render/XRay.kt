
package com.minato.module.modules.render

import com.minato.Minato.mc
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.settings.collections.CollectionSetting.Companion.onDeselect
import com.minato.config.settings.collections.CollectionSetting.Companion.onSelect
import com.minato.context.SafeContext
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.util.BlockUtils
import net.minecraft.block.Blocks
import net.minecraft.fluid.Fluids
import net.minecraft.registry.Registries

object XRay : Module(
	name = "XRay",
	description = "Allows you to see ores through walls",
	tag = ModuleTag.RENDER,
) {
	val defaultBlocks = setOf(
		Blocks.COAL_ORE, Blocks.DEEPSLATE_COAL_ORE,
		Blocks.IRON_ORE, Blocks.DEEPSLATE_IRON_ORE,
		Blocks.GOLD_ORE, Blocks.DEEPSLATE_GOLD_ORE,
		Blocks.LAPIS_ORE, Blocks.DEEPSLATE_LAPIS_ORE,
		Blocks.REDSTONE_ORE, Blocks.DEEPSLATE_REDSTONE_ORE,
		Blocks.DIAMOND_ORE, Blocks.DEEPSLATE_DIAMOND_ORE,
		Blocks.EMERALD_ORE, Blocks.DEEPSLATE_EMERALD_ORE,
		Blocks.COPPER_ORE, Blocks.DEEPSLATE_COPPER_ORE,
		Blocks.NETHER_GOLD_ORE, Blocks.NETHER_QUARTZ_ORE,
		Blocks.ANCIENT_DEBRIS
	)

	val fluids = BlockUtils.fluids - Fluids.EMPTY

	@JvmStatic val opacity by setting("Opacity", 40, 1..100, 1, "Opacity of the non x-rayed blocks, (automatically overridden as 0 when running Sodium)").onValueChange(::reload)
	@JvmStatic val blockSelection by setting("Block Selection", defaultBlocks, Registries.BLOCK - setOf(Blocks.WATER, Blocks.LAVA), description = "Block selection that will be shown (whitelist) or hidden (blacklist)")
		.onSelect { _ -> reload(null, null, null) }
		.onDeselect { _ -> reload(null, null, null) }
	@JvmStatic val fluidSelection by setting("Fluid Selection", fluids, fluids, description = "Fluids that will be shown when x-raying")
		.onSelect { _ -> reload(null, null, null) }
		.onDeselect { _ -> reload(null, null, null) }

	init {
		onToggle {
			mc.worldRenderer.reload()
		}
	}

	@Suppress("unused")
	fun reload(safeContext: SafeContext?, from: Any?, to: Any?) {
		if (isEnabled) mc.worldRenderer.reload()
	}
}
