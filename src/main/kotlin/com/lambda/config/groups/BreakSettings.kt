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
import com.lambda.interaction.request.breaking.BreakConfig
import com.lambda.interaction.request.breaking.BreakConfig.AnimationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.request.breaking.BreakConfig.BreakMode
import com.lambda.interaction.request.breaking.BreakConfig.SortMode
import com.lambda.interaction.request.breaking.BreakConfig.SwingMode
import com.lambda.util.BlockUtils.allSigns
import com.lambda.util.NamedEnum
import java.awt.Color

class BreakSettings(
    c: Configurable,
    groupPath: List<NamedEnum> = emptyList(),
    vis: () -> Boolean = { true },
) : BreakConfig {
    enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Cosmetic("Cosmetic")
    }

    // General
    override val breakMode by c.setting("Break Mode", BreakMode.Packet, visibility = vis).group(groupPath, Group.General)
    override val sorter by c.setting("Sorter", SortMode.Closest, "The order in which breaks are performed", visibility = vis).group(groupPath, Group.General)
    override val rebreak by c.setting("Rebreak", true, "Re-breaks blocks after they've been broken once", visibility = vis).group(groupPath, Group.General)

    // Double break
    override val doubleBreak by c.setting("Double Break", true, "Allows breaking two blocks at once", visibility = vis).group(groupPath, Group.General)
    override val unsafeCancels by c.setting("Unsafe Cancels", true, "Allows cancelling block breaking even if the server might continue breaking sever side, potentially causing unexpected state changes", visibility = vis).group(groupPath, Group.General)

    // Fixes / Delays
    override val breakThreshold by c.setting("Break Threshold", 0.70f, 0.1f..1.0f, 0.01f, "The break amount at which the block is considered broken", visibility = vis).group(groupPath, Group.General)
    override val fudgeFactor by c.setting("Fudge Factor", 2, 0..5, 1, "The amount of ticks to give double, aka secondary breaks extra for the server to recognise the break", visibility = vis).group(groupPath, Group.General)
