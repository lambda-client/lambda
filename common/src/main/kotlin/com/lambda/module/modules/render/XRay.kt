package com.lambda.module.modules.render

import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks

object XRay : Module(
    name = "XRay",
    description = "Allows you to see ores through walls",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private val defaultBlocks = setOf(
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

    private val selection by setting("Block Selection", defaultBlocks, "Block selection that will be shown (whitelist) or hidden (blacklist)")
    private val mode by setting("Selection Mode", Selection.WHITELIST, "The mode of the block selection")

    @JvmStatic
    fun isSelected(blockState: BlockState) = mode.select(blockState)

    enum class Selection(val select: (BlockState) -> Boolean) {
        WHITELIST({ it.block in selection }),
        BLACKLIST({ it.block !in selection })
    }

    init {
        onToggle {
            mc.worldRenderer.reload()
        }
    }
}
