package com.lambda.client.buildtools.task

import com.lambda.client.buildtools.blueprint.StructureTask
import com.lambda.client.buildtools.blueprint.strategies.MoveXStrategy
import com.lambda.client.buildtools.pathfinding.BaritoneHelper
import com.lambda.client.buildtools.pathfinding.BaritonePathfindingProcess
import com.lambda.client.buildtools.task.build.BreakTask
import com.lambda.client.buildtools.task.build.DoneTask
import com.lambda.client.buildtools.task.build.PlaceTask
import com.lambda.client.buildtools.task.sequence.TaskSequenceStrategy
import com.lambda.client.buildtools.task.sequence.strategies.LeftToRightStrategy
import com.lambda.client.buildtools.task.sequence.strategies.OriginStrategy
import com.lambda.client.buildtools.task.sequence.strategies.RandomStrategy
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.interactionLimit
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils.distanceTo
import net.minecraft.util.math.BlockPos
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedDeque

object TaskProcessor {
    val tasks = ConcurrentHashMap<BlockPos, BuildTask>()
    var currentBuildTask: BuildTask? = null
    var waitPlace = 0
    var waitBreak = 0
    val packetLimiter = ConcurrentLinkedDeque<Long>()

    enum class EnumTaskSequenceStrategy {
        ORIGIN {
            override fun getInstance() = OriginStrategy
        },
        RANDOM {
            override fun getInstance() = RandomStrategy
        },
        LEFT_TO_RIGHT {
            override fun getInstance() = LeftToRightStrategy
        };

        abstract fun getInstance(): TaskSequenceStrategy

        companion object {
            fun getDefault() = ORIGIN
        }
    }

    fun SafeClientEvent.doTickOnTasks(structureTask: StructureTask) {
        /* update all tasks */
        tasks.values.forEach {
            with(it) {
                runUpdate()
            }
        }

        /* Remove done tasks */
        if (structureTask.blueprintStrategy is MoveXStrategy) {
            tasks.values.filter {
                it is DoneTask
                    && structureTask.blueprintStrategy.currentOrigin.distanceTo(it.blockPos) > BuildTools.maxReach + 2
            }.forEach {
                if (it.toRemove) {
                    if (System.currentTimeMillis() - it.timeStamp > 1000L) tasks.remove(it.blockPos)
                } else {
                    it.toRemove = true
                    it.timeStamp = System.currentTimeMillis()
                }
            }
        }

        /* get task with the highest priority based on selection strategy */
        val containerTasks = getContainerTasks()

        currentBuildTask = if (containerTasks.isEmpty()) {
            structureTask.taskSequenceStrategy.getNextTask(tasks.values.toList())
        } else {
            structureTask.taskSequenceStrategy.getNextTask(containerTasks)
        }

        currentBuildTask?.let { currentTask ->
            with(currentTask) {
                if (currentTask is BreakTask && waitBreak > 0) waitBreak--
                if (currentTask is PlaceTask && waitPlace > 0) waitPlace--

                if (isValid() && !runUpdate()) {
                    BaritoneHelper.setupBaritone()

                    BaritoneUtils.primary?.pathingControlManager?.registerProcess(BaritonePathfindingProcess)
                    runExecute()

                    hitVec3d?.let {
                        BuildTools.sendPlayerPacket {
                            rotate(getRotationTo(it))
                        }
                    }
                }
            }
        }
    }

    /* allows tasks to convert into different types */
    inline fun <reified T : BuildTask> BuildTask.convertTo(
        isSupportTask: Boolean = this.isSupportTask,
        isFillerTask: Boolean = this.isFillerTask,
        isContainerTask: Boolean = this.isContainerTask
    ) {
        val newTask = T::class.java.getDeclaredConstructor().newInstance(blockPos, targetBlock)
        newTask.isSupportTask = isSupportTask
        newTask.isFillerTask = isFillerTask
        newTask.isContainerTask = isContainerTask

        tasks[blockPos] = newTask
    }

    fun addTask(buildTask: BuildTask) {
        tasks[buildTask.blockPos] = buildTask
    }

    fun getContainerTasks() = tasks.values.filter { it.isContainerTask }

    fun isDone() = tasks.values.all { it is DoneTask }

    fun reset() {
        tasks.clear()
        currentBuildTask = null
        waitBreak = 0
        waitPlace = 0
        packetLimiter.clear()
    }

    val interactionLimitNotReached = packetLimiter.size < interactionLimit
}
