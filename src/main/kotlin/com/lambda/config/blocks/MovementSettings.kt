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

class MovementSettings(override val c: Config) : MovementConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
        private const val DEBUG_GROUP = "Debug"
    }

    @Group(GENERAL_GROUP) override val enabled by c.setting("Executor Enabled", true, "Master toggle for the path executor. When disabled, paths are still planned and rendered but the player does not walk.")
    @Group(GENERAL_GROUP) override val followPartialPaths by c.setting("Follow Partial Paths", true, "Allows the executor to follow the best partial path while D* Lite is still repairing toward the goal.")
    @Group(GENERAL_GROUP) override val allowSprint by c.setting("Allow Sprint", true, "Uses sprint while following longer walk segments.")
    @Group(GENERAL_GROUP) override val lookaheadDistance by c.setting("Lookahead Distance", 0.90, 0.10..3.00, 0.05, "Distance ahead on the active segment that the controller aims for.")
    @Group(GENERAL_GROUP) override val corridorRadius by c.setting("Corridor Radius", 0.75, 0.20..3.00, 0.05, "Maximum horizontal drift from a segment before it is considered lost.")
    @Group(GENERAL_GROUP) override val verticalTolerance by c.setting("Vertical Tolerance", 0.60, 0.10..3.00, 0.05, "Maximum allowed vertical deviation from the active segment.")
    @Group(GENERAL_GROUP) override val backtrackAllowance by c.setting("Backtrack Allowance", 0.55, 0.0..3.0, 0.05, "How far behind a segment start the player may fall before relocalization is required.")
    @Group(GENERAL_GROUP) override val overshootAllowance by c.setting("Overshoot Allowance", 0.70, 0.0..3.0, 0.05, "How far past a segment end the player may travel while still matching that segment.")
    @Group(GENERAL_GROUP) override val reachDistance by c.setting("Reach Distance", 0.28, 0.05..1.0, 0.01, "Soft distance threshold for completing the current segment.")
    @Group(GENERAL_GROUP) override val finalApproachDistance by c.setting("Final Approach Distance", 0.90, 0.10..3.00, 0.05, "Distance from the active segment end where movement starts slowing down.")
    @Group(GENERAL_GROUP) override val minimumThrottle by c.setting("Minimum Throttle", 0.35, 0.05..1.0, 0.05, "Minimum analog movement strength used during the final approach slowdown.")
    @Group(GENERAL_GROUP) override val sprintMinRemaining by c.setting("Sprint Min Remaining", 1.60, 0.0..8.0, 0.1, "Minimum remaining segment distance required before sprint is allowed.")
    @Group(GENERAL_GROUP) override val goalStopSpeed by c.setting("Goal Stop Speed", 0.08, 0.01..0.50, 0.01, "Maximum horizontal speed for goal arrival: the traversal only completes once the player has braked to a stand on the final node instead of sliding through it.", " blocks/tick")
    @Group(GENERAL_GROUP) override val searchBehindSegments by c.setting("Search Behind", 2, 0..16, 1, "How many previous segments relocalization may inspect.")
    @Group(GENERAL_GROUP) override val searchAheadSegments by c.setting("Search Ahead", 6, 0..32, 1, "How many future segments relocalization may inspect.")
    @Group(GENERAL_GROUP) override val relocalizeDistance by c.setting("Relocalize Distance", 2.50, 0.20..10.0, 0.10, "Maximum lateral distance for snapping back onto a nearby segment after displacement.")
    @Group(GENERAL_GROUP) override val maxLostTicks by c.setting("Max Lost Ticks", 8, 1..40, 1, "How many consecutive lost ticks are tolerated before the current traversal is refreshed from the player's new position.")

    @Group(DEBUG_GROUP) override val maxDebugSamples by c.setting("Max Debug Samples", 80, 0..400, 1, "How many recent executor samples to retain for HUD/render debugging.")
    @Group(DEBUG_GROUP) override val logExecutionDebug by c.setting("Log Debug To Console", false, "Writes a compact execution summary to the log on state changes and at the chosen interval.")
    @Group(DEBUG_GROUP) override val logDebugInterval by c.setting("Log Interval", 10, 1..200, 1, "Ticks between periodic executor debug log lines while following.") { logExecutionDebug }
}
