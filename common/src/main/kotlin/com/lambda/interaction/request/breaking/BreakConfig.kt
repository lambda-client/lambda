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

package com.lambda.interaction.request.breaking

import com.lambda.config.groups.BuildConfig
import com.lambda.event.Event
import com.lambda.interaction.request.RequestConfig
import net.minecraft.block.Block
import java.awt.Color

abstract class BreakConfig : RequestConfig<BreakRequest>() {
    abstract val breakMode: BreakMode
    abstract val reBreak: Boolean
    abstract val unsafeCancels: Boolean
    abstract val breakThreshold: Float
    abstract val doubleBreak: Boolean
    abstract val fudgeFactor: Int
    abstract val desyncFix: Boolean
    abstract val breakDelay: Int
    abstract val breakStageMask: Set<Event>
    abstract val swing: SwingMode
    abstract val swingType: BuildConfig.SwingType
    abstract val sounds: Boolean
    abstract val particles: Boolean
    abstract val breakingTexture: Boolean
    abstract val rotateForBreak: Boolean
    abstract val breakConfirmation: BreakConfirmationMode
    abstract val maxPendingBreaks: Int
    abstract val breaksPerTick: Int
    abstract val suitableToolsOnly: Boolean
    abstract val avoidLiquids: Boolean
    abstract val avoidSupporting: Boolean
    abstract val breakWeakBlocks: Boolean
    abstract val forceSilkTouch: Boolean
    abstract val forceFortunePickaxe: Boolean
    abstract val minFortuneLevel: Int
    abstract val ignoredBlocks: Set<Block>

    abstract val renders: Boolean
    abstract val fill: Boolean
    abstract val outline: Boolean
    abstract val outlineWidth: Int
    abstract val animation: AnimationMode

    abstract val dynamicFillColor: Boolean
    abstract val staticFillColor: Color
    abstract val startFillColor: Color
    abstract val endFillColor: Color

    abstract val dynamicOutlineColor: Boolean
    abstract val staticOutlineColor: Color
    abstract val startOutlineColor: Color
    abstract val endOutlineColor: Color

    override fun requestInternal(request: BreakRequest, queueIfClosed: Boolean) {
        BreakManager.request(request, queueIfClosed)
    }

    enum class BreakMode {
        Vanilla,
        Packet
    }

    enum class SwingMode {
        Constant,
        StartAndEnd,
        Start,
        End,
        None;

        fun isEnabled() = this != None
    }

    enum class BreakConfirmationMode {
        None,
        BreakThenAwait,
        AwaitThenBreak
    }

    enum class AnimationMode {
        None,
        Out,
        In,
        OutIn,
        InOut,
    }
}
