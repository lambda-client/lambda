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

import com.lambda.graphics.util.DirectionMask
import net.minecraft.util.math.Direction
import java.awt.Color

/**
 * DSL builder for creating boxes with fine-grained control over:
 * - Which sides to show for outlines vs faces (independently)
 * - Individual vertex colors for all 8 corners
 *
 * Vertex naming convention (looking at box from outside):
 * - Bottom corners: bottomNorthWest, bottomNorthEast, bottomSouthWest, bottomSouthEast
 * - Top corners: topNorthWest, topNorthEast, topSouthWest, topSouthEast
 *
 * Usage:
 * ```
 * builder.box(myBox) {
 *     outlineSides = DirectionMask.UP or DirectionMask.DOWN
 *     faceSides = DirectionMask.ALL
 *     thickness = 2f
 *     
 *     // Set all vertices to one color
 *     allColors(Color.RED)
 *     
 *     // Or set gradient colors
 *     bottomColor = Color.RED
 *     topColor = Color.BLUE
 *     
 *     // Or set individual vertex colors
 *     topNorthWest = Color.RED
 *     topNorthEast = Color.GREEN
 *     // etc.
 * }
 * ```
 */
class BoxBuilder(val lineWidth: Float) {
    // Side masks - independent control for outlines and faces
    var outlineSides: Int = DirectionMask.ALL
    var fillSides: Int = DirectionMask.ALL
    
    // Outline mode for edge visibility
    var outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
    
    // Dash style for outline edges (null = solid lines)
    var dashStyle: LineDashStyle? = null

    // Bottom layer fill colors
    var fillBottomNorthWest: Color = Color.WHITE
    var fillBottomNorthEast: Color = Color.WHITE
    var fillBottomSouthWest: Color = Color.WHITE
    var fillBottomSouthEast: Color = Color.WHITE

    // Top layer fill colors
    var fillTopNorthWest: Color = Color.WHITE
    var fillTopNorthEast: Color = Color.WHITE
    var fillTopSouthWest: Color = Color.WHITE
    var fillTopSouthEast: Color = Color.WHITE

    // Bottom layer outline colors
    var outlineBottomNorthWest: Color = Color.WHITE
    var outlineBottomNorthEast: Color = Color.WHITE
    var outlineBottomSouthWest: Color = Color.WHITE
    var outlineBottomSouthEast: Color = Color.WHITE
    
    // Top layer outline colors
    var outlineTopNorthWest: Color = Color.WHITE
    var outlineTopNorthEast: Color = Color.WHITE
    var outlineTopSouthWest: Color = Color.WHITE
    var outlineTopSouthEast: Color = Color.WHITE

    /** Set both outline and fill colors at once. */
    @RenderDsl
    fun allColors(color: Color) {
        outlineColor(color)
        fillColor(color)
    }

    /** Set outline and fill to different colors. */
    @RenderDsl
    fun colors(fill: Color, outline: Color) {
        fillColor(fill)
        outlineColor(outline)
    }

    /** Set all fill (face) colors to a single color. */
    @RenderDsl
    fun fillColor(color: Color) {
        fillBottomNorthWest = color
        fillBottomNorthEast = color
        fillBottomSouthWest = color
        fillBottomSouthEast = color
        fillTopNorthWest = color
        fillTopNorthEast = color
        fillTopSouthWest = color
        fillTopSouthEast = color
    }

    /** Set all outline (edge) colors to a single color. */
    @RenderDsl
    fun outlineColor(color: Color) {
        outlineBottomNorthWest = color
        outlineBottomNorthEast = color
        outlineBottomSouthWest = color
        outlineBottomSouthEast = color
        outlineTopNorthWest = color
        outlineTopNorthEast = color
        outlineTopSouthWest = color
        outlineTopSouthEast = color
    }

    /** Set all bottom vertices to one color and all top vertices to another (both outline and fill). */
    @RenderDsl
    fun gradientY(bottom: Color, top: Color) {
        fillGradientY(bottom, top)
        outlineGradientY(bottom, top)
    }

    /** Set fill gradient along Y axis (bottom to top). */
    @RenderDsl
    fun fillGradientY(bottom: Color, top: Color) {
        fillBottomNorthWest = bottom
        fillBottomNorthEast = bottom
        fillBottomSouthWest = bottom
        fillBottomSouthEast = bottom
        fillTopNorthWest = top
        fillTopNorthEast = top
        fillTopSouthWest = top
        fillTopSouthEast = top
    }

    /** Set outline gradient along Y axis (bottom to top). */
    @RenderDsl
    fun outlineGradientY(bottom: Color, top: Color) {
        outlineBottomNorthWest = bottom
        outlineBottomNorthEast = bottom
        outlineBottomSouthWest = bottom
        outlineBottomSouthEast = bottom
        outlineTopNorthWest = top
        outlineTopNorthEast = top
        outlineTopSouthWest = top
        outlineTopSouthEast = top
    }

    @RenderDsl
    fun gradientX(west: Color, east: Color) {
        fillGradientX(west, east)
        outlineGradientX(west, east)
    }

