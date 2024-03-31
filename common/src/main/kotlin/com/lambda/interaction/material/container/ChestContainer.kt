package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.buildChain
import com.lambda.task.tasks.GoalTask.Companion.moveIntoEntityRange
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.threading.runGameBlocking
import com.lambda.util.Communication.info
import net.minecraft.block.ChestBlock
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.screen.ScreenHandler
import net.minecraft.util.math.BlockPos

data class ChestContainer(
    override var stacks: List<ItemStack>,
    val blockPos: BlockPos,
) : MaterialContainer(Rank.CHEST) {
    override fun prepare() = buildChain {
        moveIntoEntityRange(blockPos)
        required {
            runGameBlocking {
                if (ChestBlock.isChestBlocked(world, blockPos)) {
                    throw ChestBlockedException()
                }
            }
        }
    }

    override fun withdraw(selection: StackSelection) = buildChain {
        openContainer<GenericContainerScreenHandler>(blockPos)
            .withMaxAttempts(3)
            .withTimeout(1000L)
            .onSuccess { screen ->
                info("Withdrawing $selection from ${screen.type}")
                withdraw(screen, selection)
            }
    }

    override fun deposit(selection: StackSelection) = buildChain {
        openContainer<GenericContainerScreenHandler>(blockPos)
            .withMaxAttempts(3)
            .withTimeout(1000L)
            .onSuccess { screen ->
                info("Depositing $selection to ${screen.type}")
                deposit(screen, selection)
            }
    }

    class ChestBlockedException: Exception("The chest is blocked by another block or a cat")
    class UnexpectedScreen(screenHandler: ScreenHandler): Exception("Unexpected screen. Got ${screenHandler.type}")
}