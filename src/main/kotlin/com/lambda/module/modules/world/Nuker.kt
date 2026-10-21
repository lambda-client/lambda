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

import com.lambda.config.automation.setDefaultAutomationConfig
import com.lambda.config.blocks.BreakConfig
import com.lambda.config.editTypedSettings
import com.lambda.config.entries.onValueChange
import com.lambda.config.settings.complex.Bind
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.event.events.PlayerEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.ModuleTag
import com.lambda.task.Task
import com.lambda.task.start
import com.lambda.task.tasks.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.CommunicationUtils.info
import com.lambda.util.InputUtils.isSatisfied
import com.lambda.util.PlayerBuildLayerUtils.FlattenMode
import com.lambda.util.PlayerBuildLayerUtils.isInBaritoneSelection
import com.lambda.util.PlayerBuildLayerUtils.isInFlatten
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import org.lwjgl.glfw.GLFW

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
	private val selective by setting("Selective", false, description = "Click on a block to nuke that type of block")
	private val selectAddKey by setting("Select Additional Keybind", Bind(GLFW.GLFW_KEY_LEFT_CONTROL, 0), description = "Hold down this key to add more blocks to the selective block list", visibility = { selective })
	private val onGround by setting("On Ground", false, "Only break blocks when the player is standing on ground")
	private val fillFloor by setting("Fill Floor", false)
	private val baritoneSelection by setting("Baritone Selection", false, "Restricts nuker to your baritone selection")
	private val inverseSelection by setting("Inverse Selection", false, "Breaks blocks outside of the baritone selection and ignores blocks inside") { baritoneSelection }
	private val sneakLowersFlatten by setting("Sneak Lowers Flatten", false)
	private val async by setting("Async", true, "Allows the simulation the 50 milliseconds between ticks where nothing changes to avoid lag. This causes a 1 tick wait between starting the module, and it performing actions")
		.onValueChange { _, _ -> if (buildTask != null) startBuildTask() }

	private var buildTask: Task<*>? = null
	private val selectedBlocks = mutableSetOf<Block>()

	init {
		setDefaultAutomationConfig()
			.withEdits {
				buildConfig.apply {
					editTypedSettings(::pathing) { defaultValue(false) }
				}
			}

		onEnable {
			startBuildTask()
		}

		onDisable {
			buildTask?.cancel()
			buildTask = null
			selectedBlocks.clear()
		}

		listen<PlayerEvent.Attack.Block>(priority = { 69420 }) {
			if (!selective) return@listen

			val selected = blockState(it.pos).block
			if (breakConfig.whitelistMode == BreakConfig.WhitelistMode.Blacklist && breakConfig.blacklist.contains(selected)) {
				it.cancel()
				this@Nuker.info("${selected.name.string} is blacklisted in the break config!")
				return@listen
			} else if (breakConfig.whitelistMode == BreakConfig.WhitelistMode.Whitelist && !breakConfig.whitelist.contains(selected)) {
				it.cancel()
				this@Nuker.info("${selected.name.string} is not whitelisted in the break config!")
				return@listen
			}

			if (selectAddKey.isSatisfied()) {
				if (selectedBlocks.add(selected)) {
					val text = (if (selectedBlocks.size > 1) "Selected blocks:" else "Selected block:") +
								selectedBlocks.joinToString(",") { block -> " ${block.name.string}" } + "."

					this@Nuker.info(text)
				}
			} else if (!selectedBlocks.contains(selected) || selectedBlocks.size > 1) {
				selectedBlocks.clear()
				selectedBlocks.add(selected)
				this@Nuker.info("Selected block: ${selected.name.string}.")
			}
		}
	}

	private fun startBuildTask() {
		buildTask?.cancel()
		buildTask = tickingBlueprint {
			if (onGround && !player.isOnGround) return@tickingBlueprint emptyMap()

			val selection = BlockPos.iterateOutwards(player.blockPos, width, height, width)
				.map { it.blockPos }
				.asSequence()
				.filter { !world.isAir(it) }
				.filter { !baritoneSelection || isInBaritoneSelection(it) != inverseSelection }
				.filter { isInFlatten(it, flattenMode, sneakLowersFlatten, baritoneSelection, inverseSelection) }
				.filter { isWithinDigDirection(it) }
				.filter { isValidTarget(it) }
				.associateWith { if (breakConfig.fillFluids) TargetState.Air else TargetState.Empty }

			if (fillFloor) {
				val floor = BlockPos.iterateOutwards(player.blockPos.down(), width, 0, width)
					.map { it.blockPos }
					.associateWith { TargetState.Solid(setOf(Blocks.MAGMA_BLOCK)) }
				return@tickingBlueprint selection + floor
			}

			selection
		}.build(finishOnDone = false, async = async)
			.start()
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

	private fun SafeContext.isValidTarget(pos: BlockPos): Boolean =
		!selective || blockState(pos).block in selectedBlocks

	private enum class DigDirection {
		None,
		East,
		South,
		West,
		North
	}
}