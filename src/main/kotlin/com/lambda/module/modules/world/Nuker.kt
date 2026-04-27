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
import com.lambda.config.applyEdits
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.RootTask.run
import com.lambda.task.Task
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.PlayerBuildLayerUtils.FlattenMode
import com.lambda.util.PlayerBuildLayerUtils.isInBaritoneSelection
import com.lambda.util.PlayerBuildLayerUtils.isInFlatten
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos

@Suppress("unused")
object Nuker : Module(
	name = "Nuker",
	description = "Breaks blocks around you",
	tag = ModuleTag.WORLD,
	autoDisable = true
) {
	private val height by setting("Height", 6, 1..8, 1)
	private val width by setting("Width", 6, 1..8, 1)
	private val flattenMode by setting("Flatten Mode", FlattenMode.Standard)
	private val directionalDig by setting("Directional Dig", DigDirection.None)
	private val onGround by setting("On Ground", false, "Only break blocks when the player is standing on ground")
	private val fillFloor by setting("Fill Floor", false)
	private val baritoneSelection by setting("Baritone Selection", false, "Restricts nuker to your baritone selection")
	private val inverseSelection by setting("Inverse Selection", false, "Breaks blocks outside of the baritone selection and ignores blocks inside") { baritoneSelection }
	private val sneakLowersFlatten by setting("Sneak Lowers Flatten", false)

	private var task: Task<*>? = null

	init {
		setDefaultAutomationConfig {
			applyEdits {
				buildConfig.apply {
					editTyped(::pathing, ::stayInRange) { defaultValue(false) }
				}
			}
		}

		onEnable {
			task = tickingBlueprint {
				if (onGround && !player.isOnGround) return@tickingBlueprint emptyMap()

				val selection = BlockPos.iterateOutwards(player.blockPos, width, height, width)
					.map { it.blockPos }
					.asSequence()
					.filter { !world.isAir(it) }
					.filter { !baritoneSelection || isInBaritoneSelection(it) != inverseSelection }
					.filter { isInFlatten(it, flattenMode, sneakLowersFlatten, baritoneSelection, inverseSelection) }
					.filter { isWithinDigDirection(it) }
					.associateWith { if (breakConfig.fillFluids) TargetState.Air else TargetState.Empty }

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

	private enum class DigDirection {
		None,
		East,
		South,
		West,
		North
	}
}