package com.lambda.module.modules.player

import com.lambda.interaction.construction.Blueprint.Companion.emptyStructure
import com.lambda.interaction.construction.DynamicBlueprint.Companion.toBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.BuildTask.Companion.build
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import net.minecraft.util.math.BlockPos

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you",
    defaultTags = setOf(ModuleTag.PLAYER, ModuleTag.AUTOMATION)
) {
    private val height by setting("Height", 4, 1..8, 1)
    private val width by setting("Width", 4, 1..8, 1)
    private val flatten by setting("Flatten", true)
    private val onlyBreakInstant by setting("Only Break Instant", true)
    private val fillFloor by setting("Fill Floor", false)

    private var task = emptyTask()

    init {
        onEnable {
            task = emptyStructure()
                .toBlueprint {
                    val selection = BlockPos.iterateOutwards(player.blockPos, width, height, width)
                        .asSequence()
                        .map { it.blockPos }
                        .filter { !world.isAir(it) }
                        .filter { !flatten || it.y >= player.blockPos.y }
                        .filter { !onlyBreakInstant || it.blockState(world).getHardness(world, it) <= 1 }
                        .filter { it.blockState(world).getHardness(world, it) >= 0 }
                        .associateWith { TargetState.Air }

                    if (fillFloor) {
                        val floor = BlockPos.iterateOutwards(player.blockPos.down(), width, 0, width)
                            .map { it.blockPos }
                            .associateWith { TargetState.Solid }
                        return@toBlueprint selection + floor
                    }

                    selection
                }
                .build(
                    pathing = false,
                    finishOnDone = false,
                    cancelOnUnsolvable = false
                )
            task.start(null)
        }

        onDisable {
            task.cancel()
        }

//        listener<TickEvent.Pre> {
//            task?.let {
//                if (!it.isRunning) return@listener
//
//                info(it.info)
//            }
//        }
    }
}
