
package com.minato.task.tasks

import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.interaction.construction.blueprint.Blueprint.Companion.toStructure
import com.minato.interaction.construction.blueprint.StaticBlueprint.Companion.toBlueprint
import com.minato.interaction.construction.simulation.BuildSimulator.simulate
import com.minato.interaction.construction.simulation.result.results.GenericResult
import com.minato.interaction.construction.simulation.result.results.InteractResult
import com.minato.interaction.construction.verify.TargetState
import com.minato.interaction.managers.ManagerUtils
import com.minato.task.Task
import com.minato.task.tasks.BuildTask.Companion.build
import com.minato.threading.runSafeAutomated
import com.minato.util.BlockUtils.blockPos
import com.minato.util.item.ItemUtils.shulkerBoxes
import com.minato.util.math.distSq
import net.minecraft.block.ChestBlock
import net.minecraft.entity.mob.ShulkerEntity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.screen.slot.Slot
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class PlaceContainerTask @Ta5kBuilder constructor(
    val slot: Slot,
    automated: Automated
) : Task<BlockPos>(), Automated by automated {
    override val name: String get() = "Placing container ${slot.stack.name.string}"

    override fun SafeContext.onStart() {
        val results = runSafeAutomated {
            BlockPos.iterateOutwards(player.blockPos, 4, 3, 4)
                .map { it.blockPos }
                .asSequence()
                .filter { !ManagerUtils.isPosBlocked(it) }
                .flatMap {
                    it.toStructure(TargetState.Stack(slot.stack))
                        .simulate()
                }
        }

        val options = results.filterIsInstance<InteractResult.Interact>().filter {
            canBeOpened(slot.stack, it.pos, it.context.hitResult.side)
        } + results.filterIsInstance<GenericResult.WrongItemSelection>()

        val containerPosition = options.filter {
            // ToDo: Check based on if we can move the player close enough rather than y level once the custom pathfinder is merged
            it.pos.y == player.blockPos.y
        }.minByOrNull { it.pos distSq player.pos }?.pos ?: run {
            failure("Couldn't find a valid container placement position for ${slot.stack.name.string}")
            return@onStart
        }

        containerPosition
            .toStructure(TargetState.Stack(slot.stack))
            .toBlueprint()
            .build(finishOnDone = true, collectDrops = false)
            .finally { success(containerPosition) }
            .execute(this@PlaceContainerTask)
    }

    private fun SafeContext.canBeOpened(
        itemStack: ItemStack,
        blockPos: BlockPos,
        direction: Direction,
    ) = when (itemStack.item) {
        Items.ENDER_CHEST -> {
            !ChestBlock.isChestBlocked(world, blockPos)
        }
        in shulkerBoxes -> {
            val box = ShulkerEntity
                .calculateBoundingBox(0.5f, direction, 0.0f, blockPos.toBottomCenterPos())
                .offset(blockPos)
                .contract(1.0E-6)

            world.isSpaceEmpty(box)
        }
        else -> false
    }
}
