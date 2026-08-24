/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.world.CoarseVoxelView
import kotlin.math.atan2
import kotlin.math.hypot

/** A point on a stance surface: horizontal position with the surface height it belongs to. */
data class HorizontalPoint(val x: Double, val y: Double, val z: Double)

fun Stance.center() = HorizontalPoint(x + 0.5, y.toDouble(), z + 0.5)

/**
 * The stance's centre at the height the body's feet will actually be.
 *
 * A stance names the cell above whatever holds the body up, so its nominal height is that
 * cell's floor -- right whenever the support is a whole block, and half a block too high
 * over a slab. Everything that only steers by it can use the nominal [center]; anything
 * that compares a height against a simulated body has to use this one, or it is asking
 * whether the body is standing half a block inside the floor.
 */
fun Stance.center(view: CoarseVoxelView) = HorizontalPoint(
    x + 0.5,
    y + view.surfaceOffset(x, y - 1, z),
    z + 0.5,
)

fun horizontalDistance(from: HorizontalPoint, to: HorizontalPoint): Double =
    hypot(to.x - from.x, to.z - from.z)

fun bearingBetween(from: HorizontalPoint, to: HorizontalPoint): Double =
    Math.toDegrees(atan2(to.z - from.z, to.x - from.x)) - 90.0

/** How far along the `from -> to` line the point (x, z) lies, in blocks. */
fun alongEdge(from: HorizontalPoint, to: HorizontalPoint, x: Double, z: Double): Double {
    val dx = to.x - from.x
    val dz = to.z - from.z
    val length = hypot(dx, dz)
    if (length <= 1e-9) return 0.0
    return ((x - from.x) * dx + (z - from.z) * dz) / length
}

/** Interpolates [distance] blocks along the `from -> to` line, keeping `from`'s height. */
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
