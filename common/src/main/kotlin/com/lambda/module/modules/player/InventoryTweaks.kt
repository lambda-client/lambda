/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.player

import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.ScreenHandlerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.TaskFlow.run
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainer
import com.lambda.task.tasks.PlaceContainer
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
        listen<PlayerEvent.SlotClick> {
            if (it.action != SlotActionType.PICKUP || it.button != 1) return@listen
            val stack = it.screenHandler.getSlot(it.slot).stack
            if (!(instantShulker && stack.item in shulkerBoxes) && !(instantEChest && stack.item == Items.ENDER_CHEST)) return@listen
            it.cancel()
            move(it.slot, stack)

            player.closeScreen()

            lastPlace = PlaceContainer(stack).then { placePos ->
                placedPos = placePos
                OpenContainer(placePos).finally { screenHandler ->
                    lastOpenScreen = screenHandler
                }
            }.run()
        }

        listen<ScreenHandlerEvent.Close> { event ->
            if (event.screenHandler != lastOpenScreen) return@listen
            lastOpenScreen = null
            placedPos?.let {
                lastBreak = breakAndCollectBlock(it).run()
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
