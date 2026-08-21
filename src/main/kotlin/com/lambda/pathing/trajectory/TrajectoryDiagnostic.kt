/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.trajectory

import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

sealed interface TrajectoryDiagnostic {
    val frame: Int

    data class HorizontalCollision(
        override val frame: Int,
        val position: Vec3d,
    ) : TrajectoryDiagnostic

    data class HeadBonk(
        override val frame: Int,
        val position: Vec3d,
    ) : TrajectoryDiagnostic

    data class FellBelowRoute(
        override val frame: Int,
        val depth: Double,
    ) : TrajectoryDiagnostic

    data class HarmfulFall(
        override val frame: Int,
        val fallDistance: Double,
    ) : TrajectoryDiagnostic

    data class NoStop(
        override val frame: Int,
        val goalError: Double,
        val speed: Double,
    ) : TrajectoryDiagnostic

    data class UnsupportedPhysics(
        override val frame: Int,
        val reason: String,
    ) : TrajectoryDiagnostic

    data class OutsideSnapshot(
        override val frame: Int,
        val position: BlockPos,
    ) : TrajectoryDiagnostic
}
