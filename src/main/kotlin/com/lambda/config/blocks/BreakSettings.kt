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

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group
import com.lambda.config.blocks.ActionConfig.SortMode
import com.lambda.config.blocks.BreakConfig.AnimationMode
import com.lambda.config.blocks.BreakConfig.BreakConfirmationMode
import com.lambda.config.blocks.BreakConfig.BreakMode
import com.lambda.config.blocks.BreakConfig.SwapMode
import com.lambda.config.blocks.BreakConfig.SwingMode
import com.lambda.config.blocks.BreakConfig.WhitelistMode
import com.lambda.config.blocks.BuildConfig.SwingType
import com.lambda.config.entries.onValueChangeUnsafe
import com.lambda.config.hide
import com.lambda.config.settings.collections.blockCollectionSetting
import com.lambda.config.settings.collections.collectionSetting
import com.lambda.config.settings.comparable.boolSetting
import com.lambda.config.settings.comparable.enumSetting
import com.lambda.config.settings.complex.colorSetting
import com.lambda.config.settings.numeric.floatSetting
import com.lambda.config.settings.numeric.intSetting
import com.lambda.config.withEdits
import com.lambda.event.events.TickEvent
import com.lambda.event.events.TickEvent.Companion.ALL_STAGES
import net.minecraft.registry.Registries
import java.awt.Color

class BreakSettings(override val c: Config) : BreakConfig, ConfigBlock {
	companion object {
		const val COSMETIC_GROUP = "Cosmetic"
	}

