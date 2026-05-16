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

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.config.applyEdits
import com.lambda.context.Automated
import com.lambda.event.events.InventoryEvent
import com.lambda.event.events.PlayerEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.managers.inventory.InventoryRequest.Companion.inventoryRequest
import com.lambda.interaction.material.StackSelection.Companion.selectStack
import com.lambda.interaction.material.container.ContainerManager.lastInteractedBlockEntity
import com.lambda.interaction.material.container.containers.ChestContainer
import com.lambda.interaction.material.container.containers.CombinedPlayerInventory
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.breakAndCollectBlock
import com.lambda.task.tasks.OpenContainerTask
import com.lambda.task.tasks.PlaceContainerTask
import com.lambda.threading.runSafeAutomated
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.entity.player.PlayerInventory
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType
import net.minecraft.util.math.BlockPos

object InventoryTweaks : Module(
    name = "InventoryTweaks",
    tag = ModuleTag.PLAYER,
) {
    private val instantShulker by setting("Instant Shulker", true, description = "Right-click shulker boxes in your inventory to instantly place them and open them.")
    private val fromContainer by setting("From Container", true, description = "Allow instance shulkers from containers") { instantShulker }
    private val backToContainer by setting("Back To Container", true, description = "After closing the shulker, put it back in the container it was taken from") { instantShulker && fromContainer }
    private val instantEChest by setting("Instant Ender-Chest", true, description = "Right-click ender chests in your inventory to instantly place them and open them.")
    private var placedPos: BlockPos? = null
    private var placeAndOpen: Task<*>? = null
    private var takenFromContainer: BlockPos? = null
    private var transferBack: Task<*>? = null
    private var lastBreak: Task<*>? = null
    private var lastOpenScreen: ScreenHandler? = null
    private var lastPickedUpShulker: ItemStack? = null

    init {
        setDefaultAutomationConfig {
            applyEdits {
                hideAllGroupsExcept(breakConfig, interactConfig, inventoryConfig, hotbarConfig)
            }
        }

        listen<PlayerEvent.SlotClick> {
            if (it.action != SlotActionType.PICKUP || it.button != 1) return@listen
            it.screenHandler.getSlot(it.slot).let { slot ->
                if (!(instantShulker && slot.stack.item in shulkerBoxes) && !(instantEChest && slot.stack.item == Items.ENDER_CHEST)) return@listen
                it.cancel()
                if (slot.inventory !is PlayerInventory && fromContainer) {
                    // TODO: place should be able to place blocks from containers without needing to transfer them to the player inventory first. Wait for beanbag to fix.
                    takenFromContainer = lastInteractedBlockEntity?.pos
                    val stackCopy = slot.stack.copy()
                    inventoryRequest {
                        quickMove(slot.id)
                        onComplete {
                            player.closeHandledScreen()

                            (CombinedPlayerInventory.slots).firstOrNull { playerSlot ->
                                ItemStack.areItemsAndComponentsEqual(playerSlot.stack, stackCopy)
                            }?.let { invSlot ->
                                place(invSlot)
                            }
                        }
                    }.submit()
                } else {
                    takenFromContainer = null
                    place(slot)
                }
                lastOpenScreen = null
            }
        }

        listen<InventoryEvent.SlotUpdate2> { event ->
            if (event.stack.item !in shulkerBoxes) return@listen
            lastPickedUpShulker = event.stack
        }

        listen<InventoryEvent.Close> { event ->
            if (event.screenHandler != lastOpenScreen) return@listen
            lastOpenScreen = null
            placedPos?.let { placePos ->
                // TODO: breakAndCollectBlock should be able to return the drops it collected. If it did, we could chain a task to transfer the shulker back to the container it came from. Wait for bean to fix.
                lastBreak = breakAndCollectBlock(placePos).thenOrNull {
                    val lastPos = takenFromContainer
                    if (backToContainer && lastPos != null) return@thenOrNull DepositBackTask(lastPos, this@InventoryTweaks)
                    else return@thenOrNull null
                }.run()
                placedPos = null
            }
        }

        onDisable {
            placeAndOpen?.cancel()
            lastBreak?.cancel()
            transferBack?.cancel()
            lastOpenScreen = null
            takenFromContainer = null
        }
    }

    private fun place(slot: Slot) {
        placeAndOpen = PlaceContainerTask(slot, this@InventoryTweaks).then { placePos ->
            placedPos = placePos
            OpenContainerTask(placePos, this@InventoryTweaks).finally { screenHandler ->
                lastOpenScreen = screenHandler
            }
        }.run()
    }

    class DepositBackTask @Ta5kBuilder constructor(
        private val blockPos: BlockPos,
        private val automated: Automated,
    ) : Task<Unit>(), Automated by automated {
        override val name: String
            get() = "Depositing back to container at ${blockPos.toShortString()}"

        init {
            listen<TickEvent.Pre> {
                lastPickedUpShulker?.let { lastPickedUpShulker ->
                    runSafeAutomated {
                        // TODO: This only works if the target container is a chest. Wait for beanbag to fix.
                        CombinedPlayerInventory.transferByTask(selectStack {
                            { it: ItemStack -> ItemStack.areItemsEqual(it, lastPickedUpShulker) }
                        }, ChestContainer(listOf(), blockPos))
                            .execute(this@DepositBackTask)
                            .thenOrNull {
                                success()
                                this@InventoryTweaks.lastPickedUpShulker = null
                                null
                            }
                    }
                }
            }
        }
    }
}
