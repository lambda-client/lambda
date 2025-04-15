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

package com.lambda.pathing

import com.lambda.interaction.request.rotation.RotationConfig
import com.lambda.util.NamedEnum

interface PathingConfig {
    val algorithm: PathingAlgorithm
    val cutoffTimeout: Long
    val maxFallHeight: Double
    val mlg: Boolean
    val useWaterBucket: Boolean
    val useLavaBucket: Boolean
    val useBoat: Boolean
    val maxPathLength: Int

    val refinePath: Boolean
    val useThetaStar: Boolean
    val shortcutLength: Int
    val clearancePrecision: Double
    val findShortcutJumps: Boolean
    val maxJumpDistance: Double
    val spline: Spline
    val epsilon: Double

    val moveAlongPath: Boolean
    val kP: Double
    val kI: Double
    val kD: Double
    val tolerance: Double
    val allowSprint: Boolean

    val rotation: RotationConfig

    val renderCoarsePath: Boolean
    val renderRefinedPath: Boolean
    val renderGoal: Boolean
    val renderGraph: Boolean
    val renderPositions: Boolean
    val renderCost: Boolean
    val renderG: Boolean
    val renderRHS: Boolean
    val maxRenderObjects: Int
    val fontScale: Double
    val assumeJesus: Boolean

    enum class PathingAlgorithm(override val displayName: String) : NamedEnum {
        A_STAR("A*"),
        D_STAR_LITE("Lazy D* Lite"),
    }

    enum class Spline {
        None,
        CatmullRom,
        CubicBezier,
    }
}
