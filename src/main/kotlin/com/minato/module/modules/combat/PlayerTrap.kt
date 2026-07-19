
package com.minato.module.modules.combat

import com.minato.config.ConfigEditor.editTypedSettings
import com.minato.config.ConfigEditor.hideBlock
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.minato.interaction.construction.verify.TargetState
import com.minato.interaction.handlers.FriendHandler.isFriend
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask.run
import com.minato.task.Task
import com.minato.task.tasks.BuildTask.Companion.build
import com.minato.util.BlockUtils.blockState
import com.minato.util.extension.shrinkByEpsilon
import com.minato.util.item.ItemUtils.block
import com.minato.util.math.flooredBlockPos
import com.minato.util.player.SlotUtils.hotbarAndInventoryStacks
import com.minato.util.world.entitySearch
import net.minecraft.block.Blocks
import net.minecraft.client.network.OtherClientPlayerEntity
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.item.BlockItem
import net.minecraft.util.math.BlockPos
import kotlin.jvm.optionals.getOrNull

object PlayerTrap : Module(
	name = "PlayerTrap",
	description = "Surrounds players with any given block",
	tag = ModuleTag.COMBAT
) {
	private val blocks by setting("Blocks", setOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.CRYING_OBSIDIAN))
	private val friends by setting("Friends", false)
	private val self by setting("Self", false)

	private var task: Task<*>? = null

	init {
		setDefaultAutomationConfig()
			.withEdits {
				buildConfig.apply {
					editTypedSettings(
						::pathing,
						::spleefEntities,
						::collectDrops
					) { defaultValue(false); hide() }
				}
				hideBlock(::eatConfig)
			}

		onEnable {
			task = tickingBlueprint {
				val block = player.hotbarAndInventoryStacks.firstOrNull {
					it.item is BlockItem && blocks.contains(it.item.block)
				}?.item?.block ?: return@tickingBlueprint emptyMap()
				val targetPlayer = if (self) player
				else entitySearch<OtherClientPlayerEntity>(
					buildConfig.blockReach,
					player.eyePos.flooredBlockPos
				).firstOrNull { friends || !isFriend(it.gameProfile) } ?: return@tickingBlueprint emptyMap()
				getTrapPositions(targetPlayer).associateWith { TargetState.Block(block) }
			}.build(finishOnDone = false).run()
		}
		onDisable { task?.cancel(); task = null }
	}

	fun SafeContext.getTrapPositions(player: PlayerEntity): Set<BlockPos> {
		val min = player.boundingBox.shrinkByEpsilon().minPos.flooredBlockPos.add(-1, -1, -1)
		val max = player.boundingBox.shrinkByEpsilon().maxPos.flooredBlockPos.add(1, 1, 1)

		return buildSet {
			(min.x + 1..<max.x).forEach { x ->
				(min.y + 1..<max.y).forEach { y ->
					add(BlockPos(x, y, min.z))
					add(BlockPos(x, y, max.z))
				}
			}

			(min.z + 1..<max.z).forEach { z ->
				(min.y + 1..<max.y).forEach { y ->
					add(BlockPos(min.x, y, z))
					add(BlockPos(max.x, y, z))
				}
			}

			(min.x + 1..<max.x).forEach { x ->
				(min.z + 1..<max.z).forEach { z ->
					BlockPos(x, min.y, z).let { pos ->
						if (pos != player.supportingBlockPos.getOrNull() || blockState(pos).isReplaceable) add(pos)
					}
					add(BlockPos(x, max.y, z))
				}
			}
		}
	}
}