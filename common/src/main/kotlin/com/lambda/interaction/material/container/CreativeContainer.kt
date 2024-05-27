package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task.Companion.buildTask
import com.lambda.util.item.ItemStackUtils.equal
import net.minecraft.item.ItemStack

data object CreativeContainer : MaterialContainer(Rank.CREATIVE) {
    override var stacks = emptyList<ItemStack>()

    override fun available(selection: StackSelection): Int =
        if (mc.player?.isCreative == true && selection.optimalStack != null) Int.MAX_VALUE else 0

    override fun deposit(selection: StackSelection) = buildTask("CreativeDeposit") {
        if (!player.isCreative) {
            // ToDo: Maybe switch gamemode?
            throw NotInCreativeModeException()
        }

        interaction.clickCreativeStack(
            ItemStack.EMPTY,
            36 + player.inventory.selectedSlot
        )
    }

    // Withdraws items from the creative menu to the player's main hand
    override fun withdraw(selection: StackSelection) = buildTask("CreativeWithdraw") {
        selection.optimalStack?.let { optimalStack ->
            if (player.mainHandStack.equal(optimalStack)) return@buildTask

            if (!player.isCreative) {
                // ToDo: Maybe switch gamemode?
                throw NotInCreativeModeException()
            }

            interaction.clickCreativeStack(
                optimalStack,
                36 + player.inventory.selectedSlot
            )
            return@buildTask
        }

        throw NoOptimalStackException()
    }

    class NotInCreativeModeException : IllegalStateException("Insufficient permission: not in creative mode")
    class NoOptimalStackException : IllegalStateException("Cannot move item: no optimal stack")
}