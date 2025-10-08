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

import com.lambda.context.Automated
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.Request

class InventoryRequest(
    automated: Automated
) : Request(), LogContext, Automated by automated {
    override val requestID = ++requestCount

    override val done: Boolean
        get() = TODO("Not yet implemented")

    override fun submit(queueIfClosed: Boolean) =
        InventoryManager.request(this, queueIfClosed)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Inventory Request") {
            value("Request ID", requestID)
        }
    }

    companion object {
        var requestCount = 0
    }
}
