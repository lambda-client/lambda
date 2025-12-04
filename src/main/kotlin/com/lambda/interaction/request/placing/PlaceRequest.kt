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
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Dependent
import com.lambda.interaction.construction.result.results.PlaceResult
import com.lambda.interaction.request.LogContext
import com.lambda.interaction.request.LogContext.Companion.LogContextBuilder
import com.lambda.interaction.request.Request
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.matches
import net.minecraft.util.math.BlockPos

data class PlaceRequest private constructor(
    val contexts: Collection<PlaceContext>,
    val pendingInteractions: MutableCollection<BuildContext>,
    private val automated: Automated,
    override val nowOrNothing: Boolean = false,
) : Request(), LogContext, Automated by automated {
    override val requestId = ++requestCount
    override val tickStageMask get() = placeConfig.tickStageMask

	var onPlace: (SafeContext.(BlockPos) -> Unit)? = null

    override val done: Boolean
        get() = runSafe {
            contexts.all { it.expectedState.matches(blockState(it.blockPos)) }
        } == true

    override fun submit(queueIfMismatchedStage: Boolean) =
        PlaceManager.request(this, queueIfMismatchedStage)

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
		contexts: Collection<PlaceContext>,
		pendingInteractions: MutableCollection<BuildContext>,
		nowOrNothing: Boolean,
		automated: Automated
	) {
		val request = PlaceRequest(contexts, pendingInteractions, automated, nowOrNothing)

		@PlaceRequestDsl
		fun onPlace(callback: SafeContext.(BlockPos) -> Unit) {
			request.onPlace = callback
		}
	}

    companion object {
        var requestCount = 0

	    @PlaceRequestDsl
	    @JvmName("placeRequest1")
	    context(automated: Automated)
	    fun Collection<BuildResult>.placeRequest(
			pendingInteractions: MutableCollection<BuildContext>,
			nowOrNothing: Boolean = false,
			builder: (PlaceRequestBuilder.() -> Unit)? = null
		) = asSequence()
		    .map { if (it is Dependent) it.lastDependency else it }
		    .filterIsInstance<PlaceResult.Place>()
		    .map { it.context }
		    .toSet()
		    .takeIf { it.isNotEmpty() }
		    ?.let { automated.placeRequest(it, pendingInteractions, nowOrNothing, builder) }

	    @PlaceRequestDsl
	    @JvmName("placeRequest2")
	    fun Automated.placeRequest(
		    contexts: Collection<PlaceContext>,
		    pendingInteractions: MutableCollection<BuildContext>,
		    nowOrNothing: Boolean = false,
			builder: (PlaceRequestBuilder.() -> Unit)? = null
		) = PlaceRequestBuilder(contexts, pendingInteractions, nowOrNothing, this).apply { builder?.invoke(this) }.build()

	    @PlaceRequestDsl
	    fun PlaceRequestBuilder.build() = request
    }
}
