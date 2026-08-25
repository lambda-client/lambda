package com.lambda.pathing.movement

import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.atan2
import kotlin.math.hypot

data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

fun Stance.center() = HorizontalPoint(x + 0.5, y.toDouble(), z + 0.5)

fun Stance.center(view: CoarseVoxelView) = HorizontalPoint(
    x + 0.5,
    y + view.surfaceOffset(x, y - 1, z),
    z + 0.5,
)

fun horizontalDistance(from: HorizontalPoint, to: HorizontalPoint): Double =
    hypot(to.x - from.x, to.z - from.z)

fun bearingBetween(from: HorizontalPoint, to: HorizontalPoint): Double =
    Math.toDegrees(atan2(to.z - from.z, to.x - from.x)) - 90.0

fun alongEdge(from: HorizontalPoint, to: HorizontalPoint, x: Double, z: Double): Double {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val length = hypot(dx, dz)
    if (length <= 1e-9) return 0.0
    return ((x - from.x) * dx + (z - from.z) * dz) / length
}

fun interpolate(from: HorizontalPoint, to: HorizontalPoint, distance: Double): HorizontalPoint {
    val length = horizontalDistance(from, to)
    if (length <= 1e-9) return from
    val scale = distance / length
    return HorizontalPoint(
        from.x + (to.x - from.x) * scale,
        from.y,
        from.z + (to.z - from.z) * scale,
    )
}

internal const val MAX_PROGRESS_ADVANCE = 2
