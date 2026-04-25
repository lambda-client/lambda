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
import com.lambda.config.applyEdits
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import com.lambda.interaction.managers.breaking.BreakConfig
import com.lambda.interaction.managers.breaking.BreakConfig.AnimationMode
import com.lambda.interaction.managers.breaking.BreakConfig.BreakConfirmationMode
import com.lambda.interaction.managers.breaking.BreakConfig.BreakMode
import com.lambda.interaction.managers.breaking.BreakConfig.SwingMode
import com.lambda.interaction.managers.breaking.BreakConfig.WhitelistMode
import com.lambda.util.NamedEnum
import net.minecraft.registry.Registries
import java.awt.Color

open class BreakSettings(
	c: Configurable,
	vararg baseGroup: NamedEnum,
	prefix: String = "",
	override val visibility: () -> Boolean = { true },
) : SettingGroup(c), BreakConfig {
	enum class Group(override val displayName: String) : NamedEnum {
		General("General"),
		Cosmetic("Cosmetic")
	}

	// General
	override val breakMode by c.setting("${prefix}Break Mode", BreakMode.Packet, visibility = visibility).group(*baseGroup, Group.General).index()
	override val sorter by c.setting("${prefix}Break Sorter", ActionConfig.SortMode.Tool, "The order in which breaks are performed", visibility = visibility).group(*baseGroup, Group.General).index()
	override val rebreak by c.setting("${prefix}Rebreak", true, "Re-breaks blocks after they've been broken once", visibility = visibility).group(*baseGroup, Group.General).index()

	// Double break
	override val doubleBreak by c.setting("${prefix}Double Break", true, "Allows breaking two blocks at once", visibility = visibility).group(*baseGroup, Group.General).index()
	override val unsafeCancels by c.setting("${prefix}Unsafe Cancels", true, "Allows cancelling block breaking even if the server might continue breaking sever side, potentially causing unexpected state changes") { visibility() && doubleBreak }.group(*baseGroup, Group.General).index()

	// Fixes / Delays
	override val breakThreshold by c.setting("${prefix}Break Threshold", 0.70f, 0.1f..1.0f, 0.01f, "The break amount at which the block is considered broken", visibility = visibility).group(*baseGroup, Group.General).index()
	override val fudgeFactor by c.setting("${prefix}Fudge Factor", 1, 0..5, 1, "The number of ticks to add to the break time, usually to account for server lag", visibility = visibility).group(*baseGroup, Group.General).index()
	override val serverSwapTicks by c.setting("${prefix}Server Swap", 0, 0..5, 1, "The number of ticks to give the server time to recognize the player attributes on the swapped item", " tick(s)", visibility = visibility).group(*baseGroup, Group.General).index()

	//    override val desyncFix by c.setting("Desync Fix", false, "Predicts if the players breaking will be slowed next tick as block break packets are processed using the players next position") { vis() && page == Page.General }
	override val breakDelay by c.setting("${prefix}Break Delay", 0, 0..6, 1, "The delay between breaking blocks", " tick(s)", visibility = visibility).group(*baseGroup, Group.General).index()

	// Timing
	override val tickStageMask by c.setting("${prefix}Break Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which break actions can be performed", displayClassName = true, visibility = visibility).group(*baseGroup, Group.General).index()

	// Swap
	override val swapMode by c.setting("${prefix}Break Swap Mode", BreakConfig.SwapMode.End, "Decides when to swap to the best suited tool when breaking a block", visibility = visibility).group(*baseGroup, Group.General).index()

	// Swing
	override val swing by c.setting("${prefix}Swing Mode", SwingMode.Constant, "The times at which to swing the players hand", visibility = visibility).group(*baseGroup, Group.General).index()
	override val swingType by c.setting("${prefix}Break Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { visibility() && swing != SwingMode.None }.group(*baseGroup, Group.General).index()

	// Rotate
	override val rotate by c.setting("${prefix}Rotate For Break", false, "Rotate towards block while breaking", visibility = visibility).group(*baseGroup, Group.General).index()

	// Pending / Post
	override val breakConfirmation by c.setting("${prefix}Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking", visibility = visibility).group(*baseGroup, Group.General).index()
	override val breaksPerTick by c.setting("${prefix}Breaks Per Tick", 30, 1..30, 1, "Maximum instant block breaks per tick", visibility = visibility).group(*baseGroup, Group.General).index()

	// Block
	override val whitelistMode by c.setting("${prefix}Whitelist Mode", WhitelistMode.None, "The type of block selection used", visibility = visibility).group(*baseGroup, Group.General).index()
	override val whitelist by c.setting("${prefix}Whitelist", mutableSetOf(), Registries.BLOCK.toMutableSet(), "Only these selected blocks are allowed to be broken") { visibility() && whitelistMode == WhitelistMode.Whitelist }.group(*baseGroup, Group.General).index()
	override val blacklist by c.setting("${prefix}Blacklist", mutableSetOf(), Registries.BLOCK.toMutableSet(), "These selected blocks are not allowed to be broken") { visibility() && whitelistMode == WhitelistMode.Blacklist }.group(*baseGroup, Group.General).index()
	override val avoidFluids by c.setting("${prefix}Avoid Fluids", true, "Avoids breaking blocks that would cause fluids to spill", visibility = visibility).group(*baseGroup, Group.General).index()
	override val avoidSupporting by c.setting("${prefix}Avoid Supporting", true, "Avoids breaking the block supporting the player", visibility = visibility).group(*baseGroup, Group.General).index()
	override val fillFluids by c.setting("Fill Fluids", true, "Fills fluids in order to break blocks that would initially spill them") { visibility() && avoidFluids }.group(*baseGroup, Group.General).index()
	// Tool
	override val efficientOnly by c.setting("${prefix}Efficient Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { visibility() && swapMode.isEnabled() }.group(*baseGroup, Group.General).index()
	override val suitableToolsOnly by c.setting("${prefix}Suitable Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { visibility() && swapMode.isEnabled() }.group(*baseGroup, Group.General).index()
	override val forceSilkTouch by c.setting("${prefix}Force Silk Touch", false, "Force silk touch when breaking blocks") { visibility() && swapMode.isEnabled() }.group(*baseGroup, Group.General).index()
	override val forceFortunePickaxe by c.setting("${prefix}Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks") { visibility() && swapMode.isEnabled() }.group(*baseGroup, Group.General).index()
	override val minFortuneLevel by c.setting("${prefix}Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { visibility() && swapMode.isEnabled() && forceFortunePickaxe }.group(*baseGroup, Group.General).index()

	// Cosmetics
	override val sounds by c.setting("${prefix}Break Sounds", true, "Plays the breaking sounds", visibility = visibility).group(*baseGroup, Group.Cosmetic).index()
	override val particles by c.setting("${prefix}Particles", true, "Renders the breaking particles", visibility = visibility).group(*baseGroup, Group.Cosmetic).index()
	override val breakingTexture by c.setting("${prefix}Breaking Overlay", true, "Overlays the breaking texture at its different stages", visibility = visibility).group(*baseGroup, Group.Cosmetic).index()

	// Modes
	override val renders by c.setting("${prefix}Renders", true, "Enables the render settings for breaking progress", visibility = visibility).group(*baseGroup, Group.Cosmetic).index()
	override val animation by c.setting("${prefix}Animation", AnimationMode.Out, "The style of animation used for the box") { visibility() && renders }.group(*baseGroup, Group.Cosmetic).index()

	// Fill
	override val fill by c.setting("${prefix}Fill", true, "Renders the sides of the box to display break progress") { visibility() && renders }.group(*baseGroup, Group.Cosmetic).index()
	override val dynamicFillColor by c.setting("${prefix}Dynamic Colour", true, "Enables fill color interpolation from start to finish for fill when breaking a block") { visibility() && renders && fill }.group(*baseGroup, Group.Cosmetic).index()
	override val staticFillColor by c.setting("${prefix}Fill Color", Color(255, 0, 0, 60).brighter(), "The color of the fill") { visibility() && renders && !dynamicFillColor && fill }.group(*baseGroup, Group.Cosmetic).index()
	override val startFillColor by c.setting("${prefix}Start Fill Color", Color(255, 0, 0, 60).brighter(), "The color of the fill at the start of breaking") { visibility() && renders && dynamicFillColor && fill }.group(*baseGroup, Group.Cosmetic).index()
	override val endFillColor by c.setting("${prefix}End Fill Color", Color(0, 255, 0, 60).brighter(), "The color of the fill at the end of breaking") { visibility() && renders && dynamicFillColor && fill }.group(*baseGroup, Group.Cosmetic).index()

	// Outline
	override val outline by c.setting("${prefix}Outline", true, "Renders the lines of the box to display break progress") { visibility() && renders }.group(*baseGroup, Group.Cosmetic).index()
	override val outlineConfig = WorldLineSettings(c, *baseGroup, Group.Cosmetic, prefix = "${prefix}Outline ") { visibility() && outline }.apply {
		c.applyEdits {
			hide(::startColor, ::endColor)
		}
	}
	override val dynamicOutlineColor by c.setting("${prefix}Dynamic Outline Color", true, "Enables color interpolation from start to finish for the outline when breaking a block") { visibility() && renders && outline }.group(*baseGroup, Group.Cosmetic).index()
	override val staticOutlineColor by c.setting("${prefix}Outline Color", Color.RED.brighter(), "The Color of the outline at the start of breaking") { visibility() && renders && !dynamicOutlineColor && outline }.group(*baseGroup, Group.Cosmetic).index()
	override val startOutlineColor by c.setting("${prefix}Start Outline Color", Color.RED.brighter(), "The color of the outline at the start of breaking") { visibility() && renders && dynamicOutlineColor && outline }.group(*baseGroup, Group.Cosmetic).index()
	override val endOutlineColor by c.setting("${prefix}End Outline Color", Color.GREEN.brighter(), "The color of the outline at the end of breaking") { visibility() && renders && dynamicOutlineColor && outline }.group(*baseGroup, Group.Cosmetic).index()
}
