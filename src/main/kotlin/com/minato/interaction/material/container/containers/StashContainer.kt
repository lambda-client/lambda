
package com.minato.interaction.material.container.containers

import com.minato.context.SafeContext
import com.minato.interaction.material.StackSelection
import com.minato.interaction.material.container.MaterialContainer
import com.minato.util.math.roundedBlockPos
import com.minato.util.text.buildText
import com.minato.util.text.highlighted
import com.minato.util.text.literal
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.Box

data class StashContainer(
    val chests: Set<ChestContainer>,
    val pos: Box,
) : MaterialContainer(Rank.Stash) {
    context(_: SafeContext)
    override val slots: List<Slot>
        get() = chests.flatMap { it.slots }
    override var stacks: List<ItemStack>
        get() = chests.flatMap { it.stacks }
        set(_) {}

    override val description = buildText {
        literal("Stash at ")
        highlighted(pos.center.roundedBlockPos.toShortString())
    }

    context(_: SafeContext)
    override fun materialAvailable(selection: StackSelection): Int =
        chests.sumOf {
            it.materialAvailable(selection)
        }
}
