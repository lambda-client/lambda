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

package com.lambda.task.tasks

import com.lambda.config.blocks.EatConfig.Companion.reasonEating
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.blueprint.PropagatingBlueprint
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.blueprint.toBlueprint
import com.lambda.interaction.construction.simulation.BuildGoal
import com.lambda.interaction.construction.simulation.Simulation.Companion.simulation
import com.lambda.interaction.construction.simulation.context.BuildContext
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.Contextual
import com.lambda.interaction.construction.simulation.result.Dependent
import com.lambda.interaction.construction.simulation.result.Drawable
import com.lambda.interaction.construction.simulation.result.Navigable
import com.lambda.interaction.construction.simulation.result.Resolvable
import com.lambda.interaction.construction.simulation.result.results.BreakResult
import com.lambda.interaction.construction.simulation.result.results.GenericResult
import com.lambda.interaction.construction.simulation.result.results.InteractResult
import com.lambda.interaction.construction.simulation.result.results.PreSimResult
import com.lambda.interaction.construction.simulation.sim
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.interaction.manager.managers.breaking.breakRequest
import com.lambda.interaction.manager.managers.interacting.interactRequest
import com.lambda.module.modules.client.Client
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.task.tasks.wrappers.softFail
import com.lambda.threading.runConcurrent
import com.lambda.threading.runSafe
import com.lambda.threading.runSafeAutomated
import com.lambda.threading.withSafeAutomated
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.EntityUtils.getClosestPointTo
import com.lambda.util.EntityUtils.getPositionsWithinBox
import com.lambda.util.FormattingUtils.format
import com.lambda.util.extension.Structure
import com.lambda.util.math.dist
import kotlinx.coroutines.Job
import kotlinx.coroutines.runBlocking
import net.minecraft.block.BlockState
import net.minecraft.entity.ItemEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.ChunkDeltaUpdateS2CPacket
import net.minecraft.network.packet.s2c.play.EntityPositionS2CPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.util.math.BlockPos
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue
import kotlin.coroutines.cancellation.CancellationException
import kotlin.math.sqrt

@Ta5kBuilder
fun Automated.build(
	finishOnDone: Boolean = true,
	collectDrops: Boolean = false,
	lifeMaintenance: Boolean = false,
	async: Boolean = false,
	buildResultFilter: SafeContext.(BuildResult) -> Boolean = { true },
	blueprint: () -> Blueprint
) = BuildTask(blueprint(), finishOnDone, collectDrops, lifeMaintenance, async, this, buildResultFilter)

@Ta5kBuilder
context(automated: Automated)
fun Structure.build(
	finishOnDone: Boolean = true,
	collectDrops: Boolean = false,
	lifeMaintenance: Boolean = false,
	async: Boolean = false,
	buildResultFilter: SafeContext.(BuildResult) -> Boolean = { true },
) = BuildTask(toBlueprint(), finishOnDone, collectDrops, lifeMaintenance, async, automated, buildResultFilter)

@Ta5kBuilder
context(automated: Automated)
fun Blueprint.build(
	finishOnDone: Boolean = true,
	collectDrops: Boolean = false,
	lifeMaintenance: Boolean = false,
	async: Boolean = false,
	buildResultFilter: SafeContext.(BuildResult) -> Boolean = { true },
) = BuildTask(this, finishOnDone, collectDrops, lifeMaintenance, async, automated, buildResultFilter)

@Ta5kBuilder
context(automated: Automated)
fun breakAndCollect(
	blockPos: BlockPos,
	finishOnDone: Boolean = true,
	lifeMaintenance: Boolean = false,
	async: Boolean = false,
	buildResultFilter: SafeContext.(BuildResult) -> Boolean = { true },
) = BuildTask(
	blockPos.toStructure(TargetState.Empty).toBlueprint(),
	finishOnDone, true, lifeMaintenance, async, automated, buildResultFilter
)

data class BuildOutcome(
	val structure: Structure,
	val pickups: List<PickedUpItem>,
	val pickupTypes: Set<Item>
)

data class BuildStatus(
	val structure: Structure,
	val pickedUpTypes: Set<Item>,
	val dropsPending: List<ItemStack>
)

