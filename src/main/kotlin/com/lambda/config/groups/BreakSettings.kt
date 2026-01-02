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
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.managers.breaking.BreakConfig
import com.lambda.interaction.managers.breaking.BreakConfig.AnimationMode
import com.lambda.interaction.managers.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.managers.breaking.BreakConfig.BreakMode
import com.lambda.interaction.managers.breaking.BreakConfig.SwingMode
import com.lambda.util.NamedEnum
import net.minecraft.block.Block
import java.awt.Color

open class BreakSettings(
    c: Configurable,
    baseGroup: NamedEnum
) : SettingGroup(c), BreakConfig {
    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Cosmetic("Cosmetic")
    }

    // General
    override val breakMode by c.setting("Break Mode", BreakMode.Packet).group(baseGroup, Group.General).index()
    override val sorter by c.setting("Break Sorter", ActionConfig.SortMode.Tool, "The order in which breaks are performed").group(baseGroup, Group.General).index()
    override val rebreak by c.setting("Rebreak", true, "Re-breaks blocks after they've been broken once").group(baseGroup, Group.General).index()

    // Double break
    override val doubleBreak by c.setting("Double Break", true, "Allows breaking two blocks at once").group(baseGroup, Group.General).index()
    override val unsafeCancels by c.setting("Unsafe Cancels", true, "Allows cancelling block breaking even if the server might continue breaking sever side, potentially causing unexpected state changes") { doubleBreak }.group(baseGroup, Group.General).index()

    // Fixes / Delays
    override val breakThreshold by c.setting("Break Threshold", 0.70f, 0.1f..1.0f, 0.01f, "The break amount at which the block is considered broken").group(baseGroup, Group.General).index()
    override val fudgeFactor by c.setting("Fudge Factor", 1, 0..5, 1, "The number of ticks to add to the break time, usually to account for server lag").group(baseGroup, Group.General).index()
    override val serverSwapTicks by c.setting("Server Swap", 0, 0..5, 1, "The number of ticks to give the server time to recognize the player attributes on the swapped item", " tick(s)").group(baseGroup, Group.General).index()

    //    override val desyncFix by c.setting("Desync Fix", false, "Predicts if the players breaking will be slowed next tick as block break packets are processed using the players next position") { vis() && page == Page.General }
    override val breakDelay by c.setting("Break Delay", 0, 0..5, 1, "The delay between breaking blocks", " tick(s)").group(baseGroup, Group.General).index()

    // Timing
    override val tickStageMask by c.setting("Break Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which break actions can be performed", displayClassName = true).group(baseGroup, Group.General).index()

    // Swap
    override val swapMode by c.setting("Break Swap Mode", BreakConfig.SwapMode.End, "Decides when to swap to the best suited tool when breaking a block").group(baseGroup, Group.General).index()

    // Swing
    override val swing by c.setting("Swing Mode", SwingMode.Constant, "The times at which to swing the players hand").group(baseGroup, Group.General).index()
    override val swingType by c.setting("Break Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { swing != SwingMode.None }.group(baseGroup, Group.General).index()

    // Rotate
    override val rotate by c.setting("Rotate For Break", false, "Rotate towards block while breaking").group(baseGroup, Group.General).index()

    // Pending / Post
    override val breakConfirmation by c.setting("Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking").group(baseGroup, Group.General).index()
    override val breaksPerTick by c.setting("Breaks Per Tick", 5, 1..30, 1, "Maximum instant block breaks per tick").group(baseGroup, Group.General).index()

    // Block
    override val ignoredBlocks by c.setting("Ignored Blocks", emptySet<Block>(), description = "Blocks that wont be broken").group(baseGroup, Group.General).index()
    override val avoidLiquids by c.setting("Avoid Liquids", true, "Avoids breaking blocks that would cause liquid to spill").group(baseGroup, Group.General).index()
    override val avoidSupporting by c.setting("Avoid Supporting", true, "Avoids breaking the block supporting the player").group(baseGroup, Group.General).index()

    // Tool
    override val efficientOnly by c.setting("Efficient Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val suitableToolsOnly by c.setting("Suitable Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val forceFortunePickaxe by c.setting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val minFortuneLevel by c.setting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { swapMode.isEnabled() && forceFortunePickaxe }.group(baseGroup, Group.General).index()
    override val useWoodenTools by c.setting("Use Wooden Tools", true, "Use wooden tools when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val useStoneTools by c.setting("Use Stone Tools", true, "Use stone tools when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val useIronTools by c.setting("Use Iron Tools", true, "Use iron tools when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val useDiamondTools by c.setting("Use Diamond Tools", true, "Use diamond tools when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val useGoldTools by c.setting("Use Gold Tools", true, "Use gold tools when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()
    override val useNetheriteTools by c.setting("Use Netherite Tools", true, "Use netherite tools when breaking blocks") { swapMode.isEnabled() }.group(baseGroup, Group.General).index()

    // Cosmetics
    override val sounds by c.setting("Break Sounds", true, "Plays the breaking sounds").group(baseGroup, Group.Cosmetic).index()
    override val particles by c.setting("Particles", true, "Renders the breaking particles").group(baseGroup, Group.Cosmetic).index()
    override val breakingTexture by c.setting("Breaking Overlay", true, "Overlays the breaking texture at its different stages").group(baseGroup, Group.Cosmetic).index()

    // Modes
    override val renders by c.setting("Renders", true, "Enables the render settings for breaking progress").group(baseGroup, Group.Cosmetic).index()
    override val animation by c.setting("Animation", AnimationMode.Out, "The style of animation used for the box") { renders }.group(baseGroup, Group.Cosmetic).index()

    // Fill
    override val fill by c.setting("Fill", true, "Renders the sides of the box to display break progress") { renders }.group(baseGroup, Group.Cosmetic).index()
    override val dynamicFillColor by c.setting("Dynamic Colour", true, "Enables fill color interpolation from start to finish for fill when breaking a block") { renders && fill }.group(baseGroup, Group.Cosmetic).index()
    override val staticFillColor by c.setting("Fill Color", Color(255, 0, 0, 60).brighter(), "The color of the fill") { renders && !dynamicFillColor && fill }.group(baseGroup, Group.Cosmetic).index()
    override val startFillColor by c.setting("Start Fill Color", Color(255, 0, 0, 60).brighter(), "The color of the fill at the start of breaking") { renders && dynamicFillColor && fill }.group(baseGroup, Group.Cosmetic).index()
    override val endFillColor by c.setting("End Fill Color", Color(0, 255, 0, 60).brighter(), "The color of the fill at the end of breaking") { renders && dynamicFillColor && fill }.group(baseGroup, Group.Cosmetic).index()

    // Outline
    override val outline by c.setting("Outline", true, "Renders the lines of the box to display break progress") { renders }.group(baseGroup, Group.Cosmetic).index()
    override val outlineWidth by c.setting("Outline Width", 2, 0..5, 1, "The width of the outline") { renders && outline }.group(baseGroup, Group.Cosmetic).index()
    override val dynamicOutlineColor by c.setting("Dynamic Outline Color", true, "Enables color interpolation from start to finish for the outline when breaking a block") { renders && outline }.group(baseGroup, Group.Cosmetic).index()
    override val staticOutlineColor by c.setting("Outline Color", Color.RED.brighter(), "The Color of the outline at the start of breaking") { renders && !dynamicOutlineColor && outline }.group(baseGroup, Group.Cosmetic).index()
    override val startOutlineColor by c.setting("Start Outline Color", Color.RED.brighter(), "The color of the outline at the start of breaking") { renders && dynamicOutlineColor && outline }.group(baseGroup, Group.Cosmetic).index()
    override val endOutlineColor by c.setting("End Outline Color", Color.GREEN.brighter(), "The color of the outline at the end of breaking") { renders && dynamicOutlineColor && outline }.group(baseGroup, Group.Cosmetic).index()
}
