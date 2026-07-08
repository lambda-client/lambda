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

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group

class PathRefinementSettings(override val c: Config) : PathRefinementConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
        private const val FLAT_SWEEP_GROUP = "Flat Sweep"
        private const val SIMULATION_GROUP = "Simulation"
        private const val ACCEPTANCE_GROUP = "Acceptance"
        private const val CONTROLLER_GROUP = "Controller"
    }

    @Group(GENERAL_GROUP) override val enabled by c.setting("Refinement Enabled", true, "Enable post-processing that replaces runs of coarse nodes with validated shortcut segments.")
    @Group(GENERAL_GROUP) override val clearanceMargin by c.setting("Clearance Margin", 0.05, 0.0..0.30, 0.005, "Extra horizontal margin added to standing-position clearance checks so shortcuts do not graze block corners.", " blocks")
    @Group(GENERAL_GROUP) override val maxLookahead by c.setting("Max Lookahead", 64, 1..512, 1, "Maximum number of future coarse nodes one anchor may try to skip over while searching for the farthest valid shortcut.")
    @Group(GENERAL_GROUP) override val maxChecks by c.setting("Max Checks", 2_048, 1..20_000, 1, "Budget for shortcut validation attempts during one refinement pass. When exhausted, the remaining coarse path is kept as-is.")

    @Group(FLAT_SWEEP_GROUP) override val flatWalkSampleStep by c.setting("Sample Step", 0.125, 0.01..0.25, 0.001, "Distance between dense standing-position samples when validating same-height any-angle walk shortcuts.", " blocks")
    @Group(FLAT_SWEEP_GROUP) override val hybridFlatSampleStep by c.setting("Hybrid Flat Sample Step", 0.125, 0.01..0.25, 0.005, "Sample step for the flat tail of a hybrid shortcut. Can be coarser than pure flat sweep since the sim already validated the tricky vertical part.", " blocks") { useHybridRefinement }

    @Group(SIMULATION_GROUP) override val useSimulation by c.setting("Use Simulation", true, "Use the movement simulator for non-flat shortcuts such as rises, drops, and jump-like profiles.")
    @Group(SIMULATION_GROUP) override val useHybridRefinement by c.setting("Hybrid Refinement", true, "Simulate only the vertical transition part of a non-flat shortcut then use the fast flat sweep for the remainder. Much cheaper than full simulation.") { useSimulation }
    @Group(SIMULATION_GROUP) override val hybridVerticalStepLimit by c.setting("Hybrid Step Limit", 1, 0..5, 1, "Maximum vertical delta (block difference) for which the hybrid sim+sweep is attempted. Larger deltas route to full simulation instead.") { useHybridRefinement }
    @Group(SIMULATION_GROUP) override val hybridSimMaxTicks by c.setting("Hybrid Max Ticks", 40, 10..200, 1, "Maximum simulation ticks for the vertical transition phase of a hybrid shortcut. When exceeded the transition is considered stuck.", " ticks") { useHybridRefinement }
    @Group(SIMULATION_GROUP) override val simulationMaxTicks by c.setting("Max Ticks", 120, 1..400, 1, "Hard upper bound on simulated ticks for a single shortcut attempt.", " ticks") { useSimulation }
    @Group(SIMULATION_GROUP) override val minSimulationTicks by c.setting("Min Ticks", 8, 1..100, 1, "Lower bound on shortcut simulation time even for very short segments, giving the controller time to accelerate and settle.", " ticks") { useSimulation }
    @Group(SIMULATION_GROUP) override val simulationTickBudgetPadding by c.setting("Tick Padding", 18, 0..100, 1, "Extra slack added on top of the estimated travel time for a shortcut profile.", " ticks") { useSimulation }
    @Group(SIMULATION_GROUP) override val simulationLookaheadDistance by c.setting("Sim Lookahead Distance", 0.75, 0.0..3.0, 0.05, "Distance ahead on the shortcut segment that lookahead-based profiles steer toward while simulating.", " blocks") { useSimulation }
    @Group(SIMULATION_GROUP) override val simulationCorridorMargin by c.setting("Corridor Margin", 0.15, 0.0..1.0, 0.01, "Additional lateral slack around the ideal shortcut segment before the simulation is considered to have drifted away.", " blocks") { useSimulation }
    @Group(SIMULATION_GROUP) override val simulationStagnationTicks by c.setting("Stagnation Ticks", 8, 1..60, 1, "Number of consecutive ticks without meaningful progress before a shortcut attempt is treated as stalled.", " ticks") { useSimulation }
    @Group(SIMULATION_GROUP) override val expectedWalkSpeed by c.setting("Walk Speed", 0.10, 0.01..0.40, 0.005, "Expected horizontal blocks per tick for a non-sprinting walk profile when estimating simulation time budgets.", " blocks/tick") { useSimulation }
    @Group(SIMULATION_GROUP) override val expectedSprintWalkSpeed by c.setting("Sprint Walk Speed", 0.14, 0.01..0.50, 0.005, "Expected horizontal blocks per tick for sprint walk profiles when estimating simulation time budgets.", " blocks/tick") { useSimulation }
    @Group(SIMULATION_GROUP) override val expectedJumpSpeed by c.setting("Jump Speed", 0.14, 0.01..0.50, 0.005, "Expected horizontal blocks per tick for non-sprinting jump profiles when estimating simulation time budgets.", " blocks/tick") { useSimulation }
    @Group(SIMULATION_GROUP) override val expectedSprintJumpSpeed by c.setting("Sprint Jump Speed", 0.18, 0.01..0.60, 0.005, "Expected horizontal blocks per tick for sprint jump profiles when estimating simulation time budgets.", " blocks/tick") { useSimulation }

    @Group(ACCEPTANCE_GROUP) override val reachThreshold by c.setting("Reach Threshold", 0.2, 0.01..1.0, 0.01, "Horizontal distance that counts as a hard arrival when the simulator is already grounded and standing safely.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val verticalReachThreshold by c.setting("Vertical Reach Threshold", 0.35, 0.01..1.5, 0.01, "Maximum allowed vertical mismatch for hard arrival acceptance.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val softReachThreshold by c.setting("Soft Reach Threshold", 0.14, 0.01..1.0, 0.01, "Base horizontal tolerance used for soft acceptance when a shortcut stalls or slightly overshoots near the goal.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val softReachMultiplier by c.setting("Soft Reach Multiplier", 2.0, 1.0..5.0, 0.05, "Multiplier applied to the hard reach threshold when evaluating soft arrival recovery.") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val softVerticalReachThreshold by c.setting("Soft Vertical Threshold", 0.6, 0.01..2.0, 0.01, "Maximum vertical mismatch allowed for soft acceptance near the goal.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val softReachMaxSpeed by c.setting("Soft Max Speed", 0.12, 0.0..0.5, 0.005, "Maximum horizontal speed allowed for soft acceptance. Prevents fast fly-bys from being treated as arrivals.", " blocks/tick") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val stagnationProgressEpsilon by c.setting("Progress Epsilon", 1.0E-3, 1.0E-5..0.05, 1.0E-4, "Minimum improvement in remaining shortcut distance that resets the stagnation counter.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val backtrackTolerance by c.setting("Backtrack Tolerance", 0.05, 0.0..1.0, 0.01, "How far the simulated actor may move behind the start of the segment before the attempt is rejected as backtracking.", " segment progress") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val maxOvershootDistance by c.setting("Overshoot Distance", 0.55, 0.0..2.0, 0.01, "Maximum distance the simulation may travel past the segment end before the shortcut is rejected as a real overshoot.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val maxOvershootRemainingDistance by c.setting("Overshoot Remaining", 0.45, 0.0..2.0, 0.01, "Overshoots are only rejected when the actor is still at least this far away from the goal after passing it.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val maxUnplannedRise by c.setting("Unplanned Rise", 1.25, 0.0..5.0, 0.05, "Maximum unexpected climb above the start height for profiles that are not supposed to rise.", " blocks") { useSimulation }
    @Group(ACCEPTANCE_GROUP) override val maxUnplannedDrop by c.setting("Unplanned Drop", 3.5, 0.0..10.0, 0.05, "Maximum unexpected drop below the shortcut endpoints for profiles that are not supposed to fall.", " blocks") { useSimulation }

    @Group(CONTROLLER_GROUP) override val minBrakingWindow by c.setting("Min Braking Window", 0.45, 0.0..3.0, 0.01, "Smallest distance window where simulated walk profiles start reducing forward input near the target.", " blocks") { useSimulation }
    @Group(CONTROLLER_GROUP) override val walkBrakingTicks by c.setting("Walk Braking Ticks", 6.0, 0.0..30.0, 0.1, "How many ticks of current horizontal speed to reserve as braking room for walk profiles.", " ticks") { useSimulation }
    @Group(CONTROLLER_GROUP) override val sprintBrakingTicks by c.setting("Sprint Braking Ticks", 8.0, 0.0..30.0, 0.1, "How many ticks of current horizontal speed to reserve as braking room for sprint profiles.", " ticks") { useSimulation }
}