//    override val desyncFix by c.setting("Desync Fix", false, "Predicts if the players breaking will be slowed next tick as block break packets are processed using the players next position") { vis() && page == Page.General }
    override val breakDelay by c.setting("Break Delay", 0, 0..6, 1, "The delay between breaking blocks", " ticks", visibility = vis).group(groupPath, Group.General)

    // Timing
    override val breakStageMask by c.setting("Break Stage Mask", setOf(TickEvent.Input.Post, TickEvent.Player.Post), description = "The sub-tick timing at which break actions can be performed", visibility = vis).group(groupPath, Group.General)

    // Swap
    override val swapMode by c.setting("Swap Mode", BreakConfig.SwapMode.End, "Decides when to swap to the best suited tool when breaking a block", visibility = vis).group(groupPath, Group.General)

    // Swing
    override val swing by c.setting("Swing Mode", SwingMode.Constant, "The times at which to swing the players hand", visibility = vis).group(groupPath, Group.General)
    override val swingType by c.setting("Break Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { vis() && swing != SwingMode.None }.group(groupPath, Group.General)

    // Rotate
    override val rotateForBreak by c.setting("Rotate For Break", false, "Rotate towards block while breaking", visibility = vis).group(groupPath, Group.General)

    // Pending / Post
    override val breakConfirmation by c.setting("Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking", visibility = vis).group(groupPath, Group.General)
    override val breaksPerTick by c.setting("Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick", visibility = vis).group(groupPath, Group.General)
    override val maxPendingBreaks by c.setting("Max Pending Breaks", 15, 1..30, 1, "The maximum amount of pending breaks", visibility = vis).group(groupPath, Group.General)

    // Block
    override val avoidLiquids by c.setting("Avoid Liquids", true, "Avoids breaking blocks that would cause liquid to spill", visibility = vis).group(groupPath, Group.General)
    override val avoidSupporting by c.setting("Avoid Supporting", true, "Avoids breaking the block supporting the player", visibility = vis).group(groupPath, Group.General)
    override val breakWeakBlocks by c.setting("Break Weak Blocks", false, "Break blocks that dont have structural integrity (e.g: grass)", visibility = vis).group(groupPath, Group.General)
    override val ignoredBlocks by c.setting("Ignored Blocks", allSigns, description = "Blocks that wont be broken", visibility = vis).group(groupPath, Group.General)

    // Tool
    override val suitableToolsOnly by c.setting("Suitable Tools Only", false, "Places a restriction to only use tools suitable for the given block", visibility = vis).group(groupPath, Group.General)
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks", visibility = vis).group(groupPath, Group.General)
    override val forceFortunePickaxe by c.setting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks", visibility = vis).group(groupPath, Group.General)
    override val minFortuneLevel by c.setting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { vis() && forceFortunePickaxe }.group(groupPath, Group.General)

    // Cosmetics
    override val sounds by c.setting("Break Sounds", true, "Plays the breaking sounds", visibility = vis).group(groupPath, Group.Cosmetic)
    override val particles by c.setting("Particles", true, "Renders the breaking particles", visibility = vis).group(groupPath, Group.Cosmetic)
    override val breakingTexture by c.setting("Breaking Overlay", true, "Overlays the breaking texture at its different stages", visibility = vis).group(groupPath, Group.Cosmetic)
    // Modes
    override val renders by c.setting("Renders", true, "Enables the render settings for breaking progress", visibility = vis).group(groupPath, Group.Cosmetic)
    override val animation by c.setting("Animation", AnimationMode.Out, "The style of animation used for the box") { vis() && renders }.group(groupPath, Group.Cosmetic)
    // Fill
    override val fill by c.setting("Fill", true, "Renders the sides of the box to display break progress") { vis() && renders }.group(groupPath, Group.Cosmetic)
    override val dynamicFillColor by c.setting("Dynamic Colour", true, "Enables fill color interpolation from start to finish for fill when breaking a block") { vis() && renders && fill }.group(groupPath, Group.Cosmetic)
    override val staticFillColor by c.setting("Fill Color", Color(255, 0, 0, 60).brighter(), "The color of the fill") { vis() && renders && !dynamicFillColor && fill }.group(groupPath, Group.Cosmetic)
    override val startFillColor by c.setting("Start Fill Color", Color(255, 0, 0, 60).brighter(), "The color of the fill at the start of breaking") { vis()  && renders && dynamicFillColor && fill }.group(groupPath, Group.Cosmetic)
    override val endFillColor by c.setting("End Fill Color", Color(0, 255, 0, 60).brighter(), "The color of the fill at the end of breaking") { vis() && renders && dynamicFillColor && fill }.group(groupPath, Group.Cosmetic)
    // Outline
    override val outline by c.setting("Outline", true, "Renders the lines of the box to display break progress") { vis() && renders }.group(groupPath, Group.Cosmetic)
    override val outlineWidth by c.setting("Outline Width", 2, 0..5, 1, "The width of the outline") { vis() && renders && outline }.group(groupPath, Group.Cosmetic)
    override val dynamicOutlineColor by c.setting("Dynamic Outline Color", true, "Enables color interpolation from start to finish for the outline when breaking a block") { vis() && renders && outline }.group(groupPath, Group.Cosmetic)
    override val staticOutlineColor by c.setting("Outline Color", Color.RED.brighter(), "The Color of the outline at the start of breaking") { vis() && renders && !dynamicOutlineColor && outline }.group(groupPath, Group.Cosmetic)
    override val startOutlineColor by c.setting("Start Outline Color", Color.RED.brighter(), "The color of the outline at the start of breaking") { vis() && renders && dynamicOutlineColor && outline }.group(groupPath, Group.Cosmetic)
    override val endOutlineColor by c.setting("End Outline Color", Color.GREEN.brighter(), "The color of the outline at the end of breaking") { vis() && renders && dynamicOutlineColor && outline }.group(groupPath, Group.Cosmetic)
}
