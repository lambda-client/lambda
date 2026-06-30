/*
 * Copyright 2026 Lambda
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

package com.lambda.interaction.managers.breaking

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.simulation.context.BreakContext
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.results.BreakResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.managers.Request
import com.lambda.interaction.managers.breaking.BreakRequest.Companion.breakRequest
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
) : Request(), Automated by automated {
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

	@DslMarker
	annotation class BreakRequestDsl

	@BreakRequestDsl
	override fun submit(queueIfMismatchedStage: Boolean) =
		BreakManager.request(this, queueIfMismatchedStage)

	@BreakRequestDsl
	class BreakRequestBuilder(
		contexts: Collection<BreakContext>,
		pendingInteractions: MutableCollection<BuildContext>,
		nowOrNothing: Boolean,
		automated: Automated
	) {
		val request = BreakRequest(contexts, pendingInteractions, automated, nowOrNothing)

		fun onStart(callback: SafeContext.(BlockPos) -> Unit) {
			request.onStart = callback
		}

		fun onUpdate(callback: SafeContext.(BlockPos) -> Unit) {
			request.onUpdate = callback
		}

		fun onStop(callback: SafeContext.(BlockPos) -> Unit) {
			request.onStop = callback
		}

		fun onCancel(callback: SafeContext.(BlockPos) -> Unit) {
			request.onCancel = callback
		}

		fun onItemDrop(callback: SafeContext.(ItemEntity) -> Unit) {
			request.onItemDrop = callback
		}

		fun onReBreakStart(callback: SafeContext.(BlockPos) -> Unit) {
			request.onReBreakStart = callback
		}

		fun onReBreak(callback: SafeContext.(BlockPos) -> Unit) {
			request.onReBreak = callback
		}
	}

	companion object {
		var requestCount = 0

		@BreakRequestDsl
		@JvmName("breakRequest1")
		fun AutomatedSafeContext.breakRequest(
			positions: Collection<BlockPos>,
			pendingInteractions: MutableCollection<BuildContext>,
			nowOrNothing: Boolean = false,
			builder: (BreakRequestBuilder.() -> Unit)? = null
		) = positions
			.associateWith { TargetState.Empty }
			.simulate()
			.breakRequest(pendingInteractions, nowOrNothing, builder)

		@BreakRequestDsl
		@JvmName("breakRequest2")
		context(automated: Automated)
		fun Collection<BuildResult>.breakRequest(
			pendingInteractions: MutableCollection<BuildContext>,
			nowOrNothing: Boolean = false,
			builder: (BreakRequestBuilder.() -> Unit)? = null
		) = asSequence()
			.breakRequest(pendingInteractions, nowOrNothing, builder)

		@BreakRequestDsl
		@JvmName("breakRequest3")
		context(automated: Automated)
		fun Sequence<BuildResult>.breakRequest(
			pendingInteractions: MutableCollection<BuildContext>,
			nowOrNothing: Boolean = false,
			builder: (BreakRequestBuilder.() -> Unit)? = null
		) = map { if (it is Dependent) it.lastDependency else it }
			.filterIsInstance<BreakResult.Break>()
			.sorted()
			.map { it.context }
			.toSet()
			.takeIf { it.isNotEmpty() }
			?.let { automated.breakRequest(it, pendingInteractions, nowOrNothing, builder) }

		@BreakRequestDsl
		@JvmName("breakRequest4")
		fun Automated.breakRequest(
			contexts: Collection<BreakContext>,
			pendingInteractions: MutableCollection<BuildContext>,
			nowOrNothing: Boolean = false,
			builder: (BreakRequestBuilder.() -> Unit)? = null
		) = BreakRequestBuilder(
			contexts, pendingInteractions, nowOrNothing, this
		).apply { builder?.invoke(this) }.build()

		@BreakRequestDsl
		private fun BreakRequestBuilder.build(): BreakRequest = request
	}
}
