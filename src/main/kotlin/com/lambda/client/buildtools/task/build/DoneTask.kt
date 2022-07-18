package com.lambda.client.buildtools.task.build

import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.util.color.ColorHolder
import net.minecraft.block.Block
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

class DoneTask(
    blockPos: BlockPos,
    targetBlock: Block
) : BuildTask(blockPos, targetBlock, false, false, false) {
    override var priority = 0
    override val timeout = 0
    override var threshold = 0
    override val color = ColorHolder(50, 50, 50)
    override var hitVec3d: Vec3d? = null

    override fun SafeClientEvent.isValid() = true

    override fun SafeClientEvent.update() = false

    override fun SafeClientEvent.execute() {
        TaskProcessor.tasks.remove(blockPos)
    }

    override fun gatherInfoToString() = ""
    override fun gatherDebugInfo(): MutableList<Pair<String, String>> = mutableListOf()
}