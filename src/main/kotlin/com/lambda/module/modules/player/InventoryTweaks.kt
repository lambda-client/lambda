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

import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.hideAllExcept
import com.lambda.config.withEdits
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.ContainerHandler
import com.lambda.interaction.inventory.container.NestedContainer
import com.lambda.interaction.inventory.container.OpenedContainerContext
import com.lambda.interaction.inventory.container.containers.external.EnderChestContainer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.wrappers.then
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.SlotActionType
import java.util.*

@Suppress("unused")
object InventoryTweaks : Module(
    name = "InventoryTweaks",
    tag = ModuleTag.PLAYER,
) {
    private val instantShulker by setting("Instant Shulker", true, description = "Right-click shulker boxes in your inventory to instantly place them and open them.")
    private val instantEChest by setting("Instant Ender-Chest", true, description = "Right-click ender chests in your inventory to instantly place them and open them.")

    private var openTask: Task<*>? = null
    private val openContexts = LinkedList<OpenedContainerContext>()
    var lastOpenScreen: ScreenHandler? = null
    private var isClosing = false

    init {
        setDefaultAutomationConfig()
            .withEdits {
                hideAllExcept(::breakConfig, ::interactConfig, ::inventoryConfig, ::hotbarConfig)
            }

        listen<PlayerEvent.SlotClick> { event ->
            if (event.action != SlotActionType.PICKUP || event.button != 1) return@listen
            val slot = event.screenHandler.getSlot(event.slot) ?: return@listen
            val stack = slot.stack

            when (stack.item) {
                in shulkerBoxes if (!instantShulker) -> return@listen
                Items.ENDER_CHEST if (!instantEChest) -> return@listen
            }

            val targetContainer =
                if (stack.item == Items.ENDER_CHEST) EnderChestContainer
                else {
                    ContainerHandler.allContainers
                        .filterIsInstance<NestedContainer>()
                        .firstOrNull { container -> container.index == slot.index }
                        ?: return@listen
                }

            event.cancel()

            openTask = targetContainer
                .access()
                ?.onSuccess { ctx ->
                    openContexts.push(ctx)
                    lastOpenScreen = player.currentScreenHandler
                    openTask = null
                }
            openTask?.start()
        }

        listen<InventoryEvent.Close> { event ->
            if (isClosing || openTask != null) return@listen
            if (event.screenHandler != lastOpenScreen || openContexts.isEmpty()) return@listen

            isClosing = true
            val contextsToClose = mutableListOf<OpenedContainerContext>()
            while (openContexts.isNotEmpty()) {
                contextsToClose.add(openContexts.pop())
            }

            val closeTask =
                contextsToClose.fold<OpenedContainerContext, Task<*>?>(null) { acc, ctx ->
                    val task = ctx.close() ?: return@fold acc
	                acc?.then(task) ?: task
                }

            closeTask
                ?.onCompletion { isClosing = false }
                ?.start()
                ?: run { isClosing = false }
        }

        onDisable {
            openContexts.clear()
            isClosing = false
        }
    }
}
