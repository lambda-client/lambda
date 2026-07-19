
package com.minato.interaction.construction.simulation

import com.minato.interaction.construction.simulation.processing.PreProcessingData
import com.minato.interaction.construction.simulation.result.BuildResult
import com.minato.interaction.construction.simulation.result.results.GenericResult
import com.minato.interaction.managers.rotating.Rotation.Companion.rotationTo
import com.minato.util.math.distSq
import com.minato.util.math.vec3d
import com.minato.util.player.CheckedHit
import com.minato.util.player.RotationUtils.scanClosestPoints
import com.minato.util.player.RotationUtils.scanSurfaces
import com.minato.util.world.raycast.RayCastUtils.blockResult
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

/**
 * A class designed to simulate transforming a BlockState at a specified BlockPos to
 * a TargetState.
 *
 * Some [Sim]s might need to call other sims as an intermediary between the current BlockState
 * and the TargetState. In this case, we use a dependency system to ensure type safety.
 * All sims must only return either [GenericResult]s or typed [BuildResult]s. For example, the BreakSim
 * must only return BreakResults. For this reason, each type has its own Dependency result.
 * To make sure that the results added are of the correct type, each [Sim] must be called from another [Sim].
 * Assuming the dependency stack has not reached max capacity, the original sim is then added to the dependency stack
 * kept within the [SimInfo] object. Each [BuildResult] added is then iterated over the dependency stack, calling
 * [dependentUpon] on each one. By the end, the result will be a nested group, with your initial [BuildResult] at
 * the very bottom, which is then added to the [SimInfo.concurrentResults] set. After a sim is completed, the dependency
 * is then popped from the stack.
 *
 * @param T The type of [BuildResult] this sim produces.
 *
 * @see com.minato.interaction.construction.simulation.result.Dependent
 * @see dependentUpon
 * @see withDependent
 */
@SimDsl
abstract class Sim<T : BuildResult> : Results<T> {
    /**
     * Can be overridden to return a typed Dependent result with the initial [buildResult] nested inside.
     *
     * @see com.minato.interaction.construction.simulation.result.Dependent
     */
    @SimDsl
    open fun dependentUpon(buildResult: BuildResult): BuildResult = buildResult

    /**
     * Pushes and pops the [dependent] onto and off of the dependency stack unless the [maxSimDependencies] is reached.
     */
    protected suspend fun SimInfo.withDependent(dependent: Sim<*>, block: suspend () -> Unit) {
        // +1 because the build sim counts as a dependent
        if (dependencyStack.size >= buildConfig.maxBuildDependencies + 1) return
        dependencyStack.push(dependent)
        block()
        dependencyStack.pop()
    }

    /**
     * Scans a [voxelShape] on the given [sides] at the [pos] from the [pov].
     */
    suspend fun SimInfo.scanShape(
        pov: Vec3d,
        voxelShape: VoxelShape,
        pos: BlockPos,
        sides: Set<Direction>,
        preProcessing: PreProcessingData?
    ): Set<CheckedHit>? {
        val boxes = voxelShape.boundingBoxes.map { it.offset(pos) }

        val reachSq = buildConfig.blockReach.pow(2)

        val validHits = ConcurrentSet<CheckedHit>()
        val misses = ConcurrentSet<Pair<Vec3d, Direction>>()

        supervisorScope {
            boxes.forEach { box ->
                launch {
                    if (!buildConfig.strictRayCast) {
                        box.scanClosestPoints(pov, sides, preProcessing, interactConfig.airPlace.isEnabled) { vec, side ->
                            if (pov distSq vec > reachSq)
                                misses.add(Pair(vec, side))
                            else {
                                validHits.add(
                                    CheckedHit(
                                        BlockHitResult(vec, side, pos, false),
                                        pov.rotationTo(vec)
                                    )
                                )
                            }
                        }
                    } else box.scanSurfaces(pov, sides, buildConfig.resolution, preProcessing, false) { vec, side ->
                        if (pov distSq vec > reachSq) {
                            misses.add(Pair(vec, side))
                            return@scanSurfaces
                        }

                        val newRotation = pov.rotationTo(vec)
                        val hit = newRotation.rayCast(buildConfig.blockReach, pov)?.blockResult ?: return@scanSurfaces

                        if (hit.blockPos != pos || hit.side != side) return@scanSurfaces
                        val checked = CheckedHit(hit, newRotation)

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