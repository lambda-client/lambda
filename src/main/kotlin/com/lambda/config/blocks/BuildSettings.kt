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
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.comparable.enumSetting
import com.lambda.config.settings.numeric.doubleSetting
import com.lambda.config.settings.numeric.intSetting
import kotlin.math.max

class BuildSettings(override val c: Config) : BuildConfig, ConfigBlock {
    companion object {
        private const val GENERAL_GROUP = "General"
        private const val PACKET_LIMITS_GROUP = "Packet Limits"
        private const val REACH_GROUP = "Reach"
        private const val SCAN_GROUP = "Scan"
    }

    @Group(GENERAL_GROUP) override val breakBlocks by c.boolSetting("Break", true, "Break blocks")
    @Group(GENERAL_GROUP) override val placeBlocks by c.boolSetting("Place", true, "Place blocks")
    @Group(GENERAL_GROUP) override val interactBlocks by c.boolSetting("Interact", true, "Interact blocks")

    @Group(GENERAL_GROUP) override val pathing by c.boolSetting("Pathing", false, "Path to blocks")
    @Group(GENERAL_GROUP) override val collectDrops by c.boolSetting("Collect All Drops", false, "Collect all drops when breaking blocks")
    @Group(GENERAL_GROUP) override val spleefEntities by c.boolSetting("Spleef Entities", false, "Breaks blocks beneath entities blocking placements to get them out of the way")
    @Group(GENERAL_GROUP) override val cautionDoubleBlocks by c.boolSetting("Caution Double Blocks", true, "Prevents spamming double blocks like doors, chests, etc when configured to interact more than once per tick")
    @Group(GENERAL_GROUP) override val maxPendingActions by c.intSetting("Max Pending Actions", 59, 1..60, 1, "The maximum count of pending interactions to allow before pausing future interactions")
    @Group(GENERAL_GROUP) override val actionTimeout by c.intSetting("Action Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks")
    @Group(GENERAL_GROUP) override val maxBuildDependencies by c.intSetting("Max Sim Dependencies", 3, 0..10, 1, "Maximum dependency build results")

    @Group(PACKET_LIMITS_GROUP) override val limitTimeframe by c.intSetting("Limit Timeframe", 310, 50..1500, 1, "The timeframe in which the limit is bound to", "ms")
    @Group(PACKET_LIMITS_GROUP) override val actionLimit by c.intSetting("Action Limit", 59, 1..100, 1, "The maximum allowed action packets to be sent to the server per given timeframe")
    @Group(PACKET_LIMITS_GROUP) override val interactionLimit by c.intSetting("Interaction Limit", 9, 1..20, 1, "The maximum allowed interaction packets to be sent to the server per given timeframe")
    @Group(PACKET_LIMITS_GROUP) override val inventoryLimit by c.intSetting("Inventory Limit", 5, 1..100, 1, "The maximum allowed inventory packets to be sent to the server per given timeframe")

    @Group(REACH_GROUP) override var blockReach by c.doubleSetting("Block Reach", 4.5, 1.0..7.0, 0.01, "Maximum block interaction distance")
    @Group(REACH_GROUP) override var entityReach by c.doubleSetting("Entity Reach", 3.0, 1.0..7.0, 0.01, "Maximum entity interaction distance")
    override val scanReach: Double get() = max(entityReach, blockReach)

    @Group(SCAN_GROUP) override val checkSideVisibility by c.boolSetting("Visibility Check", false, "Whether to check if an AABB side is visible")
    @Group(SCAN_GROUP) override val strictRayCast by c.boolSetting("Strict Raycast", false, "Whether to include the environment to the ray cast context")
    @Group(SCAN_GROUP) override val resolution by c.intSetting("Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "") { strictRayCast }
    @Group(SCAN_GROUP) override val pointSelection by c.enumSetting("Point Selection", BuildConfig.PointSelection.Optimum, "The strategy to select the best hit point")
}
