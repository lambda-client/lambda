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

package com.lambda.graphics.outline

import java.awt.Color

/**
 * Style configuration for an outline effect.
 *
 * @property color The outline color (ARGB)
 * @property thickness Line thickness in pixels (1-5 range recommended)
 * @property glowIntensity How much blur/glow to apply (0 = sharp edge, 1 = max glow)
 * @property glowRadius Blur spread radius for the glow effect
 */
data class OutlineStyle(
    val color: Color,
    val thickness: Float = 2f,
    val glowIntensity: Float = 0.5f,
    val glowRadius: Float = 2f,
    /** Whether to fill the entity silhouette with color. */
    val fill: Boolean = true,
    /** Opacity of the fill (0.0 to 1.0). */
    val fillOpacity: Float = 0.4f
) {
    /**
     * Get the color as an ARGB int for Minecraft's outline system.
     */
    fun toArgb(): Int = color.rgb
    
    companion object {
        /** Default red outline for hostile entities */
        val HOSTILE = OutlineStyle(Color.RED)
        
        /** Default blue outline for players */
        val PLAYER = OutlineStyle(Color(0x5555FF))
        
        /** Default green outline for passive entities */
        val PASSIVE = OutlineStyle(Color.GREEN)
        
        /** Default yellow outline for items */
        val ITEM = OutlineStyle(Color.YELLOW)
        
        /** Default white outline */
        val DEFAULT = OutlineStyle(Color.WHITE)
    }
}
