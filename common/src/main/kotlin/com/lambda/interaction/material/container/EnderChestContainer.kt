package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.Task.Companion.failTask
import com.lambda.task.tasks.InventoryTask.Companion.deposit
import com.lambda.task.tasks.InventoryTask.Companion.withdraw
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import net.minecraft.item.ItemStack
import net.minecraft.screen.GenericContainerScreenHandler
import net.minecraft.util.math.BlockPos

object EnderChestContainer : MaterialContainer(Rank.ENDER_CHEST) {
    override var stacks = emptyList<ItemStack>()
    override val name = "EnderChest"
    private var placePos: BlockPos? = null

    override fun prepare(): Task<*> {
        TODO("Not yet implemented")
    }
//        findBlock(Blocks.ENDER_CHEST).onSuccess { pos ->
//            moveIntoEntityRange(pos)
//            placePos = pos
//        }.onFailure {
//            acquireStack(Items.ENDER_CHEST.select()).onSuccess { _, stack ->
//                placeContainer(stack).onSuccess { _, pos ->
//                    placePos = pos
//                }
//            }
//        }

    override fun withdraw(selection: StackSelection): Task<*> {
        val pos = placePos ?: return failTask("No placePos found for EnderChestContainer")
        return openContainer<GenericContainerScreenHandler>(pos)
            .onSuccess { _, screen ->
                withdraw(screen, selection)
            }
    }

    override fun deposit(selection: StackSelection): Task<*> {
        val pos = placePos ?: return failTask("No placePos found for EnderChestContainer")
        return openContainer<GenericContainerScreenHandler>(pos)
            .onSuccess { _, screen ->
                deposit(screen, selection)
            }
    }
}