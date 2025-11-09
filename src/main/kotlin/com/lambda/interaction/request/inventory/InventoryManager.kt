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

import com.lambda.context.AutomatedSafeContext
import com.lambda.context.AutomationConfig
import com.lambda.context.AutomationConfig.avoidDesync
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.module.hud.ManagerDebugLoggers.inventoryManagerLogger
import com.lambda.util.Communication.info
import com.lambda.util.collections.LimitedDecayQueue
import com.lambda.util.item.ItemStackUtils.equal
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.InventoryS2CPacket
import net.minecraft.screen.ScreenHandler
import net.minecraft.screen.slot.Slot

object InventoryManager : RequestHandler<InventoryRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post
), Logger {
    private var actions = mutableListOf<SafeContext.() -> Unit>()

    private var slots = listOf<ItemStack>()
    private var alteredSlots = LimitedDecayQueue<Pair<Int, Pair<ItemStack, ItemStack>>>(
        AutomationConfig.maxDesyncCache, AutomationConfig.desyncTimeout * 50L
    )

    private var screenHandler: ScreenHandler? = null
        set(value) {
            if (value != null && field !== value)
                slots = getStacks(value.slots)
            field = value
        }

    private var maxActionsThisSecond = 0
    private var actionsThisSecond = 0
    private var secondCounter = 0
    private var actionsThisTick = 0

    override val logger = inventoryManagerLogger

    override fun load(): String {
        super.load()

        listen<PacketEvent.Receive.Pre>(priority = Int.MIN_VALUE) { event ->
            if (!avoidDesync) return@listen
            val packet = event.packet as? InventoryS2CPacket ?: return@listen
            screenHandler = player.currentScreenHandler
            val packetScreenHandler =
                if (packet.syncId == 0) player.playerScreenHandler
                else player.currentScreenHandler
            event.cancel()
            val alteredContents = mutableListOf<ItemStack>()
            packet.contents.forEachIndexed { index, incomingStack ->
                val matches = alteredSlots.removeIf { cached ->
                    incomingStack.equal(cached.second.second)
                }
                if (matches) alteredContents.add(packetScreenHandler.slots[index].stack)
                else alteredContents.add(incomingStack)
                if (matches) info(matches.toString())
            }
            mc.executeSync {
                packetScreenHandler.updateSlotStacks(packet.revision(), alteredContents, packet.cursorStack())
            }
        }

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            if (avoidDesync) {
                alteredSlots.addAll(gatherInventoryChanges())
                slots = getStacks(player.currentScreenHandler.slots)
            }
            if (++secondCounter >= 20) {
                secondCounter = 0
                actionsThisSecond = 0
            }
            actionsThisTick = 0
            actions.clear()
        }

        return "Loaded Inventory Manager"
    }

    override fun AutomatedSafeContext.handleRequest(request: InventoryRequest) {
        if (request.actions.size >= request.inventoryConfig.actionsPerSecond - actionsThisSecond &&
            !request.settleForLess &&
            !request.mustPerform) return
        if (tickStage !in inventoryConfig.tickStageMask) return

        if (request.fresh) populateFrom(request)

        PlaceManager.logger.debug("Processing request", request)

        screenHandler = player.currentScreenHandler
        val iterator = actions.iterator()
        while (iterator.hasNext()) {
            if (actionsThisSecond + 1 > maxActionsThisSecond && !request.mustPerform) break
            iterator.next()()
            actionsThisTick++
            actionsThisSecond++
            iterator.remove()
        }

        if (actions.isEmpty()) {
            request.done = true
            request.onComplete?.invoke(this)
        }

        if (actionsThisTick > 0) activeThisTick = true
    }

    private fun populateFrom(request: InventoryRequest) {
        PlaceManager.logger.debug("Populating from request", request)
        actions = request.actions.toMutableList()
        maxActionsThisSecond = request.inventoryConfig.actionsPerSecond
    }

    private fun SafeContext.gatherInventoryChanges() =
        if (player.currentScreenHandler !== screenHandler) emptyList()
        else screenHandler?.slots
            ?.filter { it.stack != slots[it.id] }
            ?.map { Pair(it.id, Pair(slots[it.id], it.stack.copy())) }
            ?: emptyList()

    private fun getStacks(slots: Collection<Slot>) = slots.map { it.stack.copy() }

    override fun preEvent() = UpdateManagerEvent.Inventory.post()
}
