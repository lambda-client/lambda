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

package com.lambda.interaction.managers.interacting

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.context.InteractContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.results.InteractResult
import com.lambda.interaction.managers.LogContext
import com.lambda.interaction.managers.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.managers.Request
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.matches
import net.minecraft.util.math.BlockPos

data class InteractRequest private constructor(
	val contexts: Collection<InteractContext>,
	val pendingInteractions: MutableCollection<BuildContext>,
	private val automated: Automated,
	override val nowOrNothing: Boolean = false,
) : Request(), LogContext, Automated by automated {
    override val requestId = ++requestCount
    override val tickStageMask get() = interactConfig.tickStageMask

	var onPlace: (SafeContext.(BlockPos) -> Unit)? = null

    override val done: Boolean
        get() = runSafe {
            contexts.all { it.expectedState.matches(blockState(it.blockPos)) }
        } == true

    override fun submit(queueIfMismatchedStage: Boolean) =
        InteractManager.request(this, queueIfMismatchedStage)

    override fun getLogContextBuilder(): LogContextBuilder.() -> Unit = {
        group("PlaceRequest") {
            value("Request ID", requestId)
            value("Contexts", contexts.size)
        }
    }

	@DslMarker
	annotation class PlaceRequestDsl

	@PlaceRequestDsl
	class PlaceRequestBuilder(
		contexts: Collection<InteractContext>,
		pendingInteractions: MutableCollection<BuildContext>,
		nowOrNothing: Boolean,
		automated: Automated
	) {
		val request = InteractRequest(contexts, pendingInteractions, automated, nowOrNothing)

		@PlaceRequestDsl
		fun onPlace(callback: SafeContext.(BlockPos) -> Unit) {
			request.onPlace = callback
		}
	}

    companion object {
        var requestCount = 0

	    @PlaceRequestDsl
	    @JvmName("interactRequest1")
	    context(automated: Automated)
	    fun Collection<BuildResult>.interactRequest(
		    pendingInteractions: MutableCollection<BuildContext>,
		    nowOrNothing: Boolean = false,
		    builder: (PlaceRequestBuilder.() -> Unit)? = null
	    ) = asSequence()
		    .map { if (it is Dependent) it.lastDependency else it }
		    .filterIsInstance<InteractResult.Interact>()
		    .map { it.context }
		    .toSet()
		    .takeIf { it.isNotEmpty() }
		    ?.let { automated.interactRequest(it, pendingInteractions, nowOrNothing, builder) }

	    @PlaceRequestDsl
	    @JvmName("interactRequest2")
	    fun Automated.interactRequest(
		    contexts: Collection<InteractContext>,
		    pendingInteractions: MutableCollection<BuildContext>,
		    nowOrNothing: Boolean = false,
		    builder: (PlaceRequestBuilder.() -> Unit)? = null
		) = PlaceRequestBuilder(contexts, pendingInteractions, nowOrNothing, this).apply { builder?.invoke(this) }.build()

	    @PlaceRequestDsl
	    fun PlaceRequestBuilder.build() = request
    }
}
