package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.InteractionEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.tasks.BuildStructure.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainer.Companion.openContainer
import com.lambda.task.tasks.PlaceContainer.Companion.placeContainer
import com.lambda.util.item.ItemUtils.shulkerBoxes
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object InventoryTweaks : Module(
    name = "InventoryTweaks",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val shulkerPeak by setting("Shulker Peek", true, description = "Peek into shulker boxes in your inventory")
    private var placedPos: BlockPos? = null
    private var lastPlace: Task<*>? = null
    private var lastBreak: Task<*>? = null

    init {
        listener<InteractionEvent.SlotClick> {
            if (!shulkerPeak) return@listener
            if (it.action != SlotActionType.PICKUP || it.button != 1) return@listener
            val stack = it.screenHandler.getSlot(it.slot).stack
            if (stack.item !in shulkerBoxes && stack.item != Items.ENDER_CHEST) return@listener
            it.cancel()
            move(it.slot, stack)

            player.closeScreen()

            lastPlace = placeContainer(stack).thenRun(null) { _, placePos ->
                placedPos = placePos
                openContainer(placePos)
            }.start(null)
        }

        listener<ScreenHandlerEvent.Close> {
            if (!shulkerPeak) return@listener
            placedPos?.let {
                lastBreak = breakAndCollectBlock(it).start(null)
                placedPos = null
            }
        }

        onDisable {
            lastPlace?.cancel()
            lastBreak?.cancel()
        }
    }

    private fun SafeContext.move(index: Int, stack: ItemStack) {
        if (stack == player.mainHandStack) {
            return
        }

        if (stack == player.offHandStack) {
            connection.sendPacket(
                PlayerActionC2SPacket(
                    PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
                    BlockPos.ORIGIN,
                    Direction.DOWN,
                ),
            )
            return
        }

        if (stack in player.hotbar) {
            player.inventory.selectedSlot = player.hotbar.indexOf(stack)
            return
        }

        interaction.pickFromInventory(index)
    }
}