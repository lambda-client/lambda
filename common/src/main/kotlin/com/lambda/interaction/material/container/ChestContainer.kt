package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.tasks.GoalTask.Companion.moveIntoEntityRange
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
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
    override fun prepare() =
        moveIntoEntityRange(blockPos).onSuccess { _, _ ->
//            when {
//                ChestBlock.hasBlockOnTop(world, blockPos) -> breakBlock(blockPos.up())
//                ChestBlock.hasCatOnTop(world, blockPos) -> kill(cat)
//            }
            if (ChestBlock.isChestBlocked(world, blockPos)) {
                throw ChestBlockedException()
            }
        }

    override fun withdraw(selection: StackSelection) =
        openContainer<GenericContainerScreenHandler>(blockPos)
            .withMaxAttempts(3)
            .withTimeout(20)
            .onSuccess { open, screen ->
                info("Withdrawing $selection from ${screen.type}")
                withdraw(screen, selection).start(open)
            }

    override fun deposit(selection: StackSelection) =
        openContainer<GenericContainerScreenHandler>(blockPos)
            .withMaxAttempts(3)
            .withTimeout(20)
            .onSuccess { open, screen ->
                info("Depositing $selection to ${screen.type}")
                deposit(screen, selection).start(open)
            }

    class ChestBlockedException: Exception("The chest is blocked by another block or a cat")
    class UnexpectedScreen(screenHandler: ScreenHandler): Exception("Unexpected screen. Got ${screenHandler.type}")
}