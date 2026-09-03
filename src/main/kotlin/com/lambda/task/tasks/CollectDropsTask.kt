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

import baritone.api.pathing.goals.GoalBlock
import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.handler.handlers.BaritoneHandler
import com.lambda.interaction.manager.managers.inventory.InvRequestBuilder.Companion.inventoryRequest
import com.lambda.task.Task
import com.lambda.task.Task.Ta5kBuilder
import com.lambda.threading.runSafeAutomated
import com.lambda.util.extension.playerSlots
import net.minecraft.entity.ItemEntity

@Ta5kBuilder
context(automated: Automated)
fun collectDrops(drops: MutableSet<ItemEntity>) = CollectDropsTask(drops, automated)

/**
 * A task that collects a set of item entity drops by walking to them.
 *
 * Items are considered collected when they despawn from the world (picked up by the player).
 * If inventory is full, disposable items are thrown to make space.
 *
 * Succeeds when all drops have been collected.
 */
class CollectDropsTask @Ta5kBuilder internal constructor(
	private val drops: MutableSet<ItemEntity>,
	automated: Automated
) : Task<Unit>(), Automated by automated {
	override val name get() = "Collecting ${drops.size} item drop(s)"

	override fun SafeContext.onStart() {
		listen<TickEvent.Pre> {
			runSafeAutomated {
				val target =
					drops.firstOrNull() ?: run {
						BaritoneHandler.cancel()
						success()
						return@listen
					}

				if (!world.entities.contains(target)) {
					drops.remove(target)
					BaritoneHandler.cancel()
					return@listen
				}

				if (HotbarAndInventoryContainer.stacks.none { it.isEmpty }) {
					val stackToThrow =
						player.currentScreenHandler.playerSlots.firstOrNull {
							it.stack.item in inventoryConfig.disposables
						} ?: run {
							failure("Inventory is full and no disposable items to throw")
							return@listen
						}
					inventoryRequest {
						throwStack(stackToThrow.id)
					}.submit()
					return@listen
				}

				BaritoneHandler.setGoalAndPath(GoalBlock(target.blockPos))
			}
		}
	}

	override fun SafeContext.onCancel() {
		BaritoneHandler.cancel()
	}
}