	// General
	override val breakMode by c.enumSetting("Break Mode", BreakMode.OldGrim)
	override val sorter by c.enumSetting("Break Sorter", SortMode.Tool, "The order in which breaks are performed")
	override val rebreak by c.boolSetting("Rebreak", true, "Re-breaks blocks after they've been broken once")
	// Double break
	override val doubleBreak by c.boolSetting("Double Break", true, "Allows breaking two blocks at once")
	override val unsafeCancels by c.boolSetting("Unsafe Cancels", true, "Allows cancelling block breaking even if the server might continue breaking sever side, potentially causing unexpected state changes") { doubleBreak }
	// Fixes / Delays
	override val breakThreshold by c.floatSetting("Break Threshold", 0.70f, 0.1f..1.0f, 0.01f, "The break amount at which the block is considered broken")
	override val fudgeFactor by c.intSetting("Fudge Factor", 1, 0..5, 1, "The number of ticks to add to the break time, usually to account for server lag")
	override val serverSwapTicks by c.intSetting("Server Swap", 0, 0..5, 1, "The number of ticks to give the server time to recognize the player attributes on the swapped item", " tick(s)")
//	@Group(GeneralGroup) override val desyncFix by c.setting("Desync Fix", false, "Predicts if the players breaking will be slowed next tick as block break packets are processed using the players next position") { page == Page.General }
	override val breakDelay by c.intSetting("Break Delay", 0, 0..6, 1, "The delay between breaking blocks", " tick(s)")
	// Timing
	override val tickStageMask by c.collectionSetting("Break Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which break actions can be performed", displayClassName = true)
	override val swapMode by c.enumSetting("Break Swap Mode", SwapMode.End, "Decides when to swap to the best suited tool when breaking a block")
	override val swing by c.enumSetting("Swing Mode", SwingMode.Constant, "The times at which to swing the players hand")
	override val swingType by c.enumSetting("Break Swing Type", SwingType.Vanilla, "The style of swing") { swing != SwingMode.None }
	// Rotate
	override val rotate by c.boolSetting("Rotate For Break", false, "Rotate towards block while breaking")
	// Pending / Post
	override val breakConfirmation by c.enumSetting("Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking")
	override val breaksPerTick by c.intSetting("Breaks Per Tick", 59, 1..60, 1, "Maximum instant block breaks per tick")
	override val whitelistMode by c.enumSetting("Whitelist Mode", WhitelistMode.None, "The type of block selection used")
	override val whitelist by c.blockCollectionSetting("Whitelist", mutableSetOf(), Registries.BLOCK.toSet(), "Only these selected blocks are allowed to be broken") { whitelistMode == WhitelistMode.Whitelist }
	override val blacklist by c.blockCollectionSetting("Blacklist", mutableSetOf(), Registries.BLOCK.toSet(), "These selected blocks are not allowed to be broken") { whitelistMode == WhitelistMode.Blacklist }
	override val avoidFluids by c.boolSetting("Avoid Fluids", true, "Avoids breaking blocks that would cause fluids to spill")
	override val avoidSupporting by c.boolSetting("Avoid Supporting", true, "Avoids breaking the block supporting the player")
	override val fillFluids by c.boolSetting("Fill Fluids", true, "Fills fluids in order to break blocks that would initially spill them") { avoidFluids }
	// Tool
	override val efficientOnly by c.boolSetting("Efficient Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { swapMode.isEnabled() }
	override val suitableToolsOnly by c.boolSetting("Suitable Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { swapMode.isEnabled() }
	override var forceSilkTouch: Boolean by c.boolSetting("Force Silk Touch", false, "Force silk touch when breaking blocks") { swapMode.isEnabled() }
		.onValueChangeUnsafe { _, to -> if (to) avoidSilkTouch = false }
	override var avoidSilkTouch: Boolean by c.boolSetting("Avoid Silk Touch", false, "Never use silk touch tools when breaking blocks") { swapMode.isEnabled() }
		.onValueChangeUnsafe { _, to -> if (to) forceSilkTouch = false }
	override val forceFortunePickaxe by c.boolSetting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks") { swapMode.isEnabled() }
	override val minFortuneLevel by c.intSetting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { swapMode.isEnabled() && forceFortunePickaxe }

	// Cosmetics
	@Group(COSMETIC_GROUP) override val sounds by c.boolSetting("Break Sounds", true, "Plays the breaking sounds")
	@Group(COSMETIC_GROUP) override val particles by c.boolSetting("Particles", true, "Renders the breaking particles")
	@Group(COSMETIC_GROUP) override val breakingTexture by c.boolSetting("Breaking Overlay", true, "Overlays the breaking texture at its different stages")
	// Modes
	@Group(COSMETIC_GROUP) override val renders by c.boolSetting("Renders", true, "Enables the render settings for breaking progress")
	@Group(COSMETIC_GROUP) override val animation by c.enumSetting("Animation", AnimationMode.Out, "The style of animation used for the box") { renders }
	// Fill
	@Group(COSMETIC_GROUP) override val fill by c.boolSetting("Fill", true, "Renders the sides of the box to display break progress") { renders }
	@Group(COSMETIC_GROUP) override val dynamicFillColor by c.boolSetting("Dynamic Colour", true, "Enables fill color interpolation from start to finish for fill when breaking a block") { renders && fill }
	@Group(COSMETIC_GROUP) override val staticFillColor by c.colorSetting("Fill Color", Color(255, 0, 0, 60), "The color of the fill") { renders && !dynamicFillColor && fill }
	@Group(COSMETIC_GROUP) override val startFillColor by c.colorSetting("Start Fill Color", Color(255, 0, 0, 60), "The color of the fill at the start of breaking") { renders && dynamicFillColor && fill }
	@Group(COSMETIC_GROUP) override val endFillColor by c.colorSetting("End Fill Color", Color(0, 255, 0, 60), "The color of the fill at the end of breaking") { renders && dynamicFillColor && fill }
	// Outline
	@Group(COSMETIC_GROUP) override val outline by c.boolSetting("Outline", true, "Renders the lines of the box to display break progress") { renders }
	@Group(COSMETIC_GROUP) override val outlineConfig by c.configBlock(WorldLineSettings(c))
		.withEdits(c) {
			hide(::startColor, ::endColor)
		}
	@Group(COSMETIC_GROUP) override val dynamicOutlineColor by c.boolSetting("Dynamic Outline Color", true, "Enables color interpolation from start to finish for the outline when breaking a block") { renders && outline }
	@Group(COSMETIC_GROUP) override val staticOutlineColor by c.colorSetting("Outline Color", Color.RED, "The Color of the outline at the start of breaking") { renders && !dynamicOutlineColor && outline }
	@Group(COSMETIC_GROUP) override val startOutlineColor by c.colorSetting("Start Outline Color", Color.RED, "The color of the outline at the start of breaking") { renders && dynamicOutlineColor && outline }
	@Group(COSMETIC_GROUP) override val endOutlineColor by c.colorSetting("End Outline Color", Color.GREEN, "The color of the outline at the end of breaking") { renders && dynamicOutlineColor && outline }
}
