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

package com.lambda.interaction.request.breaking

import com.lambda.context.Automated
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.Request
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isEmpty
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

data class BreakRequest(
    val contexts: Collection<BreakContext>,
    val pendingInteractions: MutableCollection<BuildContext>,
    private val automated: Automated
) : Request(), LogContext, Automated by automated {
    override val requestID = ++requestCount

    var onStart: ((BlockPos) -> Unit)? = null
    var onUpdate: ((BlockPos) -> Unit)? = null
    var onStop: ((BlockPos) -> Unit)? = null
    var onCancel: ((BlockPos) -> Unit)? = null
    var onItemDrop: ((ItemEntity) -> Unit)? = null
    var onReBreakStart: ((BlockPos) -> Unit)? = null
    var onReBreak: ((BlockPos) -> Unit)? = null

    override val done: Boolean
        get() = runSafe { contexts.all { blockState(it.blockPos).isEmpty } } == true

    override fun submit(queueIfClosed: Boolean) =
        BreakManager.request(this, queueIfClosed)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Break Request") {
            value("Request ID", requestID)
            value("Contexts", contexts.size)
            group("Callbacks") {
                value("onStart", onStart != null)
                value("onUpdate", onUpdate != null)
                value("onStop", onStop != null)
                value("onCancel", onCancel != null)
                value("onItemDrop", onItemDrop != null)
                value("onReBreakStart", onReBreakStart != null)
                value("onReBreak", onReBreak != null)
            }
        }
    }

    @DslMarker
    annotation class BreakRequestBuilder

    @BreakRequestBuilder
    class RequestBuilder(
        contexts: Collection<BreakContext>,
        pendingInteractions: MutableCollection<BuildContext>,
        automated: Automated
    ) {
        val request = BreakRequest(contexts, pendingInteractions, automated)

        @BreakRequestBuilder
        fun onStart(callback: (BlockPos) -> Unit) {
            request.onStart = callback
        }

        @BreakRequestBuilder
        fun onUpdate(callback: (BlockPos) -> Unit) {
            request.onUpdate = callback
        }

        @BreakRequestBuilder
        fun onStop(callback: (BlockPos) -> Unit) {
            request.onStop = callback
        }

        @BreakRequestBuilder
        fun onCancel(callback: (BlockPos) -> Unit) {
            request.onCancel = callback
        }

        @BreakRequestBuilder
        fun onItemDrop(callback: (ItemEntity) -> Unit) {
            request.onItemDrop = callback
        }

        @BreakRequestBuilder
        fun onReBreakStart(callback: (BlockPos) -> Unit) {
            request.onReBreakStart = callback
        }

        @BreakRequestBuilder
        fun onReBreak(callback: (BlockPos) -> Unit) {
            request.onReBreak = callback
        }

        @BreakRequestBuilder
        fun build(): BreakRequest = request
    }

    companion object {
        var requestCount = 0

        @BreakRequestBuilder
        fun Automated.breakRequest(
            contexts: Collection<BreakContext>,
            pendingInteractions: MutableCollection<BuildContext>,
            builder: RequestBuilder.() -> Unit
        ) = RequestBuilder(contexts, pendingInteractions, this).apply(builder).build()
    }
}
