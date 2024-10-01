package com.lambda.interaction.construction.simulation

import com.lambda.context.SafeContext
import com.lambda.interaction.construction.Blueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.module.modules.client.TaskFlow
import com.lambda.task.Task
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.Communication.info
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toFastVec
import com.lambda.util.world.toVec3d
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d

data class Simulation(val blueprint: Blueprint) {
    private val cache: MutableMap<FastVector, Set<BuildResult>> = mutableMapOf()
    private fun FastVector.toView(): Vec3d = toVec3d().add(0.5, 0.62, 0.5)

    fun simulate(pos: FastVector): Set<BuildResult> {
        return cache.computeIfAbsent(pos) {
            val view = pos.toView()
            runSafe {
                if (blueprint.isOutOfBounds(view) && blueprint.getClosestPointTo(view).distanceTo(view) > 10.0) return@computeIfAbsent emptySet()
                val blockPos = pos.toBlockPos()
                if (!playerFitsIn(Vec3d.ofBottomCenter(blockPos))) return@computeIfAbsent emptySet()
                if (!blockPos.down().blockState(world).isSideSolidFullSquare(world, blockPos, Direction.UP)) return@computeIfAbsent emptySet()
            }
            blueprint.simulate(view, reach = TaskFlow.interact.reach - 1)
        }
    }

    private fun SafeContext.playerFitsIn(pos: Vec3d): Boolean {
        val pBox = player.boundingBox
        val aabb = Box(pBox.minX, pBox.minY - 1.0E-6, pBox.minZ, pBox.maxX, pBox.minY, pBox.maxZ)
        return world.isSpaceEmpty(aabb.offset(pos))
    }

    companion object {
        fun Blueprint.simulation() = Simulation(this)
    }
}