package com.lambda.module.modules.player

import com.lambda.module.Module
import com.lambda.task.TaskChain
import com.lambda.task.buildChain
import com.lambda.task.tasks.BreakBlock.Companion.breakBlock
import com.lambda.util.Communication.info
import com.lambda.util.KeyCode
import net.minecraft.util.math.BlockPos

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you",
    defaultKeybind = KeyCode.Comma
) {
    private val flatten by setting("Flatten", true)

    private var taskChain: TaskChain? = null

    init {
        onEnable {
            taskChain = buildChain {
                BlockPos.iterateOutwards(player.blockPos, 4, 4, 4).map {
                    it.toImmutable()
                }.filter {
                    world.getBlockState(it).isSolidBlock(world, it) && (!flatten || it.y >= player.y)
                }.forEach { pos ->
                    breakBlock(pos)
                        .onSuccess {
                            this@Nuker.info("Break of ${pos.toShortString()} took $age ms")
                        }
                }
            }

            taskChain?.tryRun()
        }

        onDisable {
            taskChain?.cancel()
        }

//        listener<TickEvent.Pre> {
//            taskChain?.cancel()
//
//            BlockPos.iterateOutwards(player.blockPos, 4, 4, 4).map {
//                it.toImmutable()
//            }.filter {
//                world.getBlockState(it).isSolidBlock(world, it) && (!flatten || it.y >= player.y)
//            }.minByOrNull {
//                player.pos distSq it.toCenterPos()
//            }?.let { pos ->
//                taskChain = buildChain {
//                    breakBlock(pos)
//                        .onSuccess {
//                            this@Nuker.info("Break took $age ms")
//                        }
//                }
//
//                taskChain?.tryRun()
//            }
//        }
    }
}