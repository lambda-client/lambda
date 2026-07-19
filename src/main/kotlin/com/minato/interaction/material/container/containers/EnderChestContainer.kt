
package com.minato.interaction.material.container.containers

import com.minato.context.AutomatedSafeContext
import com.minato.context.SafeContext
import com.minato.interaction.handlers.ContainerHandler
import com.minato.interaction.handlers.ContainerHandler.findSlotsWithMaterial
import com.minato.interaction.material.StackSelection.Companion.select
import com.minato.interaction.material.container.ExternalContainer
import com.minato.interaction.material.container.MaterialContainer
import com.minato.task.TaskGenerator
import com.minato.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.minato.task.tasks.OpenContainerTask
import com.minato.task.tasks.PlaceContainerTask
import com.minato.util.extension.containerSlots
import com.minato.util.text.buildText
import com.minato.util.text.literal
import net.minecraft.block.entity.EnderChestBlockEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items

object EnderChestContainer : MaterialContainer(Rank.EnderChest), ExternalContainer {
	context(safeContext: SafeContext)
	override val slots
		get() =
			if (ContainerHandler.lastInteractedBlockEntity is EnderChestBlockEntity)
				safeContext.player.currentScreenHandler.containerSlots
			else emptyList()
	override var stacks = emptyList<ItemStack>()

	override val description = buildText { literal("Ender Chest") }

	context(automatedSafeContext: AutomatedSafeContext)
	override fun accessThen(exitAfter: Boolean, taskGenerator: TaskGenerator<Unit>) =
		Items.ENDER_CHEST
			.select()
			.findSlotsWithMaterial()
			.firstOrNull()?.let { slot ->
				PlaceContainerTask(slot, automatedSafeContext).then { pos ->
					OpenContainerTask(pos, automatedSafeContext).then {
						taskGenerator.invoke(automatedSafeContext, Unit).thenOrNull {
							if (exitAfter) automatedSafeContext.breakAndCollectBlock(pos, lifeMaintenance = false)
							else null
						}
					}
				}
			}
}
