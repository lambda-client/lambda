package com.lambda.module.modules.player

import com.lambda.module.Module
import com.lambda.task.buildChain
import com.lambda.task.tasks.BuildStructure.Companion.breakAndCollectBlock
import com.lambda.threading.taskContext
import com.lambda.util.Communication.info
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.text.Text

object Nuker : Module(
    name = "Nuker",
    description = "Breaks blocks around you"
) {
    private val flatten by setting("Flatten", false)

    init {
        onEnable {
            info(player.mainHandStack.toHoverableText() ?: Text.empty())

            taskContext {
                buildChain {
                    breakAndCollectBlock(mc.crosshairTarget?.blockResult?.blockPos ?: return@buildChain)
                }
            }
        }
    }
}