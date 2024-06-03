package com.lambda.interaction.material.container

import com.lambda.Lambda.mc
import com.lambda.interaction.material.MaterialContainer
import com.lambda.interaction.material.StackSelection
import com.lambda.task.Task.Companion.buildTask
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object OffHandContainer : MaterialContainer(Rank.OFF_HAND) {
    override var stacks: List<ItemStack>
        get() = mc.player?.offHandStack?.let { listOf(it) } ?: emptyList()
        set(_) {}
    override val name = "OffHand"

    override fun withdraw(selection: StackSelection) = emptyTask("WithdrawFromOffHand")

    override fun deposit(selection: StackSelection) = buildTask("DepositToOffHand") {
        InventoryContainer.matchingStacks(selection).firstOrNull()?.let { stack ->
            if (ItemStack.areEqual(stack, player.offHandStack)) {
                return@buildTask
            }

            if (stack in player.hotbar) {
                player.inventory.selectedSlot = player.hotbar.indexOf(stack)
            } else {
                interaction.pickFromInventory(player.combined.indexOf(stack))
            }

            if (ItemStack.areEqual(stack, player.mainHandStack)) {
                connection.sendPacket(
                    PlayerActionC2SPacket(
                        PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                        BlockPos.ORIGIN,
                        Direction.DOWN,
                    ),
                )
            }
        }
    }
}