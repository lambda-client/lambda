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
import com.lambda.util.player.SlotUtils.clickSlot
import com.lambda.util.player.SlotUtils.hotbar
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object InventoryTweaks : Module(
    name = "InventoryTweaks",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val instantShulker by setting("Instant Shulker", true, description = "Right-click shulker boxes in your inventory to instantly place them and open them.")
    private val instantEChest by setting("Instant Ender-Chest", true, description = "Right-click ender chests in your inventory to instantly place them and open them.")
    private var placedPos: BlockPos? = null
    private var lastPlace: Task<*>? = null
    private var lastBreak: Task<*>? = null
    private var lastOpenScreen: ScreenHandler? = null

    init {
        listener<InteractionEvent.SlotClick> {
            if (it.action != SlotActionType.PICKUP || it.button != 1) return@listener
            val stack = it.screenHandler.getSlot(it.slot).stack
            if (!(instantShulker && stack.item in shulkerBoxes) && !(instantEChest && stack.item == Items.ENDER_CHEST)) return@listener
            it.cancel()
            move(it.slot, stack)

            player.closeScreen()

            lastPlace = placeContainer(stack).thenRun(null) { _, placePos ->
                placedPos = placePos
                openContainer(placePos).onSuccess { _, screenHandler ->
                    lastOpenScreen = screenHandler
                }
            }.start(null)
        }

        listener<ScreenHandlerEvent.Close> { event ->
            if (event.screenHandler != lastOpenScreen) return@listener
            lastOpenScreen = null
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

        clickSlot(index, player.inventory.selectedSlot, SlotActionType.SWAP)
    }
}