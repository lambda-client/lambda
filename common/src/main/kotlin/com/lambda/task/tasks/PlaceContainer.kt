package com.lambda.task.tasks

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.PlaceResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.Task
import com.lambda.task.tasks.BuildStructure.Companion.buildStructure
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.primitives.extension.tickDelta
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

class PlaceContainer @Ta5kBuilder constructor(
    val stack: ItemStack,
) : Task<BlockPos>() {
    override fun SafeContext.onStart() {
        val results = BlockPos.iterateOutwards(player.blockPos, 4, 3, 4)
            .map { it.blockPos }
            .flatMap {
                it.blockPos
                    .toStructure(TargetState.Stack(stack))
                    .toBlueprint()
                    .simulate(player.getCameraPosVec(mc.tickDelta))
            }

//        val res = results.sorted()
//        res

        val succeeds = results.filterIsInstance<PlaceResult.Success>()
        val wrongStacks = results.filterIsInstance<BuildResult.WrongStack>()
        (succeeds + wrongStacks).minOrNull()?.let { result ->
            buildStructure {
                result.blockPos
                    .toStructure(TargetState.Stack(stack))
                    .toBlueprint()
            }.onSuccess { _, _ ->
                success(result.blockPos)
            }.start(this@PlaceContainer)
        } ?: {
            failure("No valid placement found")
        }
    }

    companion object {
        @Ta5kBuilder
        fun placeContainer(stack: ItemStack) =
            PlaceContainer(stack)
    }
}
