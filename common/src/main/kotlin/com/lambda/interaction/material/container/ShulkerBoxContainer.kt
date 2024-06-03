package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.tasks.BuildStructure.Companion.breakAndCollectBlock
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.task.tasks.PlaceContainer.Companion.placeContainer
import com.lambda.util.Communication.info
import net.minecraft.item.ItemStack
import net.minecraft.screen.ShulkerBoxScreenHandler
import net.minecraft.util.math.BlockPos

data class ShulkerBoxContainer(
    override var stacks: List<ItemStack>,
    val containedIn: MaterialContainer,
    val shulkerStack: ItemStack,
) : MaterialContainer(Rank.SHULKER_BOX) {
    private var openScreen: ShulkerBoxScreenHandler? = null
    private var placePosition: BlockPos? = null

    override val name = "${shulkerStack.name.string} in slot $slotInContainer in ${containedIn.name}"

    private val slotInContainer: Int get() = containedIn.stacks.indexOf(shulkerStack)

    override fun prepare() =
        placeContainer(shulkerStack).onSuccess { place, placePos ->
            placePosition = placePos
            openContainer<ShulkerBoxScreenHandler>(placePos).onSuccess { _, screen ->
                openScreen = screen
            }.start(place)
        }

    override fun withdraw(selection: StackSelection): Task<*> {
        val open = openScreen ?: return Task.emptyTask()
        val place = placePosition ?: return Task.emptyTask()
        info("Withdrawing $selection from ${shulkerStack.name.string}")
        return withdraw(open, selection).onSuccess { withdraw, _ ->
            breakAndCollectBlock(place).start(withdraw)
        }
    }

    override fun deposit(selection: StackSelection): Task<*> {
        val open = openScreen ?: return Task.emptyTask()
        val place = placePosition ?: return Task.emptyTask()
        info("Depositing $selection to ${shulkerStack.name.string}")
        return deposit(open, selection).onSuccess { deposit, _ ->
            breakAndCollectBlock(place).start(deposit)
        }
    }
}