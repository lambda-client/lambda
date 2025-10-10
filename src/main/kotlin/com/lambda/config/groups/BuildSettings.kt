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
import com.lambda.interaction.request.rotating.visibilty.PointSelection
import com.lambda.util.NamedEnum
import kotlin.math.max

class BuildSettings(
    c: Configurable,
    vararg baseGroup: NamedEnum,
    vis: () -> Boolean = { true },
) : BuildConfig, SettingGroup(c, c.settings.size) {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Reach("Reach"),
        Scan("Scan")
    }

    // General
    override val pathing by c.setting("Pathing", true, "Path to blocks", vis).group(*baseGroup, Group.General)
    override val stayInRange by c.setting("Stay In Range", true, "Stay in range of blocks", vis).group(*baseGroup, Group.General)
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks", vis).group(*baseGroup, Group.General)
    override val interactionsPerTick by c.setting("Interactions Per Tick", 5, 1..30, 1, "The amount of interactions that can happen per tick", visibility = vis).group(*baseGroup, Group.General)
    override val maxPendingInteractions by c.setting("Max Pending Interactions", 15, 1..30, 1, "The maximum count of pending interactions to allow before pausing future interactions", visibility = vis).group(*baseGroup, Group.General)

    override val useDefaultReach by c.setting("Default Reach", true, "Whether to use vanilla interaction ranges", vis).group(*baseGroup, Group.Reach)
    override val attackReach by c.setting("Attack Reach", DEFAULT_ATTACK_REACH, 1.0..10.0, 0.01, "Maximum entity interaction distance") { vis() && !useDefaultReach }.group(*baseGroup, Group.Reach)
    override val interactReach by c.setting("Interact Reach", DEFAULT_INTERACT_REACH, 1.0..10.0, 0.01, "Maximum block interaction distance") { vis() && !useDefaultReach }.group(*baseGroup, Group.Reach)

    override val scanReach: Double get() = max(attackReach, interactReach)

    override val strictRayCast by c.setting("Strict Raycast", false, "Whether to include the environment to the ray cast context", vis).group(*baseGroup, Group.Scan)
    override val checkSideVisibility by c.setting("Visibility Check", true, "Whether to check if an AABB side is visible", vis).group(*baseGroup, Group.Scan)
    override val resolution by c.setting("Resolution", 5, 1..20, 1, "The amount of grid divisions per surface of the hit box", "", vis).group(*baseGroup, Group.Scan)
    override val pointSelection by c.setting("Point Selection", PointSelection.Optimum, "The strategy to select the best hit point", vis).group(*baseGroup, Group.Scan)

    override val interactionTimeout by c.setting("Interaction Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks", visibility = vis)

    companion object {
        const val DEFAULT_ATTACK_REACH = 3.0
        const val DEFAULT_INTERACT_REACH = 4.5
    }
}
