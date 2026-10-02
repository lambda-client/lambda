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

package com.lambda.module.modules.debug

import com.lambda.context.AutomatedSafeContext
import com.lambda.event.events.ButtonEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.container.ContainerType
import com.lambda.interaction.container.PlacedContainer
import com.lambda.interaction.container.containers.external.DoubleChestContainer
import com.lambda.interaction.container.selection.ContainerSelectionBuilder.Companion.containerSelection
import com.lambda.interaction.container.selection.select
import com.lambda.interaction.handler.handlers.ContainerSearchScope
import com.lambda.interaction.handler.handlers.findContainers
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.task.start
import com.lambda.task.tasks.transfer
import com.lambda.threading.runSafeAutomated
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.math.distSq
import net.minecraft.block.ChestBlock
import net.minecraft.item.Items
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import org.lwjgl.glfw.GLFW

@Suppress("unused")
object ContainerTransferTest : Module(
    name = "ContainerTransferTest",
    description = "Test moving stack selections over 1 stack from one external container to another.",
    tag = ModuleTag.DEBUG,
) {
    private val amountToMove by setting("Amount", 128, 1..1000)

    private var sourcePos: BlockPos? = null
    private var destPos: BlockPos? = null

    init {
        onEnable {
            sourcePos = null
            destPos = null
            info("Middle-click source chest, then middle-click destination chest.")
        }

        listen<ButtonEvent.Mouse.Click> { event ->
            if (event.button != GLFW.GLFW_MOUSE_BUTTON_MIDDLE || event.action != 1) return@listen
            
            val hitResult = mc.crosshairTarget as? BlockHitResult ?: return@listen
            val pos = hitResult.blockPos
            
            val block = blockState(pos).block
            if (block !is ChestBlock) return@listen
            
            if (sourcePos == null) {
                sourcePos = pos
                info("Source container set to $pos. Now middle-click destination.")
                event.cancel()
            } else if (destPos == null && pos != sourcePos) {
                destPos = pos
                info("Destination container set to $pos. Starting transfer task...")
                event.cancel()
                
                runSafeAutomated {
                    startTransferTask(sourcePos!!, destPos!!)
                }
                
                disable()
            }
        }
    }

    private fun AutomatedSafeContext.startTransferTask(fromPos: BlockPos, toPos: BlockPos) {
        info("--- DEBUG INFO ---")
        val allChests = findContainers(containerSelection(ContainerSearchScope.All) { ofAnyType(ContainerType.Chest) }).toList()
        info("Total known Chest containers in scope All: ${allChests.size}")
        
        val matchedFrom = allChests.filter { 
            val placed = it as? PlacedContainer ?: return@filter false
            placed.pos distSq fromPos <= 4.0 ||
                (placed is DoubleChestContainer && (placed.leftPos distSq fromPos <= 4.0 || placed.rightPos distSq fromPos <= 4.0))
        }
        info("Chests matching fromPos distance: ${matchedFrom.size}")
        
        if (matchedFrom.isNotEmpty()) {
            val fromContainer = matchedFrom.first()
            val totalItems = fromContainer.stacks.sumOf { it.count }
            info("First matched source container has $totalItems total items.")
            if (totalItems < amountToMove) {
                info("WARNING: Source container has fewer items ($totalItems) than required ($amountToMove). The task will fail!")
            }
        } else {
            info("WARNING: No chest found near fromPos. Did you open it at least once to save it?")
        }

        val matchedTo = allChests.filter { 
            val placed = it as? PlacedContainer ?: return@filter false
            placed.pos distSq toPos <= 4.0 ||
                (placed is DoubleChestContainer && (placed.leftPos distSq toPos <= 4.0 || placed.rightPos distSq toPos <= 4.0))
        }
        info("Chests matching toPos distance: ${matchedTo.size}")
        if (matchedTo.isNotEmpty()) {
            val toContainer = matchedTo.first()
            info("First matched destination container has ${toContainer.stacks.size} slots (${toContainer.stacks.count { it.isEmpty }} empty).")
        } else {
            info("WARNING: No chest found near toPos. Did you open it at least once to save it?")
        }
        info("------------------")

        val fromSelection =
            containerSelection(ContainerSearchScope.All) {
                ofAnyType(ContainerType.Chest)
                predicate { container ->
                    val placed = container as? PlacedContainer ?: return@predicate false
                    placed.pos distSq fromPos <= 4.0 ||
                        (placed is DoubleChestContainer && (placed.leftPos distSq fromPos <= 4.0 || placed.rightPos distSq fromPos <= 4.0))
                }
            }
        val toSelection =
            containerSelection(ContainerSearchScope.All) {
                ofAnyType(ContainerType.Chest)
                predicate { container ->
                    val placed = container as? PlacedContainer ?: return@predicate false
                    placed.pos distSq toPos <= 4.0 ||
                        (placed is DoubleChestContainer && (placed.leftPos distSq toPos <= 4.0 || placed.rightPos distSq toPos <= 4.0))
                }
            }

        val stackSelection = Items.OBSIDIAN.select(amountToMove)

        stackSelection.transfer(fromSelection, toSelection)
            .onSuccess { info("Transfer task completed!") }
            .onFailure { info("Transfer task failed: $it") }
            .start()
    }
}
