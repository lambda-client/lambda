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

    @Suppress("unused")
    companion object {
        val SOLID: LineDashStyle? = null
        
        fun dotted(size: Float = 0.15f) = LineDashStyle(
            dashLength = size,
            gapLength = size
        )
        
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
        
        fun longDash(dashLength: Float = 0.75f) = LineDashStyle(
            dashLength = dashLength,
            gapLength = dashLength / 3f
        )
        
        fun shortDash(size: Float = 0.3f) = LineDashStyle(
            dashLength = size,
            gapLength = size
        )

        fun screenDotted(size: Float = 0.01f) = LineDashStyle(
            dashLength = size,
            gapLength = size
        )

        fun screenMarchingAnts(
            dashLength: Float = 0.02f,
            gapLength: Float = 0.01f,
            speed: Float = 1f
        ) = LineDashStyle(
            dashLength = dashLength,
            gapLength = gapLength,
            animated = true,
            animationSpeed = speed
        )

        fun screenDashed(dashLength: Float = 0.03f, gapLength: Float = 0.015f) = LineDashStyle(
            dashLength = dashLength,
            gapLength = gapLength
        )

        fun screenShortDash(size: Float = 0.015f) = LineDashStyle(
            dashLength = size,
            gapLength = size
        )

        fun screenLongDash(dashLength: Float = 0.04f) = LineDashStyle(
            dashLength = dashLength,
            gapLength = dashLength / 3f
        )
    }
}
