/*
 * Copyright 2025 Lambda
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

package com.lambda.interaction.request.inventory

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.RequestHandler
import net.minecraft.screen.slot.Slot
import net.minecraft.screen.slot.SlotActionType

//ToDo: implement :doom:
object InventoryManager : RequestHandler<InventoryRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post
) {
    private var actionsThisTick = 0
    private var maxActionsThisTick = 0

    override fun load(): String {
        super.load()

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            actionsThisTick = 0
        }

        return "Loaded Inventory Manager!"
    }

    override fun SafeContext.handleRequest(request: InventoryRequest) {
        if (actionsThisTick + request.actions.size >= maxActionsThisTick) return
        if (request.actions.any { !it.canPerform() }) return
        request.actions.forEach { action ->
            actionsThisTick++
            if (!action.perform().done) return
        }
    }

    sealed class InventoryAction {
        abstract val slot: Slot
        var done = false
            private set

        fun perform(): InventoryAction {
            done = internalPerform()
            return this
        }

        abstract fun internalPerform(): Boolean
        abstract fun canPerform(): Boolean

        data class Swap(override val slot: Slot, val to: Slot) : InventoryAction() {
            override fun internalPerform() = clickSlot(slot.id, 0, SlotActionType.SWAP)
            override fun canPerform() = slot.isNotEmpty || to.isNotEmpty
        }

        data class QuickMove(override val slot: Slot) : InventoryAction() {
            override fun internalPerform() = clickSlot(slot.id, 0, SlotActionType.QUICK_MOVE)
            override fun canPerform() = slot.isNotEmpty
        }

        data class Throw(override val slot: Slot) : InventoryAction() {
            override fun internalPerform() = clickSlot(slot.id, 0, SlotActionType.THROW)
            override fun canPerform() = slot.isNotEmpty
        }

        data class Distribute(override val slot: Slot, val slots: List<Slot>) : InventoryAction() {
            override fun internalPerform(): Boolean {
                TODO("Not yet implemented")
            }

            override fun canPerform() = slot.isNotEmpty && slots.all { it.canInsert(slot.stack) }
        }
        data class Clone(override val slot: Slot) : InventoryAction() {
            override fun internalPerform(): Boolean {
                TODO("Not yet implemented")
            }

            override fun canPerform() = slot.isNotEmpty && mc.player?.isCreative == true
        }

        fun clickSlot(slotId: Int, button: Int, action: SlotActionType): Boolean {
            mc.interactionManager?.let { interaction ->
                mc.player?.playerScreenHandler?.syncId?.let { syncId ->
                    interaction.clickSlot(syncId, slotId, button, action, mc.player)
                    return true
                }
            }
            return false
        }
    }

    private val Slot.isEmpty get() = !hasStack()
    private val Slot.isNotEmpty get() = hasStack()

    override fun preEvent() = UpdateManagerEvent.Inventory.post()
}