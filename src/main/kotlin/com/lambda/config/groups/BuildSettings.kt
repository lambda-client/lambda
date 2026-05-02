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

package com.lambda.config.groups

import com.lambda.config.Config
import com.lambda.config.Config.Group
import com.lambda.config.SettingBlock
import com.lambda.config.groups.BuildConfig.PointSelection
import kotlin.math.max

class BuildSettings(override val c: Config) : BuildConfig {
    companion object {
        private const val GROUP_GENERAL = "General"
        private const val GROUP_PACKET_LIMITS = "Packet Limits"
        private const val GROUP_REACH = "Reach"
        private const val GROUP_SCAN = "Scan"
    }

    @Group(GROUP_GENERAL) override val breakBlocks by c.setting("Break", true, "Break blocks")
    @Group(GROUP_GENERAL) override val placeBlocks by c.setting("Place", true, "Place blocks")
    @Group(GROUP_GENERAL) override val interactBlocks by c.setting("Interact", true, "Interact blocks")

    @Group(GROUP_GENERAL) override val pathing by c.setting("Pathing", false, "Path to blocks")
    @Group(GROUP_GENERAL) override val stayInRange by c.setting("Stay In Range", false, "Stay in range of blocks")
    @Group(GROUP_GENERAL) override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks")
    @Group(GROUP_GENERAL) override val spleefEntities by c.setting("Spleef Entities", false, "Breaks blocks beneath entities blocking placements to get them out of the way")
    @Group(GROUP_GENERAL) override val maxPendingActions by c.setting("Max Pending Actions", 15, 1..30, 1, "The maximum count of pending interactions to allow before pausing future interactions")
    @Group(GROUP_GENERAL) override val actionTimeout by c.setting("Action Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks")
    @Group(GROUP_GENERAL) override val maxBuildDependencies by c.setting("Max Sim Dependencies", 3, 0..10, 1, "Maximum dependency build results")

    @Group(GROUP_PACKET_LIMITS) override val limitTimeframe by c.setting("Limit Timeframe", 310, 50..1500, 1, "The timeframe in which the limit is bound to", "ms")
    @Group(GROUP_PACKET_LIMITS) override val actionLimit by c.setting("Action Limit", 59, 1..100, 1, "The maximum allowed action packets to be sent to the server per given timeframe")
    @Group(GROUP_PACKET_LIMITS) override val interactionLimit by c.setting("Interaction Limit", 9, 1..20, 1, "The maximum allowed interaction packets to be sent to the server per given timeframe")
    @Group(GROUP_PACKET_LIMITS) override val inventoryLimit by c.setting("Inventory Limit", 5, 1..100, 1, "The maximum allowed inventory packets to be sent to the server per given timeframe")

    @Group(GROUP_REACH) override var blockReach by c.setting("Interact Reach", 4.5, 1.0..7.0, 0.01, "Maximum block interaction distance")
    @Group(GROUP_REACH) override var entityReach by c.setting("Attack Reach", 3.0, 1.0..7.0, 0.01, "Maximum entity interaction distance")
    override val scanReach: Double get() = max(entityReach, blockReach)

    @Group(GROUP_SCAN) override val checkSideVisibility by c.setting("Visibility Check", false, "Whether to check if an AABB side is visible")
    @Group(GROUP_SCAN) override val strictRayCast by c.setting("Strict Raycast", false, "Whether to include the environment to the ray cast context")
    @Group(GROUP_SCAN) override val resolution by c.setting("Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "") { strictRayCast }
    @Group(GROUP_SCAN) override val pointSelection by c.setting("Point Selection", PointSelection.Optimum, "The strategy to select the best hit point")
}
