/*
 * Copyright 2025 Lambda
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

import com.lambda.context.AutomationConfig.maxSimDependencies
import com.lambda.interaction.construction.processing.PreProcessingInfo
import com.lambda.interaction.construction.result.BuildResult
import com.lambda.interaction.construction.result.results.GenericResult
import com.lambda.interaction.request.rotating.Rotation.Companion.rotationTo
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.CheckedHit
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.scanSurfaces
import com.lambda.util.math.distSq
import com.lambda.util.math.vec3d
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import io.ktor.util.collections.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShape
import kotlin.math.pow

@DslMarker
annotation class SimDsl

@SimDsl
abstract class Sim<T : BuildResult> : Results<T> {
    @SimDsl
    open fun dependentUpon(buildResult: BuildResult): BuildResult = buildResult

    protected suspend fun ISimInfo.withDependent(dependent: Sim<*>, block: suspend () -> Unit) {
        // +1 because the build sim counts as a dependent
        if (dependencyStack.size >= maxSimDependencies + 1) return
        dependencyStack.push(dependent)
        block()
        dependencyStack.pop()
    }

    suspend fun ISimInfo.scanShape(
        pov: Vec3d,
        voxelShape: VoxelShape,
        pos: BlockPos,
        sides: Set<Direction>,
        preProcessing: PreProcessingInfo
    ): Set<CheckedHit>? {
        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }

        val reachSq = buildConfig.interactReach.pow(2)

        val validHits = ConcurrentSet<CheckedHit>()
        val misses = ConcurrentSet<Vec3d>()

        supervisorScope {
            boxes.forEach { box ->
                launch {
                    val sides = if (buildConfig.checkSideVisibility || buildConfig.strictRayCast) {
                        sides.intersect(box.getVisibleSurfaces(pov))
                    } else sides

                    scanSurfaces(box, sides, buildConfig.resolution, preProcessing.surfaceScan) { side, vec ->
                        if (pov distSq vec > reachSq) {
                            misses.add(vec)
                            return@scanSurfaces
                        }

                        val newRotation = pov.rotationTo(vec)

                        val hit = if (buildConfig.strictRayCast) {
                            newRotation.rayCast(buildConfig.interactReach, pov)?.blockResult ?: return@scanSurfaces
                        } else {
                            val hitVec = newRotation.castBox(box, buildConfig.interactReach, pov) ?: return@scanSurfaces
                            BlockHitResult(hitVec, side, pos, false)
                        }

                        if (hit.blockPos != pos || hit.side != side) return@scanSurfaces
                        val checked = CheckedHit(hit, newRotation, buildConfig.interactReach)

                        validHits.add(checked)
                    }
                }
            }
        }

        if (validHits.isEmpty()) {
            if (misses.isNotEmpty()) {
                result(GenericResult.OutOfReach(pos, pov, misses))
                return null
            }

            result(GenericResult.NotVisible(pos, pos, pov.distanceTo(pos.vec3d)))
            return null
        }

        return validHits
    }
}