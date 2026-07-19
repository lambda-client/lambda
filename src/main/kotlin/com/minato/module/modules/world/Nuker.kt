
package com.minato.module.modules.world

import com.minato.config.ConfigEditor.editTypedSettings
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.minato.interaction.construction.verify.TargetState
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask.run
import com.minato.task.Task
import com.minato.task.tasks.BuildTask.Companion.build
import com.minato.util.BlockUtils.blockPos
import com.minato.util.PlayerBuildLayerUtils.FlattenMode
import com.minato.util.PlayerBuildLayerUtils.isInFlatten
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
	private val sneakLowersFlatten by setting("Sneak Lowers Flatten", false)
	private val async by setting("Async", true, "Allows the simulation the 50 milliseconds between ticks where nothing changes to avoid lag. This causes a 1 tick wait between starting the module, and it performing actions")
		.onValueChange { _, _ -> if (buildTask != null) startBuildTask() }

	private var buildTask: Task<*>? = null

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
				.filter { isInFlatten(it, flattenMode, sneakLowersFlatten) }
				.filter { isWithinDigDirection(it) }
				.associateWith { if (breakConfig.fillFluids) TargetState.Air else TargetState.Empty }

			if (fillFloor) {
				val floor = BlockPos.iterateOutwards(player.blockPos.down(), width, 0, width)
					.map { it.blockPos }
					.associateWith { TargetState.Solid(setOf(Blocks.MAGMA_BLOCK)) }
				return@tickingBlueprint selection + floor
			}

			selection
		}.build(finishOnDone = false, async = async)
			.run()
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