class BuildTask @Ta5kBuilder internal constructor(
	private val blueprint: Blueprint,
	private val finishOnDone: Boolean,
	private val collectDrops: Boolean,
	private val lifeMaintenance: Boolean,
	private val async: Boolean,
	automated: Automated,
	private val buildResultFilter: SafeContext.(BuildResult) -> Boolean,
) : Task<BuildOutcome>(), Automated by automated {
	override val name: String get() = "Building $blueprint with ${(breaks / (age / 20.0 + 0.001)).format(precision = 1)} b/s ${(placements / (age / 20.0 + 0.001)).format(precision = 1)} p/s"

	private val pendingInteractions = ConcurrentLinkedQueue<BuildContext>()
	private val atMaxPendingInteractions
		get() = pendingInteractions.size >= buildConfig.maxPendingActions

	private var placements = 0
	private var breaks = 0
	private val dropsToCollect = mutableSetOf<ItemEntity>()
	private val pickups = mutableListOf<PickedUpItem>()
	private val pickupTypes = mutableSetOf<Item>()
	private val preTickCallbacks = mutableListOf<context(BuildTask) AutomatedSafeContext.(BuildStatus) -> Task<*>?>()

	private val onItemDrop: ((item: ItemEntity) -> Unit)?
		get() = if (collectDrops) { item ->
			dropsToCollect.add(item)
		} else null

	private var job: Job? = null
	private var results: Collection<BuildResult> = mutableSetOf()
	private var reSimPositions = Collections.synchronizedSet(hashSetOf<BlockPos>())
	private var viableResults: Sequence<BuildResult> = emptySequence()

	override fun SafeContext.onStart() {
		iteratePropagating()
	}

	@Ta5kBuilder
	fun onTickPre(callback: context(BuildTask) AutomatedSafeContext.(BuildStatus) -> Task<*>?) =
		apply { preTickCallbacks.add(callback) }

	init {
		onCompletion { BaritoneHandler.cancel() }

		listen<TickEvent.Post> {
			if (checkEmpty() || !async) return@listen
			runSafeAutomated {
				if (preSim()) return@listen
				startSimulation()
			}
		}

		listen<TickEvent.Pre> {
			runSafeAutomated {
				if (async) {
					if (job != null) {
						runBlocking { job?.join(); job = null }
						results = results.filter { it.pos !in reSimPositions } +
								blueprint.structure.filter { it.key in reSimPositions }.sim()
						reSimPositions.clear()
						setViableResults()
						processResults()
					}
					return@listen
				}

				if (preSim()) return@listen
				simulate()
				setViableResults()
				processResults()
			}
		}

		listen<PacketEvent.Receive.Pre> { event ->
			if (!async) return@listen
			runSafe {
				when (val packet = event.packet) {
					is PlayerPositionLookS2CPacket -> {
						job?.cancel(CancellationException("Player position moved, all simulations are compromised."))
						job = null
					}
					is BlockUpdateS2CPacket -> checkReSims(packet.pos, packet.state)
					is ChunkDeltaUpdateS2CPacket -> packet.visitUpdates { pos, state -> checkReSims(pos.toImmutable(), state) }
					is EntityPositionS2CPacket -> {
						val entity = world.getEntityById(packet.entityId) ?: return@listen
						val currentBox = entity.boundingBox
						val maxDist = buildConfig.blockReach + sqrt(3.0)
						val eyePos = player.eyePos
						val newBox = currentBox.offset(packet.change.position.subtract(entity.pos))
						if (currentBox.getClosestPointTo(eyePos) dist eyePos > maxDist &&
							newBox.getClosestPointTo(eyePos) dist eyePos > maxDist
						) return@listen
						reSimPositions.addAll(currentBox.getPositionsWithinBox() + newBox.getPositionsWithinBox())
					}
				}
			}
		}
	}

	private fun SafeContext.checkReSims(pos: BlockPos, state: BlockState) {
		val currentState = blockState(pos)
		if (currentState === state) return
		val pos = pos
		reSimPositions.addAll(
			arrayOf(
				pos,
				pos.up(),
				pos.down(),
				pos.north(),
				pos.south(),
				pos.east(),
				pos.west()
			)
		)
	}

	private fun AutomatedSafeContext.startSimulation() {
		if (job != null) return
		job = runConcurrent { simulate() }
	}

	private fun AutomatedSafeContext.preSim(): Boolean {
		if (runPreTick()) return true
		if (lifeMaintenance && withSafeAutomated { reasonEating() }.shouldEat()) {
			eat().start()
			return true
		}

		if (blueprint is TickingBlueprint) {
			blueprint.tick()
				?: run {
					failure("Failed to tick the ticking blueprint")
					return true
				}
		}

		return collectDrops()
	}

	private fun AutomatedSafeContext.runPreTick(): Boolean {
		if (preTickCallbacks.isEmpty()) return false
		val status =
			BuildStatus(
				structure = blueprint.structure,
				pickedUpTypes = pickupTypes,
				dropsPending = dropsToCollect.map { it.stack.copy() }
			)
		preTickCallbacks.forEach { callback ->
			val task = runCatching { callback(status) }.getOrNull()
			if (task != null) return true
		}
		return false
	}

	private fun SafeContext.currentPickups(): List<PickedUpItem> {
		if (pickups.isEmpty()) return emptyList()
		val latest = LinkedHashMap<SlotIdentity, ItemStack>()
		pickups.forEach { pickup -> pickup.slot?.let { latest[it] = pickup.stack } }
		return latest.entries
			.filter { (slot, stack) -> slot.holds(stack) }
			.map { (slot, stack) -> PickedUpItem(stack.copy(), slot) }
	}

	private fun AutomatedSafeContext.simulate() {
		results = blueprint.structure.sim()
	}

	private fun SafeContext.setViableResults() {
		viableResults = results
			.asSequence()
			.filter { result ->
				val finalResult = (result as? Dependent)?.lastDependency ?: result
				pendingInteractions.none {
					it.blockPos == finalResult.pos
				} && (finalResult !is Contextual || finalResult.context.canUse())
			}
			.filter { buildResultFilter(it) }
			.sorted()
	}

	private fun AutomatedSafeContext.processResults() {
		Client.drawables = results
			.filterIsInstance<Drawable>()
			.plus(pendingInteractions.toList())
			.toList()

		val bestResult = viableResults.firstOrNull() ?: return
		handleResult(bestResult, viableResults)
	}

	private fun checkEmpty(): Boolean {
		if (finishOnDone && blueprint.structure.isEmpty()) {
			failure("Structure is empty")
			return true
		}
		return false
	}

	private fun AutomatedSafeContext.handleResult(result: BuildResult, allResults: Sequence<BuildResult>) {
		if (result !is Dependent && result !is Contextual && pendingInteractions.isNotEmpty()) return

		when (result) {
			is PreSimResult.Done,
			is PreSimResult.Unbreakable,
			is PreSimResult.Restricted,
			is PreSimResult.NoPermission,
			is GenericResult.Ignored -> {
				if (iteratePropagating()) return
				if (finishOnDone) success(BuildOutcome(blueprint.structure, currentPickups(), pickupTypes))
			}

			is GenericResult.NotVisible,
			is InteractResult.NoIntegrity -> {
				if (!buildConfig.pathing) return
				val sim = blueprint.simulation()
				val goal = BuildGoal(sim, player.blockPos)
				BaritoneHandler.setGoalAndPath(goal)
			}

			is Navigable -> {
				if (buildConfig.pathing) BaritoneHandler.setGoalAndPath(result.goal ?: return)
			}

			is Contextual -> {
				if (atMaxPendingInteractions) return
				when (result) {
					is BreakResult.Break ->
						allResults.breakRequest(pendingInteractions) {
							onStop { breaks++ }
							onItemDrop?.let { onItemDrop ->
								onItemDrop { onItemDrop(it) }
							}
						}?.submit()

					is InteractResult.Interact -> {
						allResults.interactRequest(pendingInteractions, false) {
							onPlace { placements++ }
						}?.submit()
					}
				}
			}

			is Dependent -> handleResult(result.lastDependency, allResults)

			is Resolvable -> {
				result.resolve()
					.softFail()
					.start()
			}
		}
	}

	private fun AutomatedSafeContext.collectDrops(): Boolean {
		if (dropsToCollect.isEmpty()) return false
		if (pendingInteractions.isNotEmpty()) return true
		collectDrops(dropsToCollect)
			.onSuccess {
				pickups.addAll(it)
				pickupTypes.addAll(it.map { it.stack.item })
			}
			.start()
		return true
	}

	private fun iteratePropagating() =
		if (blueprint is PropagatingBlueprint) {
			blueprint.next() ?: failure("Failed to propagate the next blueprint")
			true
		} else false
}
