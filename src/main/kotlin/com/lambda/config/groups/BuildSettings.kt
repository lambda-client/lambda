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
import com.lambda.util.BlockUtils.allSigns
import com.lambda.util.NamedEnum

class BuildSettings(
    c: Configurable,
    baseGroup: NamedEnum,
    vis: () -> Boolean = { true }
) : BuildConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Break("Break"),
        Place("Place")
    }

    // General
    override val pathing by c.setting("Pathing", true, "Path to blocks", vis).group(baseGroup, Group.General)
    override val stayInRange by c.setting("Stay In Range", true, "Stay in range of blocks", vis).group(baseGroup, Group.General)
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks", vis).group(baseGroup, Group.General)
    override val maxPendingInteractions by c.setting("Max Pending Interactions", 1, 1..10, 1, "Dont wait for this many interactions for the server response", visibility = vis).group(baseGroup, Group.General)

    // Breaking
    override val rotateForBreak by c.setting("Rotate For Break", true, "Rotate towards block while breaking", vis).group(baseGroup, Group.Break)
    override val breakConfirmation by c.setting("Break Confirmation", false, "Wait for block break confirmation", vis).group(baseGroup, Group.Break)
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick", visibility = vis).group(baseGroup, Group.Break)
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)", vis).group(baseGroup, Group.Break)
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks", vis).group(baseGroup, Group.Break)
    override val ignoredBlocks by c.setting("Ignored Blocks", allSigns, allSigns, "Blocks that wont be broken", vis).group(baseGroup, Group.Break)

    // Placing
    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing", vis).group(baseGroup, Group.Place)
    override val placeConfirmation by c.setting("Place Confirmation", true, "Wait for block placement confirmation", vis).group(baseGroup, Group.Place)
    override val placementsPerTick by c.setting("Instant Places Per Tick", 1, 1..30, 1, "Maximum instant block places per tick", visibility = vis).group(baseGroup, Group.Place)
    override val interactionTimeout by c.setting("Interaction Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks") { vis() && (placeConfirmation || breakConfirmation) }.group(baseGroup, Group.Break).group(baseGroup, Group.Place)
}
