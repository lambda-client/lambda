
package com.minato.interaction.material.container.containers

import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.interaction.handlers.ContainerHandler
import com.minato.interaction.material.container.ExternalContainer
import com.minato.interaction.material.container.MaterialContainer
import com.minato.task.Task
import com.minato.task.TaskGenerator
import com.minato.task.tasks.OpenContainerTask
import com.minato.util.extension.containerSlots
import com.minato.util.text.buildText
import com.minato.util.text.highlighted
import com.minato.util.text.literal
import net.minecraft.block.entity.ChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

data class ChestContainer(
    override var stacks: List<ItemStack>,
    val blockPos: BlockPos,
    val containedInStash: StashContainer? = null
) : MaterialContainer(Rank.Chest), ExternalContainer {
    context(safeContext: SafeContext)
    override val slots
        get(): List<Slot> =
            if (ContainerHandler.lastInteractedBlockEntity is ChestBlockEntity)
                safeContext.player.currentScreenHandler.containerSlots
            else emptyList()

    override val description =
        buildText {
            literal("Chest at ")
            highlighted(blockPos.toShortString())
            containedInStash?.let { stash ->
                literal(" (contained in ")
                highlighted(stash.name)
                literal(")")
            }
        }

    context(automatedSafeContext: AutomatedSafeContext)
    override fun accessThen(exitAfter: Boolean, taskGenerator: TaskGenerator<Unit>): Task<*> =
        OpenContainerTask(blockPos, automatedSafeContext).then {
            taskGenerator.invoke(automatedSafeContext, Unit).finally {
                if (exitAfter) automatedSafeContext.player.closeHandledScreen()
            }
        }
}
