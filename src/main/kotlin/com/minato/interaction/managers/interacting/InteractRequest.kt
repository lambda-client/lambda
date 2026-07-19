
package com.minato.interaction.managers.interacting

import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.interaction.construction.simulation.context.BuildContext
import com.minato.interaction.construction.simulation.context.InteractContext
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.Dependent
import com.minato.interaction.construction.simulation.result.results.InteractResult
import com.minato.interaction.managers.Request
import com.minato.threading.runSafe
import com.minato.util.BlockUtils.blockState
import com.minato.util.BlockUtils.matches
import net.minecraft.util.math.BlockPos

data class InteractRequest private constructor(
	val contexts: Collection<InteractContext>,
	val pendingInteractions: MutableCollection<BuildContext>,
	private val automated: Automated,
	override val nowOrNothing: Boolean = false,
) : Request(), Automated by automated {
	override val requestId = ++requestCount
	override val tickStageMask get() = interactConfig.tickStageMask

	var onPlace: (SafeContext.(BlockPos) -> Unit)? = null

	override val done: Boolean
		get() = runSafe {
			contexts.all { it.expectedState.matches(blockState(it.blockPos)) }
		} == true

	@DslMarker
	annotation class PlaceRequestDsl

	@PlaceRequestDsl
	override fun submit(queueIfMismatchedStage: Boolean) =
		InteractManager.request(this, queueIfMismatchedStage)

	@PlaceRequestDsl
	class PlaceRequestBuilder(
		contexts: Collection<InteractContext>,
		pendingInteractions: MutableCollection<BuildContext>,
		nowOrNothing: Boolean,
		automated: Automated
	) {
		val request = InteractRequest(contexts, pendingInteractions, automated, nowOrNothing)

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
			.interactRequest(pendingInteractions, nowOrNothing, builder)

		@PlaceRequestDsl
		@JvmName("interactRequest2")
		context(automated: Automated)
		fun Sequence<BuildResult>.interactRequest(
			pendingInteractions: MutableCollection<BuildContext>,
			nowOrNothing: Boolean = false,
			builder: (PlaceRequestBuilder.() -> Unit)? = null
		) = map { if (it is Dependent) it.lastDependency else it }
			.filterIsInstance<InteractResult.Interact>()
			.sorted()
			.map { it.context }
			.toSet()
			.takeIf { it.isNotEmpty() }
			?.let { automated.interactRequest(it, pendingInteractions, nowOrNothing, builder) }

		@PlaceRequestDsl
		@JvmName("interactRequest3")
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
