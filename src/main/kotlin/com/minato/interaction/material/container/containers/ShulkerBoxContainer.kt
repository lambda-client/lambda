
package com.minato.interaction.material.container.containers

import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.interaction.handlers.ContainerHandler
import com.minato.interaction.material.container.ExternalContainer
import com.minato.interaction.material.container.MaterialContainer
import com.minato.task.TaskGenerator
import com.minato.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.minato.task.tasks.OpenContainerTask
import com.minato.task.tasks.PlaceContainerTask
import com.minato.threading.runSafe
import com.minato.util.extension.containerSlots
import com.minato.util.text.buildText
import com.minato.util.text.highlighted
import com.minato.util.text.literal
import net.minecraft.block.entity.ShulkerBoxBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: MaterialContainer,
    val shulkerSlot: Slot,
) : MaterialContainer(Rank.ShulkerBox), ExternalContainer {
    context(safeContext: SafeContext)
    override val slots
        get(): List<Slot> =
            if (ContainerHandler.lastInteractedBlockEntity is ShulkerBoxBlockEntity)
                safeContext.player.currentScreenHandler.containerSlots
            else emptyList()

    override val description =
        buildText {
            highlighted(shulkerSlot.stack.name.string)
            literal(" in ")
            highlighted(containedIn.name)
            literal(" in slot ")
            highlighted("${runSafe { slotInContainer }}")
        }

    context(_: SafeContext)
    private val slotInContainer: Int get() = containedIn.slots.indexOf(shulkerSlot)

    private var placePos = BlockPos.ORIGIN

    context(automatedSafeContext: AutomatedSafeContext)
    override fun accessThen(exitAfter: Boolean, taskGenerator: TaskGenerator<Unit>) =
        PlaceContainerTask(shulkerSlot, automatedSafeContext).then { pos ->
            placePos = pos
            OpenContainerTask(pos, automatedSafeContext).then {
                taskGenerator.invoke(automatedSafeContext, Unit).thenOrNull {
                    if (exitAfter) automatedSafeContext.breakAndCollectBlock(placePos)
                    else null
                }
            }
        }
}