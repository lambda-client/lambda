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
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.util.BlockUtils.allSigns

class BreakSettings(
    c: Configurable,
    priority: Priority = 0,
    vis: () -> Boolean = { true }
) : BreakConfig(priority) {
    override val breakMode by c.setting("Break Mode", BreakMode.Vanilla) { vis() }
    override val breakThreshold by c.setting("Break Threshold", 1.0f, 0.1f..1.0f, 0.02f, "The break amount at which the block is considered broken") { vis() }
    override val doubleBreak by c.setting("Double Break", false, "Allows breaking two blocks at once") { vis() }
    override val breakDelay by c.setting("Break Delay", 5, 0..5, 1, "The delay between breaking blocks", " ticks") { vis() }
    override val sounds by c.setting("Sounds", true, "Plays the breaking sounds") { vis() }
    override val particles by c.setting("Particles", true, "Renders the breaking particles") { vis() }
    override val breakingTexture by c.setting("Breaking Overlay", true, "Overlays the breaking texture at its different stages") { vis() }
    override val rotateForBreak by c.setting("Rotate For Break", true, "Rotate towards block while breaking") { vis() }
    override val ignoredBlocks by c.setting("Ignored Blocks", allSigns, "Blocks that wont be broken") { vis() }
    override val breakConfirmation by c.setting("Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking") { vis() }
    override val breaksPerTick by c.setting("Instant Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick") { vis() }
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)") { vis() }
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks") { vis() }
    override val forceFortunePickaxe by c.setting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks") { vis() }
    override val minFortuneLevel by c.setting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { vis() && forceFortunePickaxe }
}