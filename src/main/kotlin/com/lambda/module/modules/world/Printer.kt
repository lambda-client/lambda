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
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
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
import com.lambda.util.Communication.logError
import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import com.lambda.util.PlayerBuildLayerUtils.isInBaritoneSelection
import com.lambda.util.PlayerBuildLayerUtils.isInFlatten
import com.lambda.util.PlayerBuildLayerUtils.FlattenMode
import com.lambda.util.PlayerBuildLayerUtils.inSchematic
import fi.dy.masa.litematica.data.DataManager
import fi.dy.masa.litematica.world.SchematicWorldHandler
import net.minecraft.util.math.BlockPos

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
			buildTask = tickingBlueprint {
				val schematicWorld = SchematicWorldHandler.getSchematicWorld() ?: return@tickingBlueprint emptyMap()
				BlockPos.iterateOutwards(player.blockPos, range, range, range)
					.map { it.blockPos }
					.asSequence()
					.filter { pos -> !baritoneSelection || isInBaritoneSelection(pos) != inverseSelection }
					.filter { DataManager.getRenderLayerRange().isPositionWithinRange(it) && inSchematic(it) }
					.associateWith { TargetState.State(schematicWorld.getBlockState(it)) }
					.filter { air || !it.value.blockState.isAir }
			}.build(finishOnDone = false, buildResultFilter = { filterBuildResults(it) }).run()
		}

		onDisable { buildTask?.cancel(); buildTask = null }
	}

	/**
	 * Checks a block position against the current settings to determine whether a build result at that position should be considered for building.
	 *
	 * @return true if the build result should be built, false if it should be ignored
	 */
	private fun SafeContext.filterBuildResults(buildResult: BuildResult): Boolean {
		return if (buildResult is InteractResult && !flattenModeApply.placing ||
			buildResult is BreakResult && !flattenModeApply.breaking) true
		else isInFlatten(buildResult.pos, flattenMode, sneakLowersFlatten, baritoneSelection, inverseSelection)
	}

	private fun litematicaAvailable(): Boolean = runCatching {
		Class.forName("fi.dy.masa.litematica.Litematica")
		true
	}.getOrDefault(false)

	private enum class FlattenModeApply(
		override val displayName: String,
		val breaking: Boolean,
		val placing: Boolean,
		override val description: String
	) : NamedEnum, Describable {
		BreakOnly("Break Only", true, false, "Only applies flattening logic to blocks that are being broken"),
		InteractOnly("Place/Interact Only", false, true, "Only applies flattening logic to blocks that are being placed"),
		Both("Both", true, true, "Applies flattening logic to all blocks, whether being placed or broken")
	}
}