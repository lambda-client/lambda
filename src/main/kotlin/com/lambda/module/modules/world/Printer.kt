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

package com.lambda.module.modules.world

import com.lambda.config.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.BaritoneManager
import com.lambda.interaction.construction.blueprint.TickingBlueprint
import com.lambda.interaction.construction.simulation.result.BuildResult
import com.lambda.interaction.construction.simulation.result.results.BreakResult
import com.lambda.interaction.construction.simulation.result.results.InteractResult
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.isNotEmpty
import com.lambda.util.Communication.logError
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.math.MathUtils.ceilToInt
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

object Printer : Module(
	name = "Printer",
	description = "Automatically prints schematics",
	tag = ModuleTag.WORLD
) {
	private val range by setting("Range", 5, 1..7, 1, description = "The range around the player to check for blocks to print")
	private val air by setting("Air", false, description = "Consider breaking blocks in the world that are air in the schematic.\nNote: Breaking can also be disabled in the Automation Config.")
	private val flattenMode by setting("Flatten Mode", FlattenMode.Standard, description = "Configures what blocks to break relative to the player's Y level")
	private val flattenModeApply by setting("Flatten Apply Mode", FlattenModeApply.BreakOnly, description = "Configures whether the flatten mode applies to breaking, placing, or both") { flattenMode != FlattenMode.None }
	private val baritoneSelection by setting("Baritone Selection", false, "Restricts block breaking and placing to your baritone selection")
	private val inverseSelection by setting("Inverse Selection", false, "Break and place blocks outside of the baritone selection and ignores blocks inside") { baritoneSelection }
	private val sneakLowersFlatten by setting("Sneak Lowers Flatten", false, "When enabled, sneaking will lower the flattening level by 1, allowing you to mine the block below you")

	private var buildTask: Task<*>? = null

	init {
		setDefaultAutomationConfig()

		onEnable {
			if (!litematicaAvailable()) {
				logError("Litematica is not installed!")
				disable()
				return@onEnable
			}
			buildTask = TickingBlueprint(onTick = {
				val schematicWorld = SchematicWorldHandler.getSchematicWorld() ?: return@TickingBlueprint emptyMap()
				BlockPos.iterateOutwards(player.blockPos, range, range, range)
					.map { it.blockPos }
					.asSequence()
					.filter { DataManager.getRenderLayerRange().isPositionWithinRange(it) && inSchematic(it) }
					.associateWith { TargetState.State(schematicWorld.getBlockState(it)) }
					.filter { air || !it.value.blockState.isAir }
			}, buildResultPredicate = { filterBuildResults(it) }).build(finishOnDone = false).run()
		}

		onDisable { buildTask?.cancel(); buildTask = null }
	}

	/**
	 * Checks a block position against the current settings to determine whether a build result at that position should be considered for building.
	 *
	 * @return true if the build result should be built, false if it should be ignored
	 */
	private fun SafeContext.filterBuildResults(buildResult: BuildResult): Boolean {
		if (baritoneSelection) {
			val inSelection = isInBaritoneSelection(buildResult.pos)
			if (inverseSelection && inSelection) return false
			if (!inverseSelection && !inSelection) return false
		}

		if (flattenModeApply == FlattenModeApply.Both) {
			return isInFlatten(buildResult.pos)
		}
		return when (buildResult) {
			is InteractResult.Interact -> {
				if (flattenModeApply != FlattenModeApply.PlaceOnly) {
					return true
				}
				isInFlatten(buildResult.pos)
			}
			is BreakResult.Break -> {
				if (flattenModeApply != FlattenModeApply.BreakOnly) {
					return true
				}
				isInFlatten(buildResult.pos)
			}
			else -> true
		}
	}

	private fun inSchematic(pos: BlockPos): Boolean {
		val placementManager = DataManager.getSchematicPlacementManager()
		return placementManager?.getAllPlacementsTouchingChunk(pos)?.any {
			it.placement.isEnabled && it.bb.containsPos(pos)
		} ?: false
	}

	private fun litematicaAvailable(): Boolean = runCatching {
		Class.forName("fi.dy.masa.litematica.Litematica")
		true
	}.getOrDefault(false)

	private fun SafeContext.isInFlatten(pos: BlockPos): Boolean {
		if (flattenMode == FlattenMode.None) return true
		if (flattenMode == FlattenMode.Staircase) {
			val up = pos.up()
			if ((blockState(up).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up)))
				|| (blockState(up.east()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.east())))
				|| (blockState(up.south()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.south())))
				|| (blockState(up.west()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.west())))
				|| (blockState(up.north()).isNotEmpty && (!baritoneSelection || isInBaritoneSelection(up.north())))
			) {
				return false
			}
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

	private enum class FlattenModeApply(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		BreakOnly("Break Only", "Only applies flattening logic to blocks that are being broken"),
		PlaceOnly("Place Only", "Only applies flattening logic to blocks that are being placed"),
		Both("Both", "Applies flattening logic to all blocks, whether being placed or broken")
	}
}