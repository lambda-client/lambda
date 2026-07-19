
package com.minato.module.modules.combat

import com.minato.config.ConfigEditor.editTypedSettings
import com.minato.config.ConfigEditor.hideBlock
import com.minato.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.minato.config.withEdits
import com.minato.interaction.construction.blueprint.TickingBlueprint.Companion.tickingBlueprint
import com.minato.interaction.construction.verify.TargetState
import com.minato.module.Module
import com.minato.module.modules.combat.PlayerTrap.getTrapPositions
import com.minato.module.tag.ModuleTag
import com.minato.task.RootTask.run
import com.minato.task.Task
import com.minato.task.tasks.BuildTask.Companion.build
import com.minato.util.item.ItemUtils.block
import com.minato.util.player.SlotUtils.hotbarAndInventoryStacks
import net.minecraft.block.Blocks
import net.minecraft.item.BlockItem

@Suppress("unused")
object Surround : Module(
	name = "Surround",
	description = "Surrounds your players feet with any given block",
	tag = ModuleTag.COMBAT
) {
	private val blocks by setting("Blocks", setOf(Blocks.OBSIDIAN, Blocks.ENDER_CHEST, Blocks.CRYING_OBSIDIAN))

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
				getTrapPositions(player)
					.filter { it.y <= player.blockPos.y }
					.associateWith { TargetState.Block(block) }
			}.build(finishOnDone = false).run()
		}
		onDisable { task?.cancel(); task = null }
	}
}