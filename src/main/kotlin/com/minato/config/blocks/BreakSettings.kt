
package com.minato.config.blocks

import com.minato.config.Config
import com.minato.config.ConfigBlock
import com.minato.config.ConfigEditor.hide
import com.minato.config.Group
import com.minato.config.blocks.BreakConfig.AnimationMode
import com.minato.config.blocks.BreakConfig.BreakConfirmationMode
import com.minato.config.blocks.BreakConfig.BreakMode
import com.minato.config.blocks.BreakConfig.SwingMode
import com.minato.config.blocks.BreakConfig.WhitelistMode
import com.minato.config.withEdits
import com.minato.event.events.TickEvent
import com.minato.event.events.TickEvent.Companion.ALL_STAGES
import net.minecraft.registry.Registries
import java.awt.Color

class BreakSettings(override val c: Config) : BreakConfig, ConfigBlock {
	companion object {
		const val COSMETIC_GROUP = "Cosmetic"
	}

	// General
	override val breakMode by c.setting("Break Mode", BreakMode.Packet)
	override val sorter by c.setting("Break Sorter", ActionConfig.SortMode.Tool, "The order in which breaks are performed")
	override val rebreak by c.setting("Rebreak", true, "Re-breaks blocks after they've been broken once")
	// Double break
	override val doubleBreak by c.setting("Double Break", true, "Allows breaking two blocks at once")
	override val unsafeCancels by c.setting("Unsafe Cancels", true, "Allows cancelling block breaking even if the server might continue breaking sever side, potentially causing unexpected state changes") { doubleBreak }
	// Fixes / Delays
	override val breakThreshold by c.setting("Break Threshold", 0.70f, 0.1f..1.0f, 0.01f, "The break amount at which the block is considered broken")
	override val fudgeFactor by c.setting("Fudge Factor", 1, 0..5, 1, "The number of ticks to add to the break time, usually to account for server lag")
	override val serverSwapTicks by c.setting("Server Swap", 0, 0..5, 1, "The number of ticks to give the server time to recognize the player attributes on the swapped item", " tick(s)")
//	@Group(GeneralGroup) override val desyncFix by c.setting("Desync Fix", false, "Predicts if the players breaking will be slowed next tick as block break packets are processed using the players next position") { page == Page.General }
	override val breakDelay by c.setting("Break Delay", 0, 0..6, 1, "The delay between breaking blocks", " tick(s)")
	// Timing
	override val tickStageMask by c.setting("Break Stage Mask", setOf(TickEvent.Input.Post), ALL_STAGES.toSet(), "The sub-tick timing at which break actions can be performed", displayClassName = true)
	override val swapMode by c.setting("Break Swap Mode", BreakConfig.SwapMode.End, "Decides when to swap to the best suited tool when breaking a block")
	override val swing by c.setting("Swing Mode", SwingMode.Constant, "The times at which to swing the players hand")
	override val swingType by c.setting("Break Swing Type", BuildConfig.SwingType.Vanilla, "The style of swing") { swing != SwingMode.None }
	// Rotate
	override val rotate by c.setting("Rotate For Break", false, "Rotate towards block while breaking")
	// Pending / Post
	override val breakConfirmation by c.setting("Break Confirmation", BreakConfirmationMode.BreakThenAwait, "The style of confirmation used when breaking")
	override val breaksPerTick by c.setting("Breaks Per Tick", 30, 1..30, 1, "Maximum instant block breaks per tick")
	override val whitelistMode by c.setting("Whitelist Mode", WhitelistMode.None, "The type of block selection used")
	override val whitelist by c.setting("Whitelist", mutableSetOf(), Registries.BLOCK.toSet(), "Only these selected blocks are allowed to be broken") { whitelistMode == WhitelistMode.Whitelist }
	override val blacklist by c.setting("Blacklist", mutableSetOf(), Registries.BLOCK.toSet(), "These selected blocks are not allowed to be broken") { whitelistMode == WhitelistMode.Blacklist }
	override val avoidFluids by c.setting("Avoid Fluids", true, "Avoids breaking blocks that would cause fluids to spill")
	override val avoidSupporting by c.setting("Avoid Supporting", true, "Avoids breaking the block supporting the player")
	override val fillFluids by c.setting("Fill Fluids", true, "Fills fluids in order to break blocks that would initially spill them") { avoidFluids }
	// Tool
	override val efficientOnly by c.setting("Efficient Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { swapMode.isEnabled() }
	override val suitableToolsOnly by c.setting("Suitable Tools Only", true, "Only use tools suitable for the given block (will get the item drop)") { swapMode.isEnabled() }
	override val forceSilkTouch by c.setting("Force Silk Touch", false, "Force silk touch when breaking blocks") { swapMode.isEnabled() }
	override val forceFortunePickaxe by c.setting("Force Fortune Pickaxe", false, "Force fortune pickaxe when breaking blocks") { swapMode.isEnabled() }
	override val minFortuneLevel by c.setting("Min Fortune Level", 1, 1..3, 1, "The minimum fortune level to use") { swapMode.isEnabled() && forceFortunePickaxe }

	// Cosmetics
	@Group(COSMETIC_GROUP) override val sounds by c.setting("Break Sounds", true, "Plays the breaking sounds")
	@Group(COSMETIC_GROUP) override val particles by c.setting("Particles", true, "Renders the breaking particles")
	@Group(COSMETIC_GROUP) override val breakingTexture by c.setting("Breaking Overlay", true, "Overlays the breaking texture at its different stages")
	// Modes
	@Group(COSMETIC_GROUP) override val renders by c.setting("Renders", true, "Enables the render settings for breaking progress")
	@Group(COSMETIC_GROUP) override val animation by c.setting("Animation", AnimationMode.Out, "The style of animation used for the box") { renders }
	// Fill
	@Group(COSMETIC_GROUP) override val fill by c.setting("Fill", true, "Renders the sides of the box to display break progress") { renders }
	@Group(COSMETIC_GROUP) override val dynamicFillColor by c.setting("Dynamic Colour", true, "Enables fill color interpolation from start to finish for fill when breaking a block") { renders && fill }
	@Group(COSMETIC_GROUP) override val staticFillColor by c.setting("Fill Color", Color(255, 0, 0, 60), "The color of the fill") { renders && !dynamicFillColor && fill }
	@Group(COSMETIC_GROUP) override val startFillColor by c.setting("Start Fill Color", Color(255, 0, 0, 60), "The color of the fill at the start of breaking") { renders && dynamicFillColor && fill }
	@Group(COSMETIC_GROUP) override val endFillColor by c.setting("End Fill Color", Color(0, 255, 0, 60), "The color of the fill at the end of breaking") { renders && dynamicFillColor && fill }
	// Outline
	@Group(COSMETIC_GROUP) override val outline by c.setting("Outline", true, "Renders the lines of the box to display break progress") { renders }
	@Group(COSMETIC_GROUP) override val outlineConfig by c.configBlock(WorldLineSettings(c))
		.withEdits(c) {
			hide(::startColor, ::endColor)
		}
	@Group(COSMETIC_GROUP) override val dynamicOutlineColor by c.setting("Dynamic Outline Color", true, "Enables color interpolation from start to finish for the outline when breaking a block") { renders && outline }
	@Group(COSMETIC_GROUP) override val staticOutlineColor by c.setting("Outline Color", Color.RED, "The Color of the outline at the start of breaking") { renders && !dynamicOutlineColor && outline }
	@Group(COSMETIC_GROUP) override val startOutlineColor by c.setting("Start Outline Color", Color.RED, "The color of the outline at the start of breaking") { renders && dynamicOutlineColor && outline }
	@Group(COSMETIC_GROUP) override val endOutlineColor by c.setting("End Outline Color", Color.GREEN, "The color of the outline at the end of breaking") { renders && dynamicOutlineColor && outline }
}
