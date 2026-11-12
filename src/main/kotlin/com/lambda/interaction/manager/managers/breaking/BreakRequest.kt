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

package com.lambda.interaction.manager.managers.breaking

import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.simulation.context.BreakContext
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.results.BreakResult
import com.lambda.interaction.construction.simulation.sim
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.manager.ManagerDslMarker
import com.lambda.interaction.manager.Request
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isEmpty
import net.minecraft.entity.ItemEntity
import net.minecraft.util.math.BlockPos

@ManagerDslMarker
@JvmName("breakRequest1")
fun AutomatedSafeContext.breakRequest(
	positions: Collection<BlockPos>,
	pendingInteractions: MutableCollection<BuildContext>,
	nowOrNothing: Boolean = false,
	builder: (BreakRequestBuilder.() -> Unit)? = null
) = positions
	.associateWith { TargetState.Empty }
	.sim()
	.breakRequest(pendingInteractions, nowOrNothing, builder)

@ManagerDslMarker
@JvmName("breakRequest2")
context(automated: Automated)
fun Collection<BuildResult>.breakRequest(
	pendingInteractions: MutableCollection<BuildContext>,
	nowOrNothing: Boolean = false,
	builder: (BreakRequestBuilder.() -> Unit)? = null
) = asSequence()
	.breakRequest(pendingInteractions, nowOrNothing, builder)

@ManagerDslMarker
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

@ManagerDslMarker
@JvmName("breakRequest4")
fun Automated.breakRequest(
	contexts: Collection<BreakContext>,
	pendingInteractions: MutableCollection<BuildContext>,
	nowOrNothing: Boolean = false,
	builder: (BreakRequestBuilder.() -> Unit)? = null
) = BreakRequestBuilder(contexts, pendingInteractions, nowOrNothing, this)
	.apply { builder?.invoke(this) }
	.build()

/**
 * Contains the information necessary for initializing and continuing breaks within the [BreakManager].
 *
 * The callbacks can be used to keep track of the break progress.
 *
 * A private constructor is used to force use of the cleaner [ManagerDslMarker] builder. This is
 * accessed through the [breakRequest] method.
 *
 * @param contexts A collection of [BreakContext]'s gathered from the BuildSimulator.
 * @param pendingInteractions A mutable, concurrent list to store the pending actions.
 *
 * @see com.lambda.interaction.construction.simulation.BuildSimulator
 */
data class BreakRequest(
	val contexts: Collection<BreakContext>,
	val pendingInteractions: MutableCollection<BuildContext>,
	override val nowOrNothing: Boolean,
	private val automated: Automated,
	val onStarts: Collection<SafeContext.(BlockPos) -> Unit>,
	val onUpdates: Collection<SafeContext.(BlockPos) -> Unit>,
	val onStops: Collection<SafeContext.(BlockPos) -> Unit>,
	val onCancels: Collection<SafeContext.(BlockPos) -> Unit>,
	val onRejects: Collection<SafeContext.(BlockPos) -> Unit>,
	val onItemDrops: Collection<SafeContext.(ItemEntity) -> Unit>,
	val onReBreakStarts: Collection<SafeContext.(BlockPos) -> Unit>,
	val onReBreaks: Collection<SafeContext.(BlockPos) -> Unit>
) : Request(), Automated by automated {
	override val requestId = ++requestCount
	override val tickStageMask get() = breakConfig.tickStageMask

	override val done: Boolean
		get() = runSafe { contexts.all { blockState(it.blockPos).isEmpty } } == true

	@ManagerDslMarker
	override fun submit(queueIfMismatchedStage: Boolean) =
		BreakManager.request(this, queueIfMismatchedStage)

	companion object {
		var requestCount = 0
			private set
	}
}

@ManagerDslMarker
class BreakRequestBuilder internal constructor(
	private val contexts: Collection<BreakContext>,
	private val pendingInteractions: MutableCollection<BuildContext>,
	private val nowOrNothing: Boolean,
	private val automated: Automated
) {
	private val onStart = mutableListOf<SafeContext.(BlockPos) -> Unit>()
	private val onUpdate = mutableListOf<SafeContext.(BlockPos) -> Unit>()
	private val onStop = mutableListOf<SafeContext.(BlockPos) -> Unit>()
	private val onCancel = mutableListOf<SafeContext.(BlockPos) -> Unit>()
	private val onReject = mutableListOf<SafeContext.(BlockPos) -> Unit>()
	private val onItemDrop = mutableListOf<SafeContext.(ItemEntity) -> Unit>()
	private val onReBreakStart = mutableListOf<SafeContext.(BlockPos) -> Unit>()
	private val onReBreak = mutableListOf<SafeContext.(BlockPos) -> Unit>()

	fun onStart(callback: SafeContext.(BlockPos) -> Unit) {
		onStart += callback
	}

	fun onUpdate(callback: SafeContext.(BlockPos) -> Unit) {
		onUpdate += callback
	}

	fun onStop(callback: SafeContext.(BlockPos) -> Unit) {
		onStop += callback
	}

	fun onCancel(callback: SafeContext.(BlockPos) -> Unit) {
		onCancel += callback
	}

	fun onReject(callback: SafeContext.(BlockPos) -> Unit) {
		onReject += callback
	}

	fun onItemDrop(callback: SafeContext.(ItemEntity) -> Unit) {
		onItemDrop += callback
	}

	fun onReBreakStart(callback: SafeContext.(BlockPos) -> Unit) {
		onReBreakStart += callback
	}

	fun onReBreak(callback: SafeContext.(BlockPos) -> Unit) {
		onReBreak += callback
	}

	internal fun build() =
		BreakRequest(
			contexts,
			pendingInteractions,
			nowOrNothing,
			automated,
			onStart,
			onUpdate,
			onStop,
			onCancel,
			onReject,
			onItemDrop,
			onReBreakStart,
			onReBreak
		)
}
