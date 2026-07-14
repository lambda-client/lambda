/*
 * Copyright 2026 Lambda
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

package com.lambda.config.blocks
interface MovementConfig {

    val enabled: Boolean

    val followPartialPaths: Boolean
    val allowSprint: Boolean
    val lookaheadDistance: Double
    val corridorRadius: Double
    val verticalTolerance: Double
    val backtrackAllowance: Double
    val overshootAllowance: Double
    val reachDistance: Double
    val finalApproachDistance: Double
    val minimumThrottle: Double
    val sprintMinRemaining: Double
    val goalStopSpeed: Double
    val searchBehindSegments: Int
    val searchAheadSegments: Int
    val relocalizeDistance: Double
    val maxLostTicks: Int

    val maxDebugSamples: Int
    val logExecutionDebug: Boolean
    val logDebugInterval: Int
    val logJumpDiagnostics: Boolean
}
