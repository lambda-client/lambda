/*
 * Copyright 2025 Lambda
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
    baseGroup: NamedEnum,
) : SettingGroup(c), BuildConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Reach("Reach"),
        Scan("Scan")
    }

    override val breakBlocks by c.setting("Break", true, "Break blocks").group(baseGroup, Group.General).index()
    override val interactBlocks by c.setting("Place / Interact", true, "Interact blocks").group(baseGroup, Group.General).index()

    override val pathing by c.setting("Pathing", true, "Path to blocks").group(baseGroup, Group.General).index()
    override val stayInRange by c.setting("Stay In Range", true, "Stay in range of blocks").group(baseGroup, Group.General).index()
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks").group(baseGroup, Group.General).index()
    override val spleefEntities by c.setting("Spleef Entities", false, "Breaks blocks beneath entities blocking placements to get them out of the way").group(baseGroup, Group.General).index()
    override val maxPendingActions by c.setting("Max Pending Actions", 15, 1..30, 1, "The maximum count of pending interactions to allow before pausing future interactions").group(baseGroup, Group.General).index()
    override val actionTimeout by c.setting("Action Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks").group(baseGroup, Group.General).index()
    override val maxBuildDependencies by c.setting("Max Sim Dependencies", 3, 0..10, 1, "Maximum dependency build results").group(baseGroup, Group.General).index()

    override var blockReach by c.setting("Interact Reach", 4.5, 1.0..7.0, 0.01, "Maximum block interaction distance").group(baseGroup, Group.Reach).index()
    override var entityReach by c.setting("Attack Reach", 3.0, 1.0..7.0, 0.01, "Maximum entity interaction distance").group(baseGroup, Group.Reach).index()
    override val scanReach: Double get() = max(entityReach, blockReach)

    override val checkSideVisibility by c.setting("Visibility Check", true, "Whether to check if an AABB side is visible").group(baseGroup, Group.Scan).index()
    override val strictRayCast by c.setting("Strict Raycast", false, "Whether to include the environment to the ray cast context").group(baseGroup, Group.Scan).index()
    override val resolution by c.setting("Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "") { strictRayCast }.group(baseGroup, Group.Scan).index()
    override val pointSelection by c.setting("Point Selection", PointSelection.Optimum, "The strategy to select the best hit point").group(baseGroup, Group.Scan).index()
}
