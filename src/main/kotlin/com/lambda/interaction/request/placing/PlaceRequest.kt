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

package com.lambda.interaction.request.placing

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.construction.context.PlaceContext
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.Request
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.matches
import net.minecraft.util.math.BlockPos

data class PlaceRequest(
    val contexts: Collection<PlaceContext>,
    val pendingInteractions: MutableCollection<BuildContext>,
    private val automated: Automated,
    override val nowOrNothing: Boolean = false,
    val onPlace: (SafeContext.(BlockPos) -> Unit)? = null
) : Request(), LogContext, Automated by automated {
    override val requestId = ++requestCount

    override val done: Boolean
        get() = runSafe {
            contexts.all { it.expectedState.matches(blockState(it.blockPos)) }
        } == true

    override fun submit(queueIfClosed: Boolean) =
        PlaceManager.request(this, queueIfClosed)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("PlaceRequest") {
            value("Request ID", requestId)
            value("Contexts", contexts.size)
        }
    }

    companion object {
        var requestCount = 0
    }
}
