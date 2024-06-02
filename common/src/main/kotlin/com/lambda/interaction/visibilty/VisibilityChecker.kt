package com.lambda.interaction.visibilty

import com.lambda.context.SafeContext
import com.lambda.config.groups.InteractionConfig
import com.lambda.interaction.RotationManager
import com.lambda.config.groups.IRotationConfig
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.primitives.extension.component6
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.entityResult
import net.minecraft.entity.Entity
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.pow

object VisibilityChecker {
    fun SafeContext.lookAtEntity(
        rotationConfig: IRotationConfig,
        interactionConfig: InteractionConfig,
        entity: Entity
    ) = findRotation(listOf(entity.boundingBox), rotationConfig, interactionConfig) {
        entityResult?.entity == entity
    }

    fun SafeContext.lookAtBlock(
        blockPos: BlockPos,
        rotationConfig: IRotationConfig = TaskFlow.rotation,
        interactionConfig: InteractionConfig = TaskFlow.interact,
        sides: Set<Direction> = emptySet()
    ): RotationContext? {
        val state = blockPos.blockState(world)
        val voxelShape = state.getOutlineShape(world, blockPos)
        val boundingBoxes = voxelShape.boundingBoxes.map { it.offset(blockPos) }
        return findRotation(boundingBoxes, rotationConfig, interactionConfig, sides) {
            blockResult?.blockPos == blockPos && (blockResult?.side in sides || sides.isEmpty())
        }
    }

    fun SafeContext.findRotation(
        boxes: List<Box>,
        rotationConfig: IRotationConfig = TaskFlow.rotation,
        interact: InteractionConfig = TaskFlow.interact,
        sides: Set<Direction> = emptySet(),
        verify: HitResult.() -> Boolean,
    ): RotationContext? {
        val eye = player.getCameraPosVec(mc.tickDelta)

        val currentRotation = RotationManager.currentRotation
        val currentCast = currentRotation.rayCast(interact.reach, eye)

        if (boxes.any { it.contains(eye) }) {
            return RotationContext(currentRotation, rotationConfig, currentCast, verify)
        }

        val validHits = mutableMapOf<Vec3d, HitResult>()
        val reachSq = interact.reach.pow(2)

        boxes.forEach { box ->
            scanVisibleSurfaces(player.eyePos, box, sides, interact.resolution) { _, vec ->
                if (eye distSq vec > reachSq) return@scanVisibleSurfaces

                val newRotation = eye.rotationTo(vec)

                val cast = newRotation.rayCast(interact.reach, eye) ?: return@scanVisibleSurfaces
                if (!cast.verify()) return@scanVisibleSurfaces

                validHits[vec] = cast
            }
        }

        validHits.keys.mostCenter?.let { optimum ->
            validHits.minByOrNull { optimum distSq it.key }?.let { closest ->
                val optimumRotation = eye.rotationTo(closest.key)
                return RotationContext(optimumRotation, rotationConfig, closest.value, verify)
            }
        }

        return null
    }

    inline fun scanVisibleSurfaces(
        eyes: Vec3d,
        box: Box,
        sides: Set<Direction>,
        resolution: Int,
        check: (Direction, Vec3d) -> Unit,
    ) {
        val shrunk = box.expand(-0.005)
        box.getVisibleSurfaces(eyes)
            .forEach { side ->
                if (sides.isNotEmpty() && side !in sides) return@forEach
                val (minX, minY, minZ, maxX, maxY, maxZ) = shrunk.bounds(side)
                val stepX = (maxX - minX) / resolution
                val stepY = (maxY - minY) / resolution
                val stepZ = (maxZ - minZ) / resolution
                (0..resolution).forEach { i ->
                    val x = if (stepX != 0.0) minX + stepX * i else minX
                    (0..resolution).forEach { j ->
                        val y = if (stepY != 0.0) minY + stepY * j else minY
                        val z = if (stepZ != 0.0) minZ + stepZ * ((if (stepX != 0.0) j else i)) else minZ
                        check(side, Vec3d(x, y, z))
                    }
                }
            }
    }

    val Set<Vec3d>.mostCenter: Vec3d?
        get() = reduceOrNull { acc, vec3d ->
            acc.add(vec3d)
        }?.multiply(1.0 / size.toDouble())

    fun Box.bounds(side: Direction) =
        when (side) {
            Direction.DOWN -> doubleArrayOf(minX, minY, minZ, maxX, minY, maxZ)
            Direction.UP -> doubleArrayOf(minX, maxY, minZ, maxX, maxY, maxZ)
            Direction.NORTH -> doubleArrayOf(minX, minY, minZ, maxX, maxY, minZ)
            Direction.SOUTH -> doubleArrayOf(minX, minY, maxZ, maxX, maxY, maxZ)
            Direction.WEST -> doubleArrayOf(minX, minY, minZ, minX, maxY, maxZ)
            Direction.EAST -> doubleArrayOf(maxX, minY, minZ, maxX, maxY, maxZ)
        }

    fun Box.getVisibleSurfaces(eyes: Vec3d) =
        EnumSet.noneOf(Direction::class.java)
            .checkAxis(eyes.x - center.x, lengthX / 2, Direction.WEST, Direction.EAST)
            .checkAxis(eyes.y - center.y, lengthY / 2, Direction.DOWN, Direction.UP)
            .checkAxis(eyes.z - center.z, lengthZ / 2, Direction.NORTH, Direction.SOUTH)

    private fun EnumSet<Direction>.checkAxis(
        diff: Double,
        limit: Double,
        negativeSide: Direction,
        positiveSide: Direction,
    ) = apply {
        when {
            diff < -limit -> add(negativeSide)
            diff > limit -> add(positiveSide)
        }
    }
}
