package com.lambda.module.modules.player

import com.lambda.interaction.construction.DynamicBlueprint
import com.lambda.interaction.construction.verify.TargetState
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.task.Task
import com.lambda.task.Task.Companion.emptyTask
import com.lambda.task.tasks.BuildStructure.Companion.buildStructure
import com.lambda.util.BlockUtils.blockPos
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.BlockUtils.instantBreakable
import com.lambda.util.KeyCode
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3i

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you",
    defaultKeybind = KeyCode.Comma,
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val flatten by setting("Flatten", true)
    private val onlyBreakInstant by setting("Only Break Instant", true)
    private val fillFloor by setting("Fill Floor", false)

    private val range = Vec3i(4, 4, 4) // TODO: Customizable
    private var task: Task<*> = emptyTask()

    init {
        onEnable {
            task = buildStructure(
                pathing = false,
                finishOnDone = false
            ) {
                DynamicBlueprint { _ ->
                    val selection = BlockPos.iterateOutwards(player.blockPos, range.x, range.y, range.z)
                        .asSequence()
                        .map { it.blockPos }
                        .filter { !world.isAir(it) }
                        .filter { !flatten || it.y >= player.blockPos.y }
                        .filter { !onlyBreakInstant || instantBreakable(it.blockState(world), it) }
                        .associateWith { TargetState.Air }

//                    if (fillFloor) {
//                        // ToDo: Use smarter iteration pattern
//                        val floor = BlockPos.iterateOutwards(player.blockPos, range.x, range.y, range.z)
//                            .filter { it.y == player.blockPos.down().y }
//                            .map { it.blockPos }
//                            .associateWith { TargetState.Solid }
//                        return@DynamicBlueprint selection + floor
//                    }

                    selection
                }
            }
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
