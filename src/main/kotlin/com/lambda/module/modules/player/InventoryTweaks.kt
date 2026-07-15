/*
 * Copyright 2026 Lambda
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

import com.lambda.config.ConfigEditor.hideAllExcept
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.withEdits
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollect
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.PlaceContainerTask
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos

object InventoryTweaks : Module(
    name = "InventoryTweaks",
    tag = ModuleTag.PLAYER,
) {
    private val instantShulker by setting("Instant Shulker", true, description = "Right-click shulker boxes in your inventory to instantly place them and open them.")
    private val instantEChest by setting("Instant Ender-Chest", true, description = "Right-click ender chests in your inventory to instantly place them and open them.")

    private var placedPos: BlockPos? = null
    private var placeAndOpen: Task<*>? = null
    private var lastBreak: Task<*>? = null
    private var lastOpenScreen: ScreenHandler? = null

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::breakConfig, ::interactConfig, ::inventoryConfig, ::hotbarConfig)
            }

        listen<PlayerEvent.SlotClick> {
            if (it.action != SlotActionType.PICKUP || it.button != 1) return@listen
            val slot = it.screenHandler.getSlot(it.slot)
            if (!(instantShulker && slot.stack.item in shulkerBoxes) && !(instantEChest && slot.stack.item == Items.ENDER_CHEST)) return@listen
            it.cancel()
            lastOpenScreen = null
            placeAndOpen = PlaceContainerTask(slot, this@InventoryTweaks).then { placePos ->
                placedPos = placePos
                OpenContainerTask(placePos, this@InventoryTweaks).finally { screenHandler ->
                    lastOpenScreen = screenHandler
                }
            }.run()
        }

        listen<InventoryEvent.Close> { event ->
            if (event.screenHandler != lastOpenScreen) return@listen
            lastOpenScreen = null
            placedPos?.let {
                lastBreak = breakAndCollect(it).run()
                placedPos = null
            }
        }

        onDisable {
            placeAndOpen?.cancel()
            lastBreak?.cancel()
        }
    }
}
