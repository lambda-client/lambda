
package com.minato.interaction.construction.simulation

import com.minato.context.Automated
import com.minato.context.SafeContext
import com.minato.graphics.mc.RenderBuilder
import com.minato.interaction.construction.blueprint.Blueprint
import com.minato.interaction.construction.simulation.BuildSimulator.simulate
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.Drawable
import com.minato.threading.runSafeAutomated
import com.minato.util.BlockUtils.blockState
import com.minato.util.world.FastVector
import com.minato.util.world.toBlockPos
import com.minato.util.world.toVec3d
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.awt.Color

@Suppress("unused")
data class Simulation(
    val blueprint: Blueprint,
    private val automated: Automated
) : Automated by automated {
    private val cache: MutableMap<FastVector, Set<BuildResult>> = mutableMapOf()
    private fun FastVector.toView(): Vec3d = toVec3d().add(0.5, ClientPlayerEntity.EYE_HEIGHT.toDouble(), 0.5)

    fun simulate(pos: FastVector) =
        cache.getOrPut(pos) {
            val pov = pos.toView()
            val isOutOfBounds = blueprint.isOutOfBounds(pov)
            val isTooFar = blueprint.getClosestPointTo(pov).distanceTo(pov) > 10.0
            return@getOrPut runSafeAutomated {
                if (isOutOfBounds && isTooFar) return@getOrPut emptySet()
                val blockPos = pos.toBlockPos()
                val isWalkable = blockState(blockPos.down()).isSideSolidFullSquare(world, blockPos, Direction.UP)
                if (!isWalkable) return@getOrPut emptySet()
                if (!playerFitsIn(blockPos)) return@getOrPut emptySet()
                blueprint.structure.simulate(pov)
            } ?: emptySet()
        }

    fun goodPositions() = cache
        .filter { entry -> entry.value.any { it.rank.ordinal < 4 } }
        .map { PossiblePos(it.key.toBlockPos(), it.value.count { it.rank.ordinal < 4 }) }

    class PossiblePos(val pos: BlockPos, val interactions: Int) : Drawable {
        override fun RenderBuilder.render() {
            box(Vec3d.ofBottomCenter(pos).playerBox()) {
                colors(Color(0, 255, 0, 50), Color(0, 255, 0, 50))
            }
        }
    }

    private fun SafeContext.playerFitsIn(pos: BlockPos): Boolean {
        return world.isSpaceEmpty(Vec3d.ofBottomCenter(pos).playerBox())
    }

    companion object {
        fun Vec3d.playerBox(): Box = Box(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3).contract(1.0E-6)

        context(c: Automated)
        fun Blueprint.simulation(automated: Automated = c) = Simulation(this, automated)
    }
}
