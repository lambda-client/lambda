package com.lambda.interaction.visibilty

import com.lambda.config.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.RotationManager
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.distance
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationRequest
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.primitives.extension.component6
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.*
import java.util.*

object VisibilityChecker {
    fun SafeContext.findRotation(
        rotationConfig: IRotationConfig,
        interact: InteractionConfig,
        boxes: List<Box>,
        priority: Int = 0,
        sides: Set<Direction> = emptySet(),
        hitCheck: HitResult.() -> Boolean,
    ) : RotationRequest? {
        val eye = player.getCameraPosVec(mc.tickDelta)

        if (boxes.any { it.contains(eye) }) {
            return stay(priority, rotationConfig)
        }

        val currentRotation = RotationManager.currentRotation
        val currentCast = currentRotation.rayCast(
            interact.reach,
            interact.rayCastMask,
            eye
        )
        val check = currentCast?.let { it.hitCheck() } ?: false

        // Slowdown or freeze if looking correct
        (rotationConfig as? RotationSettings)?.slowdownIf(check) ?: run {
            if (check) {
                return stay(priority, rotationConfig)
            }
        }

        val reachSq = interact.reach * interact.reach

        var closestRotation: Rotation? = null
        var rotationDist = 0.0

        boxes.forEach { box ->
            scanVisibleSurfaces(box, sides, interact.resolution) { vec ->
                if (eye distSq vec > reachSq) return@scanVisibleSurfaces

                val newRotation = eye.rotationTo(vec)

                val cast = newRotation.rayCast(
                    interact.reach,
                    interact.rayCastMask,
                    eye
                ) ?: return@scanVisibleSurfaces
                if (!cast.hitCheck()) return@scanVisibleSurfaces

                val dist = newRotation.distance(currentRotation)
                if (dist >= rotationDist && closestRotation != null) return@scanVisibleSurfaces

                rotationDist = dist
                closestRotation = newRotation
            }
        }

        // Rotate to selected point
        closestRotation?.let { rotation ->
            return RotationRequest(priority, rotationConfig, rotation)
        }

        return null
    }

    private fun stay(priority: Int = 0, config: IRotationConfig) =
        RotationRequest(priority, config, RotationManager.currentRotation)

    inline fun SafeContext.scanVisibleSurfaces(box: Box, sides: Set<Direction>, resolution: Int, check: (Vec3d) -> Unit) {
        val shrunk = box.expand(-0.005)
        getVisibleSides(box)
            .forEach { side ->
                if (sides.isNotEmpty() && side !in sides) {
                    return@forEach
                }
                val (minX, minY, minZ, maxX, maxY, maxZ) = shrunk.bounds(side)
                val stepX = (maxX - minX) / resolution
                val stepY = (maxY - minY) / resolution
                val stepZ = (maxZ - minZ) / resolution
                for (i in 0 .. resolution) {
                    val x = if (stepX != 0.0) minX + stepX * i else minX
                    for (j in 0 .. resolution) {
                        val y = if (stepY != 0.0) minY + stepY * j else minY
                        val z = if (stepZ != 0.0) minZ + stepZ * ((if (stepX != 0.0) j else i)) else minZ
                        check(Vec3d(x, y, z))
                    }
                }
            }
    }

    fun Box.bounds(side: Direction) =
        when (side) {
            Direction.DOWN -> doubleArrayOf(minX, minY, minZ, maxX, minY, maxZ)
            Direction.UP -> doubleArrayOf(minX, maxY, minZ, maxX, maxY, maxZ)
            Direction.NORTH -> doubleArrayOf(minX, minY, minZ, maxX, maxY, minZ)
            Direction.SOUTH -> doubleArrayOf(minX, minY, maxZ, maxX, maxY, maxZ)
            Direction.WEST -> doubleArrayOf(minX, minY, minZ, minX, maxY, maxZ)
            Direction.EAST -> doubleArrayOf(maxX, minY, minZ, maxX, maxY, maxZ)
        }

    fun SafeContext.getVisibleSides(box: Box): Set<Direction> {
        val visibleSides = EnumSet.noneOf(Direction::class.java)

        val eyePos = player.eyePos
        val center = box.center

        return visibleSides
            .checkAxis(eyePos.x - center.x, box.lengthX / 2, Direction.WEST, Direction.EAST)
            .checkAxis(eyePos.y - center.y, box.lengthY / 2, Direction.DOWN, Direction.UP)
            .checkAxis(eyePos.z - center.z, box.lengthZ / 2, Direction.NORTH, Direction.SOUTH)
    }

    private fun EnumSet<Direction>.checkAxis(
        diff: Double,
        limit: Double,
        negativeSide: Direction,
        positiveSide: Direction
    ) = apply {
        when {
            diff < -limit -> {
                add(negativeSide)
            }
            diff > limit -> {
                add(positiveSide)
            }
        }
    }
}
