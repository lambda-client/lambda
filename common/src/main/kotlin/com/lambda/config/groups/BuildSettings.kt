/*
 * Copyright 2024 Lambda
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

class BuildSettings(
    c: Configurable,
    vis: () -> Boolean = { true }
) : BuildConfig {
    enum class Page {
        General, Break, Place
    }

    private val page by c.setting("Build Page", Page.General, "Current page", vis)

    // General
    override val pathing by c.setting("Pathing", true, "Path to blocks") { vis() && page == Page.General }
    override val stayInRange by c.setting("Stay In Range", true, "Stay in range of blocks") { vis() && page == Page.General && pathing }
    override val collectDrops by c.setting("Collect All Drops", false, "Collect all drops when breaking blocks") { vis() && page == Page.General }
    override val maxPendingInteractions by c.setting("Max Pending Interactions", 1, 1..10, 1, "Dont wait for this many interactions for the server response") { vis() && page == Page.General }

    // Breaking
    override val breakMode by c.setting("Break Mode", BuildConfig.BreakMode.Vanilla) { vis() && page == Page.Break }
    override val breakThreshold by c.setting("Break Threshold", 1.0f, 0.1f..1.0f, 0.02f, "The break amount at which the block is considered broken") { vis() && page == Page.Break }
    override val doubleBreak by c.setting("Double Break", false, "Allows breaking two blocks at once") { vis() && page == Page.Break }
    override val breakDelay by c.setting("Break Delay", 5, 0..5, 1, "The delay between breaking blocks", " ticks") { vis() && page == Page.Break }
    override val sounds by c.setting("Sounds", true, "Plays the breaking sounds") { vis() && page == Page.Break }
    override val particles by c.setting("Particles", true, "Renders the breaking particles") { vis() && page == Page.Break }
    override val breakingTexture by c.setting("Breaking Overlay", true, "Overlays the breaking texture at its different stages") { vis() && page == Page.Break }
    override val rotateForBreak by c.setting("Rotate For Break", true, "Rotate towards block while breaking") { vis() && page == Page.Break }
    override val ignoredBlocks by c.setting("Ignored Blocks", allSigns, "Blocks that wont be broken") { vis() && page == Page.Break }
    override val breakConfirmation by c.setting("Break Confirmation", BuildConfig.BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking") { vis() && page == Page.Break }
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick") { vis() && page == Page.Break }
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)") { vis() && page == Page.Break }
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks") { vis() && page == Page.Break }
    override val forceFortunePickaxe by c.setting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks") { vis() && page == Page.Break }
    override val minFortuneLevel by c.setting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { vis() && page == Page.Break && forceFortunePickaxe }

    // Placing
    override val rotateForPlace by c.setting("Rotate For Place", true, "Rotate towards block while placing") { vis() && page == Page.Place }
    override val placeConfirmation by c.setting("Place Confirmation", true, "Wait for block placement confirmation") { vis() && page == Page.Place }
    override val placementsPerTick by c.setting("Instant Places Per Tick", 1, 1..30, 1, "Maximum instant block places per tick") { vis() && page == Page.Place }

    override val interactionTimeout by c.setting("Interaction Timeout", 10, 1..30, 1, "Timeout for block breaks in ticks", unit = " ticks") { vis() && (page == Page.Place && placeConfirmation || page == Page.Break && breakConfirmation != BuildConfig.BreakConfirmationMode.None) }
}
