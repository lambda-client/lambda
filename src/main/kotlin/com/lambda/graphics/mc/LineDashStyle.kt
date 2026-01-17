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

package com.lambda.graphics.mc

/**
 * Configuration for dashed line rendering in world-space.
 *
 * All measurements are in WORLD UNITS (blocks). For example:
 * - dashLength = 0.5 means each dash is half a block long
 * - gapLength = 0.25 means gaps are a quarter block
 *
 * When applied to lines, creates a repeating dash pattern where visible segments
 * alternate with gaps. The pattern repeats with a period of (dashLength + gapLength).
 *
 * Animation is now handled by the shader using Minecraft's GameTime, so the
 * animated/animationSpeed properties control whether animation is enabled.
 *
 * @property dashLength Length of each visible dash segment in world units (blocks)
 * @property gapLength Length of each invisible gap segment in world units (blocks)
 * @property offset Phase offset to shift the pattern along the line (0.0 to 1.0, normalized)
 * @property animated If true, the dash pattern animates (marching ants effect)
 * @property animationSpeed Speed multiplier for animation (higher = faster marching)
 *
 * Usage:
 * ```
 * // Simple dashed line (0.5 block dash, 0.25 block gap)
 * val dashed = LineDashStyle(dashLength = 0.5f, gapLength = 0.25f)
 *
 * // Dotted line (equal dash and gap, 0.15 blocks each)
 * val dotted = LineDashStyle.dotted()
 *
 * // Animated marching ants for selection highlight
 * val marching = LineDashStyle.marchingAnts()
 * ```
 */
data class LineDashStyle(
    val dashLength: Float = 0.5f,
    val gapLength: Float = 0.25f,
    val offset: Float = 0f,
    val animated: Boolean = false,
    val animationSpeed: Float = 1f
) {
    init {
        require(dashLength > 0f) { "dashLength must be positive" }
        require(gapLength >= 0f) { "gapLength must be non-negative" }
        require(offset in 0f..1f) { "offset must be between 0.0 and 1.0" }
    }
    
    /** Total length of one dash+gap cycle in world units. */
    val cycleLength: Float get() = dashLength + gapLength
    
    /** Ratio of the dash portion (0.0 to 1.0) within each cycle. */
    val dashRatio: Float get() = dashLength / cycleLength
    
    companion object {
        /** No dashing - solid line. */
        val SOLID: LineDashStyle? = null
        
        /** 
         * Create a dotted pattern with equal dash and gap lengths.
         * Default: 0.15 blocks each (small dots)
         */
        fun dotted(size: Float = 0.15f) = LineDashStyle(
            dashLength = size,
            gapLength = size
        )
        
        /** 
         * Create an animated "marching ants" selection pattern.
         * Default: 0.4 block dash, 0.2 block gap, animated
         */
        fun marchingAnts(
            dashLength: Float = 0.4f,
            gapLength: Float = 0.2f,
            speed: Float = 1f
        ) = LineDashStyle(
            dashLength = dashLength,
            gapLength = gapLength,
            animated = true,
            animationSpeed = speed
        )
        
        /** 
         * Create a long-dash pattern (3:1 dash to gap ratio).
         * Default: 0.75 block dash, 0.25 block gap
         */
        fun longDash(dashLength: Float = 0.75f) = LineDashStyle(
            dashLength = dashLength,
            gapLength = dashLength / 3f
        )
        
        /** 
         * Create a short-dash pattern (1:1 ratio, larger than dotted).
         * Default: 0.3 block dash and gap
         */
        fun shortDash(size: Float = 0.3f) = LineDashStyle(
            dashLength = size,
            gapLength = size
        )
    }
}
