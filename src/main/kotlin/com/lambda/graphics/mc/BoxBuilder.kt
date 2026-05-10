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

import com.lambda.config.settings.blocks.LineConfig
import com.lambda.graphics.util.DirectionMask
import net.minecraft.util.math.Direction
import java.awt.Color

class BoxBuilder(lineConfig: LineConfig?) {
    var outlineSides: Int = DirectionMask.All
    var fillSides: Int = DirectionMask.All

    var outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.And
    var lineWidth = lineConfig?.width ?: -0.0005f

    var dashStyle: LineDashStyle? = lineConfig?.getDashStyle()

    var fillBottomNorthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var fillBottomNorthEast: Color = lineConfig?.startColor ?: Color.WHITE
    var fillBottomSouthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var fillBottomSouthEast: Color = lineConfig?.startColor ?: Color.WHITE

    var fillTopNorthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var fillTopNorthEast: Color = lineConfig?.startColor ?: Color.WHITE
    var fillTopSouthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var fillTopSouthEast: Color = lineConfig?.startColor ?: Color.WHITE

    var outlineBottomNorthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var outlineBottomNorthEast: Color = lineConfig?.startColor ?: Color.WHITE
    var outlineBottomSouthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var outlineBottomSouthEast: Color = lineConfig?.startColor ?: Color.WHITE

    var outlineTopNorthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var outlineTopNorthEast: Color = lineConfig?.startColor ?: Color.WHITE
    var outlineTopSouthWest: Color = lineConfig?.startColor ?: Color.WHITE
    var outlineTopSouthEast: Color = lineConfig?.startColor ?: Color.WHITE

    @RenderDsl
    fun allColors(color: Color) {
        outlineColor(color)
        fillColor(color)
    }

    @RenderDsl
    fun colors(fill: Color, outline: Color) {
        fillColor(fill)
        outlineColor(outline)
    }

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

    @RenderDsl
    fun gradientY(bottom: Color, top: Color) {
        fillGradientY(bottom, top)
        outlineGradientY(bottom, top)
    }

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
    fun lineWidth(lineWidth: Float) {
        this.lineWidth = lineWidth
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

    @RenderDsl
    fun hideOutline() {
        outlineSides = DirectionMask.None
    }

    @RenderDsl
    fun hideFill() {
        fillSides = DirectionMask.None
    }

    @RenderDsl
    fun outlineOnly() {
        outlineSides = DirectionMask.All
        fillSides = DirectionMask.None
    }

    @RenderDsl
    fun fillOnly() {
        outlineSides = DirectionMask.None
        fillSides = DirectionMask.All
    }

    @RenderDsl
    fun showFillSides(vararg directions: Direction) {
        directions.forEach { fillSides = fillSides or DirectionMask.run { it.mask } }
    }

    @RenderDsl
    fun showFillSides(mask: Int) {
        fillSides = fillSides or mask
    }

    @RenderDsl
    fun hideFillSides(vararg directions: Direction) {
        directions.forEach { fillSides = fillSides and DirectionMask.run { it.mask }.inv() }
    }

    @RenderDsl
    fun hideFillSides(mask: Int) {
        fillSides = fillSides and mask.inv()
    }

    @RenderDsl
    fun showOutlineSides(vararg directions: Direction) {
        directions.forEach { outlineSides = outlineSides or DirectionMask.run { it.mask } }
    }

    @RenderDsl
    fun showOutlineSides(mask: Int) {
        outlineSides = outlineSides or mask
    }

    @RenderDsl
    fun hideOutlineSides(vararg directions: Direction) {
        directions.forEach { outlineSides = outlineSides and DirectionMask.run { it.mask }.inv() }
    }

    @RenderDsl
    fun hideOutlineSides(mask: Int) {
        outlineSides = outlineSides and mask.inv()
    }

    @RenderDsl
    fun outlineMode(outlineMode: DirectionMask.OutlineMode) {
        this.outlineMode = outlineMode
    }
}