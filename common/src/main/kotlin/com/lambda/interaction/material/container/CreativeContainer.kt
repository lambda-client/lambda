package com.lambda.interaction.material.container

import com.lambda.interaction.material.MaterialContainer
import com.lambda.module.modules.client.TaskFlow
import com.lambda.interaction.material.StackSelection
import com.lambda.task.buildTask
import com.lambda.threading.runGameBlocking
import com.lambda.util.item.ItemStackUtils.equal
import kotlinx.coroutines.delay
import net.minecraft.item.ItemStack

data object CreativeContainer : MaterialContainer(Rank.CREATIVE) {
    override var stacks = emptyList<ItemStack>()

    override fun available(selection: StackSelection): Int =
        if (selection.optimalStack != null) Int.MAX_VALUE else 0

    override fun deposit(selection: StackSelection) = buildTask {
        runGameBlocking {
            if (!player.isCreative) {
                // ToDo: Maybe switch gamemode?
                throw NotInCreativeModeException()
            }

            interaction.clickCreativeStack(
                ItemStack.EMPTY,
                36 + player.inventory.selectedSlot
            )
        }
    }

    // Withdraws items from the creative menu to the player's main hand
    override fun withdraw(selection: StackSelection) = buildTask {
        selection.optimalStack?.let { optimalStack ->
            runGameBlocking {
                if (player.mainHandStack.equal(optimalStack)) {
                    return@runGameBlocking
                }

                if (!player.isCreative) {
                    // ToDo: Maybe switch gamemode?
                    throw NotInCreativeModeException()
                }

                interaction.clickCreativeStack(
                    optimalStack,
                    36 + player.inventory.selectedSlot
                )
            }
            delay(TaskFlow.itemMoveDelay)
            return@buildTask
        }

        throw NoOptimalStackException()
    }

    class NotInCreativeModeException : IllegalStateException("Insufficient permission: not in creative mode")
    class NoOptimalStackException : IllegalStateException("Cannot move item: no optimal stack")
}