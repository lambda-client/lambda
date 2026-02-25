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

package com.lambda.module.modules.player

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.math.MathUtils.ceilToInt
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object Nuker : Module(
	name = "Nuker",
	description = "Breaks blocks around you",
	tag = ModuleTag.PLAYER,
) {
	private val height by setting("Height", 6, 1..8, 1)
	private val width by setting("Width", 6, 1..8, 1)
	private val flattenMode by setting("Flatten Mode", FlattenMode.Standard)
	private val directionalDig by setting("Directional Dig", DigDirection.None)
	private val onGround by setting("On Ground", false, "Only break blocks when the player is standing on ground")
	private val fillFluids by setting("Fill Fluids", false, "Removes liquids by filling them in before breaking")
	private val fillFloor by setting("Fill Floor", false)
	private val baritoneSelection by setting("Baritone Selection", false, "Restricts nuker to your baritone selection")
	private val inverseSelection by setting("Inverse Selection", false, "Breaks blocks outside of the baritone selection and ignores blocks inside") { baritoneSelection }
	private val sneakLowersFlatten by setting("Sneak Lowers Flatten", false)

	private var task: Task<*>? = null

	init {
		setDefaultAutomationConfig()

		onEnable {
			task = tickingBlueprint {
				if (onGround && !player.isOnGround) return@tickingBlueprint emptyMap()

				val selection = BlockPos.iterateOutwards(player.blockPos, width, height, width)
					.asSequence()
					.map { it.blockPos }
					.filter { !world.isAir(it) }
					.filter { flattenMode == FlattenMode.None || isInFlatten(it) }
					.filter { isWithinDigDirection(it) }
					.filter { isInBaritoneSelection(it) == !inverseSelection }
					.associateWith { if (fillFluids) TargetState.Air else TargetState.Empty }

				if (fillFloor) {
					val floor = BlockPos.iterateOutwards(player.blockPos.down(), width, 0, width)
						.map { it.blockPos }
						.associateWith { TargetState.Solid(setOf(Blocks.MAGMA_BLOCK)) }
					return@tickingBlueprint selection + floor
				}

				selection
			}.build(finishOnDone = false)
				.run()
		}

		onDisable {
			task?.cancel()
		}
	}

	private fun SafeContext.isInFlatten(pos: BlockPos): Boolean {
		if (flattenMode == FlattenMode.Staircase) {
			val up = pos.up()
			if ((blockState(up).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up)))
				|| (blockState(up.east()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.east())))
				|| (blockState(up.south()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.south())))
				|| (blockState(up.west()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.west())))
				|| (blockState(up.north()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.north())))
			)  { return false }
		}

		val flattenY = player.y.ceilToInt()
		val playerPos = player.blockPos
		val flattenLevel =
			if (sneakLowersFlatten && player.isSneaking) flattenY - 1
			else flattenY

		if (!flattenMode.isSmart && pos.y < flattenLevel)
			return false

		if (pos == player.supportingBlockPos) return false

		val playerLookDir = player.horizontalFacing
		val smartFlattenDir =
			if (flattenMode == FlattenMode.Smart) playerLookDir
			else playerLookDir?.opposite

		if (pos.y >= flattenLevel) return true

		val zeroedPos = pos.add(-playerPos.x, -flattenY, -playerPos.z)

		return (zeroedPos.x < 0 && smartFlattenDir == Direction.EAST)
				|| (zeroedPos.z < 0 && smartFlattenDir == Direction.SOUTH)
				|| (zeroedPos.x > 0 && smartFlattenDir == Direction.WEST)
				|| (zeroedPos.z > 0 && smartFlattenDir == Direction.NORTH)
	}

	private fun SafeContext.isWithinDigDirection(pos: BlockPos): Boolean {
		val playerPos = player.blockPos
		return when (directionalDig) {
			DigDirection.None -> true
			DigDirection.East -> playerPos.x <= pos.x
			DigDirection.West -> playerPos.x >= pos.x
			DigDirection.North -> playerPos.z >= pos.z
			DigDirection.South -> playerPos.z <= pos.z
		}
	}

	private fun isInBaritoneSelection(pos: BlockPos) =
		if (!baritoneSelection) true
		else BaritoneManager.primary?.selectionManager?.selections?.any {
			val min = it.min()
			val max = it.max()
			pos.x >= min.x && pos.x <= max.x
					&& pos.y >= min.y && pos.y <= max.y
					&& pos.z >= min.z && pos.z <= max.z
		} ?: false

	private enum class FlattenMode {
		None,
		Standard,
		Smart,
		ReverseSmart,
		Staircase;

		val isSmart
			get() = this == Smart || this == ReverseSmart
	}

	private enum class DigDirection {
		None,
		East,
		South,
		West,
		North
	}
}