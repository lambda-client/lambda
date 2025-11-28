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
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.context.BuildContext
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.Request
import com.lambda.interaction.request.breaking.BreakRequest.Companion.breakRequest
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isEmpty
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

/**
 * Contains the information necessary for initializing and continuing breaks within the [BreakManager].
 *
 * The callbacks can be used to keep track of the break progress.
 *
 * A private constructor is used to force use of the cleaner [BreakRequestDsl] builder. This is
 * accessed through the [breakRequest] method.
 *
 * @param contexts A collection of [BreakContext]'s gathered from the BuildSimulator.
 * @param pendingInteractions A mutable, concurrent list to store the pending actions.
 *
 * @see com.lambda.interaction.construction.simulation.BuildSimulator
 */
data class BreakRequest private constructor(
    val contexts: Collection<BreakContext>,
    val pendingInteractions: MutableCollection<BuildContext>,
    private val automated: Automated,
    override val nowOrNothing: Boolean = false
) : Request(), LogContext, Automated by automated {
    override val requestId = ++requestCount
    override val tickStageMask get() = breakConfig.tickStageMask

    var onStart: (SafeContext.(BlockPos) -> Unit)? = null
    var onUpdate: (SafeContext.(BlockPos) -> Unit)? = null
    var onStop: (SafeContext.(BlockPos) -> Unit)? = null
    var onCancel: (SafeContext.(BlockPos) -> Unit)? = null
    var onItemDrop: (SafeContext.(ItemEntity) -> Unit)? = null
    var onReBreakStart: (SafeContext.(BlockPos) -> Unit)? = null
    var onReBreak: (SafeContext.(BlockPos) -> Unit)? = null

    override val done: Boolean
        get() = runSafe { contexts.all { blockState(it.blockPos).isEmpty } } == true

    override fun submit(queueIfMismatchedStage: Boolean) =
        BreakManager.request(this, queueIfMismatchedStage)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("Break Request") {
            value("Request ID", requestId)
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
    annotation class BreakRequestDsl

    @BreakRequestDsl
    class BreakRequestBuilder(
        contexts: Collection<BreakContext>,
        pendingInteractions: MutableCollection<BuildContext>,
        nowOrNothing: Boolean,
        automated: Automated
    ) {
        val request = BreakRequest(contexts, pendingInteractions, automated, nowOrNothing)

        @BreakRequestDsl
        fun onStart(callback: SafeContext.(BlockPos) -> Unit) {
            request.onStart = callback
        }

        @BreakRequestDsl
        fun onUpdate(callback: SafeContext.(BlockPos) -> Unit) {
            request.onUpdate = callback
        }

        @BreakRequestDsl
        fun onStop(callback: SafeContext.(BlockPos) -> Unit) {
            request.onStop = callback
        }

        @BreakRequestDsl
        fun onCancel(callback: SafeContext.(BlockPos) -> Unit) {
            request.onCancel = callback
        }

        @BreakRequestDsl
        fun onItemDrop(callback: SafeContext.(ItemEntity) -> Unit) {
            request.onItemDrop = callback
        }

        @BreakRequestDsl
        fun onReBreakStart(callback: SafeContext.(BlockPos) -> Unit) {
            request.onReBreakStart = callback
        }

        @BreakRequestDsl
        fun onReBreak(callback: SafeContext.(BlockPos) -> Unit) {
            request.onReBreak = callback
        }
    }

    companion object {
        var requestCount = 0

        @BreakRequestDsl
        fun Automated.breakRequest(
            contexts: Collection<BreakContext>,
            pendingInteractions: MutableCollection<BuildContext>,
            nowOrNothing: Boolean = false,
            builder: (BreakRequestBuilder.() -> Unit)? = null
        ) = BreakRequestBuilder(contexts, pendingInteractions, nowOrNothing, this).apply { builder?.invoke(this) }.build()

        @BreakRequestDsl
        private fun BreakRequestBuilder.build(): BreakRequest = request
    }
}
