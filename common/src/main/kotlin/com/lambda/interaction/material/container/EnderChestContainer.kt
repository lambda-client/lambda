package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.task.buildChain
import com.lambda.task.tasks.AcquireMaterial.Companion.acquireStack
import com.lambda.task.tasks.GoalTask.Companion.moveIntoEntityRange
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import net.minecraft.block.Blocks
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.util.math.BlockPos

object EnderChestContainer : MaterialContainer(Rank.ENDER_CHEST) {
    override var stacks = emptyList<ItemStack>()
    private var placePos: BlockPos? = null

    override fun prepare() = buildChain {
        required {
//            findBlock(Blocks.ENDER_CHEST).onSuccess { pos ->
//                moveIntoEntityRange(pos)
//                placePos = pos
//            }.onFailure {
//                acquireStack(Items.ENDER_CHEST.select()).onSuccess { stack ->
//                    placeContainer(stack).onSuccess { pos ->
//                        placePos = pos
//                    }
//                }
//            }
        }
    }

    override fun withdraw(selection: StackSelection) = buildChain {
        placePos?.let { blockPos ->
            openContainer<GenericContainerScreenHandler>(blockPos)
                .onSuccess { screen ->
                    withdraw(screen, selection)
                }
        }
    }

    override fun deposit(selection: StackSelection) = buildChain {
        placePos?.let {
            openContainer<GenericContainerScreenHandler>(it)
                .onSuccess { screen ->
                    withdraw(screen, selection)
                }
        }
    }
}