package com.lambda.interaction.visibilty

import com.lambda.config.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.interaction.InteractionConfig
import com.lambda.interaction.RotationManager
import com.lambda.interaction.rotation.IRotationConfig
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.RotationContext
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.primitives.extension.component6
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.pow

object VisibilityChecker {
    fun SafeContext.findRotation(
        rotationConfig: IRotationConfig,
        interact: InteractionConfig,
        boxes: List<Box>,
        sides: Set<Direction> = emptySet(),
        verify: HitResult.() -> Boolean,
    ): RotationContext? {
        val eye = player.getCameraPosVec(mc.tickDelta)

        if (boxes.any { it.contains(eye) }) {
            return stay(rotationConfig)
        }

//        val currentRotation = RotationManager.currentRotation
//        val currentCast = currentRotation.rayCast(
//            interact.reach,
//            interact.rayCastMask,
//            eye
//        )
//        val passed = currentCast?.let { it.verify() } ?: false
//        // Slowdown or freeze if looking correct
//        (rotationConfig as? RotationSettings)?.slowdownIf(passed) ?: run {
//            if (passed) {
//                return stay(rotationConfig)
//            }
//        }

        val validHits = mutableSetOf<Vec3d>()

        boxes.forEach { box ->
            scanVisibleSurfaces(box, sides, interact.resolution) { vec ->
                if (eye distSq vec > interact.reach.pow(2)) return@scanVisibleSurfaces

                val newRotation = eye.rotationTo(vec)

                val cast = newRotation.rayCast(
                    interact.reach,
                    interact.rayCastMask,
                    eye
                ) ?: return@scanVisibleSurfaces
                if (!cast.verify()) return@scanVisibleSurfaces

                validHits.add(vec)
            }
        }

        validHits.mostCenter?.let { optimum ->
            validHits.minByOrNull { optimum distSq it }?.let { closest ->
                val optimumRotation = eye.rotationTo(closest)
                return RotationContext(optimumRotation, rotationConfig)
            }
        }

        return null
    }

    private fun stay(config: IRotationConfig) =
        RotationContext(RotationManager.currentRotation, config)

    private inline fun SafeContext.scanVisibleSurfaces(
        box: Box,
        sides: Set<Direction>,
        resolution: Int,
        check: (Vec3d) -> Unit,
    ) {
        val shrunk = box.expand(-0.005)
        box.getVisibleSurfaces(player.eyePos)
            .forEach { side ->
                if (sides.isNotEmpty() && side !in sides) {
                    return@forEach
                }
                val (minX, minY, minZ, maxX, maxY, maxZ) = shrunk.bounds(side)
                val stepX = (maxX - minX) / resolution
                val stepY = (maxY - minY) / resolution
                val stepZ = (maxZ - minZ) / resolution
                (0..resolution).forEach { i ->
                    val x = if (stepX != 0.0) minX + stepX * i else minX
                    (0..resolution).forEach { j ->
                        val y = if (stepY != 0.0) minY + stepY * j else minY
                        val z = if (stepZ != 0.0) minZ + stepZ * ((if (stepX != 0.0) j else i)) else minZ
                        check(Vec3d(x, y, z))
                    }
                }
            }
    }

    private val Set<Vec3d>.mostCenter: Vec3d?
        get() = reduceOrNull { acc, vec3d ->
            acc.add(vec3d)
        }?.multiply(1.0 / size.toDouble())

    private fun Box.bounds(side: Direction) =
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
