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
import com.lambda.event.events.TickEvent
import com.lambda.interaction.request.Priority
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.util.BlockUtils.allSigns

class BreakSettings(
    c: Configurable,
    priority: Priority = 0,
    vis: () -> Boolean = { true }
) : BreakConfig(priority) {
    override val breakMode by c.setting("Break Mode", BreakMode.Packet, visibility = vis)
    override val reBreak by c.setting("ReBreak", true, "Re-breaks blocks after they've been broken once", visibility = vis)
    override val unsafeCancels by c.setting("Unsafe Cancels", true, "Allows cancelling block breaking even if the server might continue breaking sever side, potentially causing unexpected state changes", visibility = vis)
    override val breakThreshold by c.setting("Break Threshold", 0.75f, 0.1f..1.0f, 0.01f, "The break amount at which the block is considered broken", visibility = vis)
    override val doubleBreak by c.setting("Double Break", true, "Allows breaking two blocks at once", visibility = vis)
    override val fudgeFactor by c.setting("Fudge Factor", 2, 0..5, 1, "The amount of ticks to give double, aka secondary breaks extra for the server to recognise the break", visibility = vis)
    override val breakDelay by c.setting("Break Delay", 0, 0..6, 1, "The delay between breaking blocks", " ticks", visibility = vis)
    override val breakStageMask by c.setting("Break Stage Mask", setOf(TickEvent.Pre, TickEvent.Input.Pre, TickEvent.Input.Post, TickEvent.Player.Post), "The sub-tick timing at which break actions can be performed", visibility = vis)
    override val swing by c.setting("Swing Mode", SwingMode.Constant, "The times at which to swing the players hand", visibility = vis)
    override val swingType by c.setting("Break Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { vis() && swing != SwingMode.None }
    override val sounds by c.setting("Break Sounds", true, "Plays the breaking sounds", visibility = vis)
    override val particles by c.setting("Particles", true, "Renders the breaking particles", visibility = vis)
    override val breakingTexture by c.setting("Breaking Overlay", true, "Overlays the breaking texture at its different stages", visibility = vis)
    override val rotateForBreak by c.setting("Rotate For Break", false, "Rotate towards block while breaking", visibility = vis)
    override val ignoredBlocks by c.setting("Ignored Blocks", allSigns, "Blocks that wont be broken", visibility = vis)
    override val breakConfirmation by c.setting("Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking", visibility = vis)
    override val maxPendingBreaks by c.setting("Max Pending Breaks", 15, 1..30, 1, "The maximum amount of pending breaks", visibility = vis)
    override val breaksPerTick by c.setting("Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick", visibility = vis)
    override val suitableToolsOnly by c.setting("Suitable Tools Only", false, "Places a restriction to only use tools suitable for the given block", visibility = vis)
    override val avoidLiquids by c.setting("Avoid Liquids", true, "Avoids breaking blocks that would cause liquid to spill", visibility = vis)
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)", visibility = vis)
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks", visibility = vis)
    override val forceFortunePickaxe by c.setting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks", visibility = vis)
    override val minFortuneLevel by c.setting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { vis() && forceFortunePickaxe }
}
