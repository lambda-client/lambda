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

import com.lambda.config.Configurable
import com.lambda.config.SettingGroup
import com.lambda.interaction.managers.rotating.visibilty.PointSelection
import com.lambda.util.NamedEnum
import kotlin.math.max

class BuildSettings(
    c: Configurable,
    vararg baseGroup: NamedEnum,
    prefix: String = "",
    override val visibility: () -> Boolean = { true },
) : SettingGroup(c), BuildConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        PacketLimits("Packet Limits"),
        Reach("Reach"),
        Scan("Scan")
    }

    override val breakBlocks by c.setting("${prefix}Break", true, "Break blocks", visibility = visibility).group(*baseGroup, Group.General).index()
    override val placeBlocks by c.setting("${prefix}Place", true, "Place blocks", visibility = visibility).group(*baseGroup, Group.General).index()
    override val interactBlocks by c.setting("${prefix}Interact", true, "Interact blocks", visibility = visibility).group(*baseGroup, Group.General).index()

    override val pathing by c.setting("${prefix}Pathing", false, "Path to blocks", visibility = visibility).group(*baseGroup, Group.General).index()
    override val stayInRange by c.setting("${prefix}Stay In Range", false, "Stay in range of blocks", visibility = visibility).group(*baseGroup, Group.General).index()
    override val collectDrops by c.setting("${prefix}Collect All Drops", false, "Collect all drops when breaking blocks", visibility = visibility).group(*baseGroup, Group.General).index()
    override val spleefEntities by c.setting("${prefix}Spleef Entities", false, "Breaks blocks beneath entities blocking placements to get them out of the way", visibility = visibility).group(*baseGroup, Group.General).index()
    override val maxPendingActions by c.setting("${prefix}Max Pending Actions", 15, 1..30, 1, "The maximum count of pending interactions to allow before pausing future interactions", visibility = visibility).group(*baseGroup, Group.General).index()
    override val actionTimeout by c.setting("${prefix}Action Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks", visibility = visibility).group(*baseGroup, Group.General).index()
    override val maxBuildDependencies by c.setting("${prefix}Max Sim Dependencies", 3, 0..10, 1, "Maximum dependency build results", visibility = visibility).group(*baseGroup, Group.General).index()

    override val limitTimeframe by c.setting("${prefix}Limit Timeframe", 310, 50..1500, 1, "The timeframe in which the limit is bound to", "ms", visibility = visibility).group(*baseGroup, Group.PacketLimits).index()
    override val actionLimit by c.setting("${prefix}Action Limit", 59, 1..100, 1, "The maximum allowed action packets to be sent to the server per given timeframe", visibility = visibility).group(*baseGroup, Group.PacketLimits).index()
    override val interactionPacketLimit by c.setting("Interaction Limit", 9, 1..20, 1, "The maximum allowed interaction packets to be sent to the server per given timeframe", visibility = visibility).group(*baseGroup, Group.PacketLimits).index()

    override var blockReach by c.setting("${prefix}Interact Reach", 4.5, 1.0..7.0, 0.01, "Maximum block interaction distance", visibility = visibility).group(*baseGroup, Group.Reach).index()
    override var entityReach by c.setting("${prefix}Attack Reach", 3.0, 1.0..7.0, 0.01, "Maximum entity interaction distance", visibility = visibility).group(*baseGroup, Group.Reach).index()
    override val scanReach: Double get() = max(entityReach, blockReach)

    override val checkSideVisibility by c.setting("${prefix}Visibility Check", false, "Whether to check if an AABB side is visible", visibility = visibility).group(*baseGroup, Group.Scan).index()
    override val strictRayCast by c.setting("${prefix}Strict Raycast", false, "Whether to include the environment to the ray cast context", visibility = visibility).group(*baseGroup, Group.Scan).index()
    override val resolution by c.setting("${prefix}Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "") { visibility() && strictRayCast }.group(*baseGroup, Group.Scan).index()
    override val pointSelection by c.setting("${prefix}Point Selection", PointSelection.Optimum, "The strategy to select the best hit point", visibility = visibility).group(*baseGroup, Group.Scan).index()
}
