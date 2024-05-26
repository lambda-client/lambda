package com.lambda.task.tasks

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listener
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.Blueprint.Companion.toStructure
import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.StaticBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.context.BreakContext
import com.lambda.interaction.construction.result.BreakResult
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Resolvable
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.task.Task
import com.lambda.task.TaskCha1nBuilder
import com.lambda.util.Communication.warn
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

class BuildStructure(
    private val blueprint: Blueprint,
    private val collectDrops: Boolean = false,
    private val skipWeakBlocks: Boolean = false,
    private val pathing: Boolean = true,
    private val finishOnDone: Boolean = true,
    private val limitPerTick: Int = 20
) : Task<Unit>() {
    private var lastResult: BuildResult? = null

    init {
        listener<TickEvent.Pre> {
            val structure = when (blueprint) {
                is DynamicBlueprint -> blueprint.update(this)
                else -> blueprint.structure
            }

            if (finishOnDone && structure.isEmpty()) {
                this@BuildStructure.warn("Structure is empty")
                success(Unit)
                return@listener
            }

            val results = structure.entries.fold(emptySet<BuildResult>()) { acc, (pos, state) ->
                val buildContext = BreakContext(
                    pos,
                    Direction.entries.count { world.isAir(pos.offset(it)) },
                    player.pos.squaredDistanceTo(pos.toCenterPos())
                )

                acc + BreakResult.Success(buildContext)
            }

//            results.sorted().take(limitPerTick)

            results.minOrNull()?.let { result ->
                if (lastResult == result) return@listener
                if (result !is Resolvable) return@listener

                lastResult = result
                cancelSubTasks()
                result.resolve.start(this@BuildStructure, false)
            }
        }
    }

    companion object {
        @TaskCha1nBuilder
        fun buildStructure(
            collectDrops: Boolean = false,
            skipWeakBlocks: Boolean = false,
            pathing: Boolean = true,
            finishOnDone: Boolean = true,
            blueprint: () -> Blueprint,
        ) = BuildStructure(
                blueprint(),
                collectDrops,
                skipWeakBlocks,
                pathing,
                finishOnDone
            )

        @TaskCha1nBuilder
        fun breakAndCollectBlock(
            blockPos: BlockPos
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint(),
            collectDrops = true
        )

        @TaskCha1nBuilder
        fun breakBlock(
            blockPos: BlockPos
        ) = BuildStructure(
            blockPos.toStructure(TargetState.Air).toBlueprint()
        )
    }
}