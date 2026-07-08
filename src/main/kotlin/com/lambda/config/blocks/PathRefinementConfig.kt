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

interface PathRefinementConfig {
    val enabled: Boolean
    val clearanceMargin: Double
    val maxLookahead: Int
    val maxChecks: Int
    val flatWalkSampleStep: Double
    val useSimulation: Boolean
    val useHybridRefinement: Boolean
    val hybridVerticalStepLimit: Int
    val hybridSimMaxTicks: Int
    val hybridFlatSampleStep: Double
    val simulationMaxTicks: Int
    val minSimulationTicks: Int
    val simulationTickBudgetPadding: Int
    val simulationLookaheadDistance: Double
    val simulationCorridorMargin: Double
    val simulationStagnationTicks: Int
    val expectedWalkSpeed: Double
    val expectedSprintWalkSpeed: Double
    val expectedJumpSpeed: Double
    val expectedSprintJumpSpeed: Double
    val reachThreshold: Double
    val verticalReachThreshold: Double
    val softReachThreshold: Double
    val softReachMultiplier: Double
    val softVerticalReachThreshold: Double
    val softReachMaxSpeed: Double
    val stagnationProgressEpsilon: Double
    val backtrackTolerance: Double
    val maxOvershootDistance: Double
    val maxOvershootRemainingDistance: Double
    val maxUnplannedRise: Double
    val maxUnplannedDrop: Double
    val minBrakingWindow: Double
    val walkBrakingTicks: Double
    val sprintBrakingTicks: Double
}