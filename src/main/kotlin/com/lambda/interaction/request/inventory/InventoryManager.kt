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
import com.lambda.interaction.request.Logger

import com.lambda.interaction.request.RequestHandler
import com.lambda.interaction.request.inventory.InventoryManager.activeRequest
import com.lambda.interaction.request.inventory.InventoryManager.processRequest
import com.lambda.module.hud.ManagerDebugLoggers.inventoryManagerLogger

object InventoryManager : RequestHandler<InventoryRequest>(
    1,
    TickEvent.Pre,
    TickEvent.Input.Pre,
    TickEvent.Input.Post,
    TickEvent.Player.Post,
    onOpen = { activeRequest?.let { processRequest(it) } }
), Logger {
    var activeRequest: InventoryRequest? = null

    override val logger = inventoryManagerLogger

    override fun load(): String {
        super.load()

        return "Loaded Inventory Manager"
    }

    override fun AutomatedSafeContext.handleRequest(request: InventoryRequest) {
        TODO("Not yet implemented")
    }

    fun SafeContext.processRequest(request: InventoryRequest) {

    }

    override fun preEvent() = UpdateManagerEvent.Inventory.post()
}
