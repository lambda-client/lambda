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
import com.lambda.context.SafeContext
import com.lambda.event.EventFlow.post
import com.lambda.event.events.TickEvent
import com.lambda.event.events.UpdateManagerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.request.Logger
import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.inventory.InventoryManager.activeRequest
import com.lambda.interaction.request.inventory.InventoryManager.processRequest
import com.lambda.interaction.request.placing.PlaceManager
import com.lambda.module.hud.ManagerDebugLoggers.inventoryManagerLogger
import com.lambda.threading.runSafeAutomated

object InventoryManager : RequestHandler<InventoryRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = { activeRequest?.let { it.runSafeAutomated { processRequest(it) } } }
), Logger {
    private var activeRequest: InventoryRequest? = null
    private var actions = mutableListOf<SafeContext.() -> Unit>()

    private var maxActionsThisSecond = 0
    private var actionsThisSecond = 0
    private var secondCounter = 0
    private var actionsThisTick = 0
        set(value) {
            field += value
            actionsThisSecond+= value
        }

    override val logger = inventoryManagerLogger

    override fun load(): String {
        super.load()

        listen<TickEvent.Post>(priority = Int.MIN_VALUE) {
            if (++secondCounter >= 20) {
                secondCounter = 0
                actionsThisSecond = 0
            }
            actionsThisTick = 0
            activeRequest = null
            actions.clear()
        }

        return "Loaded Inventory Manager"
    }

    override fun AutomatedSafeContext.handleRequest(request: InventoryRequest) {
        if (activeRequest != null) return
        if (request.actions.size >= request.inventoryConfig.actionsPerSecond - actionsThisSecond &&
            !request.settleForLess &&
            !request.mustPerform) return
        activeRequest = request
        processRequest(request)
        if (actionsThisTick > 0) activeThisTick = true
    }

    private fun AutomatedSafeContext.processRequest(request: InventoryRequest) {
        if (request.fresh) populateFrom(request)

        if (tickStage !in inventoryConfig.tickStageMask) return

        PlaceManager.logger.debug("Processing request", request)

        val iterator = actions.iterator()
        while (iterator.hasNext()) {
            if (actionsThisSecond + 1 > maxActionsThisSecond && !request.mustPerform) break
            iterator.next()()
            actionsThisTick++
            iterator.remove()
        }

        if (actions.isEmpty()) {
            if (activeRequest != null) {
                activeRequest?.done = true
                logger.debug("Clearing active request", activeRequest)
                activeRequest = null
            }
        }
    }

    private fun populateFrom(request: InventoryRequest) {
        PlaceManager.logger.debug("Populating from request", request)
        actions = request.actions.toMutableList()
        maxActionsThisSecond = request.inventoryConfig.actionsPerSecond
    }

    override fun preEvent() = UpdateManagerEvent.Inventory.post()
}
