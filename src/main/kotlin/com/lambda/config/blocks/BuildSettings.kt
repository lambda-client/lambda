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
import kotlin.math.max

class BuildSettings(override val c: Config) : BuildConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
        private const val PACKET_LIMITS_GROUP = "Packet Limits"
        private const val REACH_GROUP = "Reach"
        private const val SCAN_GROUP = "Scan"
    }

    @Group(GENERAL_GROUP) override val breakBlocks by c.setting("Break", true, "Break blocks")
    @Group(GENERAL_GROUP) override val placeBlocks by c.setting("Place", true, "Place blocks")
    @Group(GENERAL_GROUP) override val interactBlocks by c.setting("Interact", true, "Interact blocks")

    @Group(GENERAL_GROUP) override val pathing by c.setting("Pathing", false, "Path to blocks")
    @Group(GENERAL_GROUP) override val stayInRange by c.setting("Stay In Range", false, "Stay in range of blocks")
    @Group(GENERAL_GROUP) override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks")
    @Group(GENERAL_GROUP) override val spleefEntities by c.setting("Spleef Entities", false, "Breaks blocks beneath entities blocking placements to get them out of the way")
    @Group(GENERAL_GROUP) override val maxPendingActions by c.setting("Max Pending Actions", 15, 1..30, 1, "The maximum count of pending interactions to allow before pausing future interactions")
    @Group(GENERAL_GROUP) override val actionTimeout by c.setting("Action Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks")
    @Group(GENERAL_GROUP) override val maxBuildDependencies by c.setting("Max Sim Dependencies", 3, 0..10, 1, "Maximum dependency build results")

    @Group(PACKET_LIMITS_GROUP) override val limitTimeframe by c.setting("Limit Timeframe", 310, 50..1500, 1, "The timeframe in which the limit is bound to", "ms")
    @Group(PACKET_LIMITS_GROUP) override val actionLimit by c.setting("Action Limit", 59, 1..100, 1, "The maximum allowed action packets to be sent to the server per given timeframe")
    @Group(PACKET_LIMITS_GROUP) override val interactionLimit by c.setting("Interaction Limit", 9, 1..20, 1, "The maximum allowed interaction packets to be sent to the server per given timeframe")
    @Group(PACKET_LIMITS_GROUP) override val inventoryLimit by c.setting("Inventory Limit", 5, 1..100, 1, "The maximum allowed inventory packets to be sent to the server per given timeframe")

    @Group(REACH_GROUP) override var blockReach by c.setting("Block Reach", 4.5, 1.0..7.0, 0.01, "Maximum block interaction distance")
    @Group(REACH_GROUP) override var entityReach by c.setting("Entity Reach", 3.0, 1.0..7.0, 0.01, "Maximum entity interaction distance")
    override val scanReach: Double get() = max(entityReach, blockReach)

    @Group(SCAN_GROUP) override val checkSideVisibility by c.setting("Visibility Check", false, "Whether to check if an AABB side is visible")
    @Group(SCAN_GROUP) override val strictRayCast by c.setting("Strict Raycast", false, "Whether to include the environment to the ray cast context")
    @Group(SCAN_GROUP) override val resolution by c.setting("Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "") { strictRayCast }
    @Group(SCAN_GROUP) override val pointSelection by c.setting("Point Selection", BuildConfig.PointSelection.Optimum, "The strategy to select the best hit point")
}
