/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.construction.simulation

import com.lambda.config.groups.BuildConfig
import com.lambda.config.groups.InteractionConfig
import com.lambda.config.groups.InventoryConfig
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.blueprint.Blueprint
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.Drawable
import com.lambda.interaction.construction.simulation.BuildSimulator.simulate
import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.module.modules.client.TaskFlowModule
import com.lambda.threading.runSafe
import com.lambda.util.world.FastVector
import com.lambda.util.world.WorldUtils.playerBox
import com.lambda.util.world.WorldUtils.traversable
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.toVec3d
import net.minecraft.client.network.ClientPlayerEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.awt.Color

data class Simulation(
    val blueprint: Blueprint,
    val interact: InteractionConfig = TaskFlowModule.interact,
    val rotation: RotationConfig = TaskFlowModule.rotation,
    val inventory: InventoryConfig = TaskFlowModule.inventory,
    val build: BuildConfig = TaskFlowModule.build,
) {
    private val cache: MutableMap<FastVector, Set<BuildResult>> = mutableMapOf()
    private fun FastVector.toView(): Vec3d = toVec3d().add(0.5, ClientPlayerEntity.DEFAULT_EYE_HEIGHT.toDouble(), 0.5)

    fun simulate(
        pos: FastVector,
    ) = cache.getOrPut(pos) {
        val view = pos.toView()
        val isOutOfBounds = blueprint.isOutOfBounds(view)
        val isTooFar = blueprint.getClosestPointTo(view).distanceTo(view) > 10.0
        runSafe {
            if (isOutOfBounds && isTooFar) return@getOrPut emptySet()
            if (!traversable(pos.toBlockPos())) return@getOrPut emptySet()
        }

        blueprint.simulate(view, interact, rotation, inventory, build)
    }

    fun goodPositions() = cache.filter { it.value.any { it.rank.ordinal < 4 } }.map { PossiblePos(it.key.toBlockPos()) }

    class PossiblePos(val pos: BlockPos): Drawable {
        override fun SafeContext.buildRenderer() {
            withBox(Vec3d.ofBottomCenter(pos).playerBox(), Color(0, 255, 0, 50))
        }
    }

    companion object {
        fun Blueprint.simulation(
            interact: InteractionConfig = TaskFlowModule.interact,
            rotation: RotationConfig = TaskFlowModule.rotation,
            inventory: InventoryConfig = TaskFlowModule.inventory,
            build: BuildConfig = TaskFlowModule.build,
        ) = Simulation(this, interact, rotation, inventory, build)
    }
}