    /** Set gradient along X axis (west to east) for both outline and fill. */
    @RenderDsl
    fun fillGradientX(west: Color, east: Color) {
        fillBottomNorthWest = west
        fillBottomSouthWest = west
        fillTopNorthWest = west
        fillTopSouthWest = west
        fillBottomNorthEast = east
        fillBottomSouthEast = east
        fillTopNorthEast = east
        fillTopSouthEast = east
    }

    @RenderDsl
    fun outlineGradientX(west: Color, east: Color) {
        outlineBottomNorthWest = west
        outlineBottomSouthWest = west
        outlineTopNorthWest = west
        outlineTopSouthWest = west
        outlineBottomNorthEast = east
        outlineBottomSouthEast = east
        outlineTopNorthEast = east
        outlineTopSouthEast = east
    }

    /** Set gradient along Z axis (north to south) for both outline and fill. */
    @RenderDsl
    fun gradientZ(north: Color, south: Color) {
        fillGradientZ(north, south)
        outlineGradientZ(north, south)
    }

    @RenderDsl
    fun fillGradientZ(north: Color, south: Color) {
        fillBottomNorthWest = north
        fillBottomNorthEast = north
        fillTopNorthWest = north
        fillTopNorthEast = north
        fillBottomSouthWest = south
        fillBottomSouthEast = south
        fillTopSouthWest = south
        fillTopSouthEast = south
    }

    @RenderDsl
    fun outlineGradientZ(north: Color, south: Color) {
        outlineBottomNorthWest = north
        outlineBottomNorthEast = north
        outlineTopNorthWest = north
        outlineTopNorthEast = north
        outlineBottomSouthWest = south
        outlineBottomSouthEast = south
        outlineTopSouthWest = south
        outlineTopSouthEast = south
    }

    @RenderDsl
    fun lineDashStyle(lineDashStyle: LineDashStyle) {
        dashStyle = lineDashStyle
    }

    @RenderDsl
    fun showSides(vararg directions: Direction) {
        showFillSides(*directions)
        showOutlineSides(*directions)
    }

    @RenderDsl
    fun showSides(mask: Int) {
        showFillSides(mask)
        showOutlineSides(mask)
    }

    @RenderDsl
    fun hideSides(vararg directions: Direction) {
        hideFillSides(*directions)
        hideOutlineSides(*directions)
    }

    @RenderDsl
    fun hideSides(mask: Int) {
        hideFillSides(mask)
        hideOutlineSides(mask)
    }

    /** Hide all outline edges. */
    @RenderDsl
    fun hideOutline() {
        outlineSides = DirectionMask.NONE
    }
    
    /** Hide all faces. */
    @RenderDsl
    fun hideFill() {
        fillSides = DirectionMask.NONE
    }
    
    /** Show only outline (no faces). */
    @RenderDsl
    fun outlineOnly() {
        outlineSides = DirectionMask.ALL
        fillSides = DirectionMask.NONE
    }
    
    /** Show only faces (no outline). */
    @RenderDsl
    fun fillOnly() {
        outlineSides = DirectionMask.NONE
        fillSides = DirectionMask.ALL
    }

    /** Show the specified fill (face) sides, adding to current mask. */
    @RenderDsl
    fun showFillSides(vararg directions: Direction) {
        directions.forEach { fillSides = fillSides or DirectionMask.run { it.mask } }
    }
    
    /** Show the specified fill (face) sides by mask, adding to current mask. */
    @RenderDsl
    fun showFillSides(mask: Int) {
        fillSides = fillSides or mask
    }
    
    /** Hide the specified fill (face) sides, removing from current mask. */
    @RenderDsl
    fun hideFillSides(vararg directions: Direction) {
        directions.forEach { fillSides = fillSides and DirectionMask.run { it.mask }.inv() }
    }
    
    /** Hide the specified fill (face) sides by mask, removing from current mask. */
    @RenderDsl
    fun hideFillSides(mask: Int) {
        fillSides = fillSides and mask.inv()
    }
    
    /** Show the specified outline sides, adding to current mask. */
    @RenderDsl
    fun showOutlineSides(vararg directions: Direction) {
        directions.forEach { outlineSides = outlineSides or DirectionMask.run { it.mask } }
    }
    
    /** Show the specified outline sides by mask, adding to current mask. */
    @RenderDsl
    fun showOutlineSides(mask: Int) {
        outlineSides = outlineSides or mask
    }
    
    /** Hide the specified outline sides, removing from current mask. */
    @RenderDsl
    fun hideOutlineSides(vararg directions: Direction) {
        directions.forEach { outlineSides = outlineSides and DirectionMask.run { it.mask }.inv() }
    }
    
    /** Hide the specified outline sides by mask, removing from current mask. */
    @RenderDsl
    fun hideOutlineSides(mask: Int) {
        outlineSides = outlineSides and mask.inv()
    }

    @RenderDsl
    fun outlineMode(outlineMode: DirectionMask.OutlineMode) {
        this.outlineMode = outlineMode
    }
}