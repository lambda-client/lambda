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

import com.lambda.config.blocks.ItemPickupConfig
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.context.SafeContext
import com.lambda.event.events.InventoryEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.toBlueprint
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.interaction.container.SlotIdentity
import com.lambda.interaction.manager.managers.breaking.BreakRequestBuilder
import com.lambda.interaction.manager.managers.interacting.InteractRequestBuilder
import com.lambda.task.Task
import com.lambda.task.TaskMarker
import com.lambda.task.tasks.wrappers.softFail
import com.lambda.util.extension.Structure
import com.lambda.util.item.ItemStackUtils.itemAndComponentsEqual
import net.minecraft.entity.ItemEntity
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

@TaskMarker
context(automated: Automated)
fun breakAndCollect(
	positions: Collection<BlockPos>,
	finishOnDone: Boolean = true,
	async: Boolean = false,
	finishOnFull: Boolean = false,
	dropPickupConfig: ItemPickupConfig = ItemPickupConfig.DEFAULT,
	breakCallbacks: (BreakRequestBuilder.() -> Unit)? = null,
	interactCallbacks: (InteractRequestBuilder.() -> Unit)? = null,
	buildResultFilter: SafeContext.(BuildResult) -> Boolean = { true },
) = BreakAndCollectTask(
	positions,
	finishOnDone,
	async,
	finishOnFull,
	dropPickupConfig,
	breakCallbacks,
	interactCallbacks,
	buildResultFilter,
	automated
)

data class BreakAndCollectStatus(
	val structure: Structure,
	val drops: List<ItemEntity>,
	val pickups: List<PickedUpItem>,
	val inventoryFull: Boolean,
	val pickupDue: Boolean,
)

data class BreakAndCollectOutcome(
	val structure: Structure,
	val pickups: List<PickedUpItem>,
	val remainingDrops: List<ItemEntity> = emptyList(),
)

class BreakAndCollectTask @TaskMarker internal constructor(
	private val positions: Collection<BlockPos>,
	private val finishOnDone: Boolean = true,
	private val async: Boolean = false,
	private val finishOnFull: Boolean = false,
	private val dropPickupConfig: ItemPickupConfig = ItemPickupConfig.DEFAULT,
	private val breakCallbacks: (BreakRequestBuilder.() -> Unit)? = null,
	private val interactCallbacks: (InteractRequestBuilder.() -> Unit)? = null,
	private val buildResultFilter: SafeContext.(BuildResult) -> Boolean = { true },
	private val automated: Automated,
) : Task<BreakAndCollectOutcome>(), Automated by automated {
	override val name =
		positions.singleOrNull()?.let { "Breaking and collecting at ${it.toShortString()}" }
			?: "Breaking and collecting ${positions.size} blocks"

	private val drops = mutableSetOf<ItemEntity>()
	private val sources = mutableSetOf<ItemEntity>()
	private val pickups = linkedMapOf<SlotIdentity, ItemStack>()
	private val preTickCallbacks =
		mutableListOf<context(BuildTask) AutomatedSafeContext.(BreakAndCollectStatus) -> Task<*>?>()

	@TaskMarker
	fun onTickPre(callback: context(BuildTask) AutomatedSafeContext.(BreakAndCollectStatus) -> Task<*>?) =
		apply { preTickCallbacks.add(callback) }

	init {
		listen<InventoryEvent.Pickup>(alwaysListen = true) { event ->
			if (event.source !in sources) return@listen
			event.slots.forEach { (slot, landed) ->
				val tracked = pickups[slot]
				if (tracked != null && tracked.itemAndComponentsEqual(landed.stack)) tracked.increment(landed.amount)
				else pickups[slot] = landed.stack.copyWithCount(landed.amount)
			}
		}

		listen<InventoryEvent.PickupMerged>(alwaysListen = true) { event ->
			if (event.source !in sources) return@listen
			sources += event.target
			if (drops.remove(event.source)) drops += event.target
		}
	}

	override fun SafeContext.onStart() {
		if (positions.isEmpty()) {
			complete(emptyMap())
			return
		}

		positions
			.associateWith { TargetState.Empty }
			.toBlueprint()
			.build(
				finishOnDone = finishOnDone,
				async = async,
				breakCallbacks = {
					breakCallbacks?.invoke(this)
					onItemDrop {
						drops += it
						sources += it
					}
				},
				interactCallbacks = interactCallbacks,
				buildResultFilter = buildResultFilter,
			)
			.onTickPre { structure ->
				drops.removeIf { it.isRemoved }
				validatePickups()

				val inventoryFull = isInventoryFull()
				val status =
					BreakAndCollectStatus(
						structure = structure,
						drops = drops.toList(),
						pickups = pickedUp(),
						inventoryFull = inventoryFull,
						pickupDue = dropPickupConfig.shouldPickup(drops),
					)

				preTickCallbacks.forEach { callback ->
					val task = runCatching { callback(status) }.getOrNull()
					if (task != null) return@onTickPre task
				}

				when {
					inventoryFull && finishOnFull -> {
						complete(structure)
						null
					}
					inventoryFull || drops.isEmpty() || !status.pickupDue -> null
					else -> with(this) { collect().start() }
				}
			}
			.onSuccess { structure ->
				drops.removeIf { it.isRemoved }

				if (drops.isEmpty() || !dropPickupConfig.pickupDrops || isInventoryFull()) {
					complete(structure)
					return@onSuccess
				}

				collect()
					.onSuccess { complete(structure) }
					.execute(this@BreakAndCollectTask)
			}
			.start()
	}

	private fun Automated.collect() =
		pickupItems(drops, false)
			.softFail()

	private fun validatePickups() =
		pickups.entries.removeIf { (slot, tracked) ->
			val live = slot.resolveStack()
			if (live == null || !live.itemAndComponentsEqual(tracked)) return@removeIf true
			tracked.count = minOf(tracked.count, live.count)
			false
		}

	private fun pickedUp() = pickups.map { (slot, stack) -> PickedUpItem(stack.copy(), slot) }

	private fun SafeContext.complete(structure: Structure) {
		validatePickups()
		success(BreakAndCollectOutcome(structure, pickedUp(), drops.filter { !it.isRemoved }))
	}

	private fun SafeContext.isInventoryFull(): Boolean {
		val live = drops.filter { !it.isRemoved }
		return live.isNotEmpty() && live.none { canPickup(it.stack) }
	}
}
