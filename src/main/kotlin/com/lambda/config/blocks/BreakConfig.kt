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

import com.lambda.util.Describable
import com.lambda.util.NamedEnum
import net.minecraft.block.Block
import java.awt.Color

@Suppress("unused")
interface BreakConfig : ActionConfig {
	val breakMode: BreakMode
	val rebreak: Boolean

	val doubleBreak: Boolean
	val unsafeCancels: Boolean

	val breakThreshold: Float
	val fudgeFactor: Int
	val serverSwapTicks: Int
	//ToDo: Needs a more advanced player simulation implementation to predict the next ticks onGround / submerged status
//    abstract val desyncFix: Boolean
	val breakDelay: Int
	val pauseWhenEating: Boolean

	val swapMode: SwapMode

	val swing: SwingMode
	val swingType: BuildConfig.SwingType

	val rotate: Boolean

	val breakConfirmation: BreakConfirmationMode
	val breaksPerTick: Int

	val avoidFluids: Boolean
	val fillFluids: Boolean
	val avoidSupporting: Boolean
	val whitelistMode: WhitelistMode
	val whitelist: Collection<Block>
	val blacklist: Collection<Block>

	val efficientOnly: Boolean
	val suitableToolsOnly: Boolean
	val forceSilkTouch: Boolean
	val forceFortunePickaxe: Boolean
	val minFortuneLevel: Int

	val sounds: Boolean
	val particles: Boolean
	val breakingTexture: Boolean

	val renders: Boolean
	val fill: Boolean
	val animation: AnimationMode

	val dynamicFillColor: Boolean
	val staticFillColor: Color
	val startFillColor: Color
	val endFillColor: Color

	val outline: Boolean
	val outlineConfig: LineConfig
	val dynamicOutlineColor: Boolean
	val staticOutlineColor: Color
	val startOutlineColor: Color
	val endOutlineColor: Color

	enum class BreakMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		Vanilla("Vanilla", "Uses vanilla breaking"),
		Packet("Packet", "Breaks blocks using only using packets")
	}

	enum class SwapMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		None("None", "Never auto-swap tools. Keeps whatever you’re holding"),
		Start("Start", "Auto-swap to the best tool right when the break starts. No further swaps during the same break"),
		End("End", "Stay on your current tool at first, then auto-swap to the best tool right before the block finishes breaking to speed up the final stretch"),
		StartAndEnd("Start and End", "Auto-swap to the best tool at the start, and again right before the block finishes breaking if it would be faster"),
		Constant("Constant", "Always keep the best tool selected for the entire break. Swaps as needed to maintain optimal speed");

		fun isEnabled() = this != None
	}

	enum class SwingMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		Constant("Constant", "Swings the hand every tick"),
		StartAndEnd("Start and End", "Swings the hand at the start and end of breaking"),
		Start("Start", "Swings the hand at the start of breaking"),
		End("End", "Swings the hand at the end of breaking"),
		None("None", "Does not swing the hand at all");

		fun isEnabled() = this != None
	}

	enum class BreakConfirmationMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		None("No confirmation", "Breaks immediately without waiting for the server. Lowest latency, but can briefly show break effects even if the server later disagrees."),
		BreakThenAwait("Break now, confirm later", "Shows the break effects right away (particles/sounds) and then waits for the server to confirm. Feels instant while keeping results consistent."),
		AwaitThenBreak("Confirm first, then break", "Waits for the server response before showing break effects. Most accurate and safest, but adds a short delay.");
	}

	enum class AnimationMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		None("None", "Does not render any breaking animation"),
		Out("Out", "Renders a growing animation"),
		In("In", "Renders a shrinking animation"),
		OutIn("Out In", "Renders a growing and shrinking animation"),
		InOut("In Out", "Renders a shrinking and growing animation")
	}

	enum class WhitelistMode(
		override val displayName: String,
		override val description: String
	) : NamedEnum, Describable {
		Whitelist("Whitelist", "Only break blocks in the whitelist"),
		Blacklist("Blacklist", "Only break blocks not in the blacklist"),
		None("None", "Breaks all blocks")
	}
}