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

package com.lambda.util

import com.lambda.Lambda
import com.lambda.Lambda.mc
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.loot.LootTable
import net.minecraft.loot.context.LootWorldContext
import net.minecraft.registry.Registries
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.random.Random
import net.minecraft.util.math.random.RandomSplitter
import java.util.*
import kotlin.math.nextDown

object BlockDropPredictor {
	private const val MAX_OUTCOME_PATHS = 4096
	private const val MAX_ENUMERATED_INT_BOUND = 4096

	data class DropPrediction(
		val items: Set<Item>,
		val guaranteesDrop: Boolean,
		val isComplete: Boolean = true
	) {
		companion object {
			val EMPTY = DropPrediction(emptySet(), false)
			val INCOMPLETE = DropPrediction(emptySet(), guaranteesDrop = false, isComplete = false)
		}
	}

	fun predictDrops(block: Block, tool: ItemStack): DropPrediction = predictDrops(block.defaultState, tool)

	fun predictDrops(
		state: BlockState,
		tool: ItemStack,
		pos: BlockPos = BlockPos.ORIGIN,
		entity: Entity? = mc.player,
		blockEntity: BlockEntity? = null,
		luck: Float = (entity as? PlayerEntity)?.luck ?: 0f
	): DropPrediction {
		if (state.isToolRequired && !tool.isSuitableFor(state)) return DropPrediction.EMPTY

		val clientWorld = mc.world ?: return DropPrediction.INCOMPLETE
		val lootTableKey = state.block.lootTableKey.orElse(null) ?: return DropPrediction.EMPTY

		return try {
			val world = DummyLootServerWorld.create(clientWorld)
			val table = world.server.reloadableRegistries.getLootTable(lootTableKey)
			if (table === LootTable.EMPTY) return DropPrediction.EMPTY

			val worldContext =
				world.createBlockLootContext(
					state = state,
					pos = pos,
					tool = tool,
					entity = entity,
					blockEntity = blockEntity,
					luck = luck
				)
			exploreOutcomes(table, worldContext)
		} catch (exception: Exception) {
			Lambda.LOG.warn("Couldn't predict drops for {}", Registries.BLOCK.getId(state.block), exception)
			DropPrediction.INCOMPLETE
		}
	}

	private fun exploreOutcomes(table: LootTable, worldContext: LootWorldContext): DropPrediction {
		val pendingPaths = ArrayDeque<List<Int>>()
		val scheduledPaths = mutableSetOf<List<Int>>()
		pendingPaths.addLast(emptyList())
		scheduledPaths.add(emptyList())

		val items = mutableSetOf<Item>()
		var complete = true
		var everyPathDropped = true
		var evaluatedPaths = 0

		while (pendingPaths.isNotEmpty()) {
			if (evaluatedPaths >= MAX_OUTCOME_PATHS) {
				complete = false
				break
			}

			val path = pendingPaths.removeFirst()
			val random = OutcomePathRandom(path)
			val drops = table.generateLoot(worldContext, random)
			evaluatedPaths++
			complete = complete && random.isComplete

			val nonEmptyDrops = drops.filterNot(ItemStack::isEmpty)
			nonEmptyDrops.forEach { items += it.item }
			if (nonEmptyDrops.isEmpty()) everyPathDropped = false

			val decisions = random.decisions
			decisions.indices.forEach { decisionIndex ->
				val currentChoice = random.choices[decisionIndex]
				repeat(decisions[decisionIndex]) { choice ->
					if (choice == currentChoice) return@repeat
					val branch = random.choices.take(decisionIndex) + choice
					if (scheduledPaths.add(branch)) pendingPaths.addLast(branch)
				}
			}
		}

		return DropPrediction(
			items = items,
			guaranteesDrop = complete && items.isNotEmpty() && everyPathDropped,
			isComplete = complete
		)
	}

	private class OutcomePathRandom(private val path: List<Int>) : Random {
		private val floatOutcomes = listOf(0f, 1f.nextDown())
		private val doubleOutcomes = listOf(0.0, 1.0.nextDown())

		val choices = mutableListOf<Int>()
		val decisions = mutableListOf<Int>()
		var isComplete = true
			private set

		private fun <T> choose(outcomes: List<T>): T {
			check(outcomes.isNotEmpty()) { "A loot random decision had no possible values" }
			val index = choices.size
			val selected = path.getOrNull(index)?.coerceIn(outcomes.indices) ?: 0
			choices += selected
			decisions += outcomes.size
			return outcomes[selected]
		}

		private fun <T> unsupported(outcomes: List<T>): T {
			isComplete = false
			return choose(outcomes)
		}

		override fun split(): Random {
			isComplete = false
			return this
		}

		override fun nextSplitter(): RandomSplitter {
			isComplete = false
			return object : RandomSplitter {
				override fun split(seed: String): Random = this@OutcomePathRandom
				override fun split(seed: Long): Random = this@OutcomePathRandom
				override fun split(x: Int, y: Int, z: Int): Random = this@OutcomePathRandom
				override fun addDebugInfo(builder: StringBuilder) {
					builder.append("loot outcome exploration")
				}
			}
		}

		override fun setSeed(seed: Long) {
			isComplete = false
		}

		override fun nextInt(): Int = unsupported(listOf(Int.MIN_VALUE, -1, 0, 1, Int.MAX_VALUE))

		override fun nextInt(bound: Int): Int {
			require(bound > 0) { "bound must be positive" }
			if (bound <= MAX_ENUMERATED_INT_BOUND) return choose((0 until bound).toList())
			return unsupported(listOf(0, bound / 2, bound - 1).distinct())
		}

		override fun nextLong(): Long = unsupported(listOf(Long.MIN_VALUE, -1L, 0L, 1L, Long.MAX_VALUE))
		override fun nextBoolean(): Boolean = choose(listOf(false, true))
		override fun nextFloat(): Float = choose(floatOutcomes)
		override fun nextDouble(): Double = choose(doubleOutcomes)
		override fun nextGaussian(): Double = unsupported(listOf(-8.0, 0.0, 8.0))
	}
}
