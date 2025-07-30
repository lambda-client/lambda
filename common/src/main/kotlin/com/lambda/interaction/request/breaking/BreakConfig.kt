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

interface BreakConfig : RequestConfig {
    val breakMode: BreakMode
    val sorter: SortMode
    val breakThreshold: Float
    val reBreak: Boolean

    val doubleBreak: Boolean
    val unsafeCancels: Boolean

    val fudgeFactor: Int
    //ToDo: Needs a more advanced player simulation implementation to predict the next ticks onGround / submerged status
//    abstract val desyncFix: Boolean
    val breakDelay: Int

    val breakStageMask: Set<Event>

    val swing: SwingMode
    val swingType: BuildConfig.SwingType

    val rotateForBreak: Boolean

    val breakConfirmation: BreakConfirmationMode
    val breaksPerTick: Int
    val maxPendingBreaks: Int

    val avoidLiquids: Boolean
    val avoidSupporting: Boolean
    val breakWeakBlocks: Boolean
    val ignoredBlocks: Set<Block>

    val suitableToolsOnly: Boolean
    val forceSilkTouch: Boolean
    val forceFortunePickaxe: Boolean
    val minFortuneLevel: Int

    val sounds: Boolean
    val particles: Boolean
    val breakingTexture: Boolean

    val renders: Boolean
    val fill: Boolean
    val outline: Boolean
    val outlineWidth: Int
    val animation: AnimationMode

    val dynamicFillColor: Boolean
    val staticFillColor: Color
    val startFillColor: Color
    val endFillColor: Color

    val dynamicOutlineColor: Boolean
    val staticOutlineColor: Color
    val startOutlineColor: Color
    val endOutlineColor: Color

    enum class BreakMode {
        Vanilla,
        Packet
    }

    enum class SortMode {
        Closest,
        Farthest,
        Rotation,
        Random
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
