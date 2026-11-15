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

package com.lambda.gui

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lambda.config.settings.complex.Bind
import com.lambda.util.InputUtils.isSatisfied
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Where something that is dragged ends up, in scene pixels: on the screen, and snapped into
 * line with its surroundings while the snap key is held. HUD elements in the HUD editor and
 * the windows of the click GUI are placed by it alike.
 *
 * Each axis is snapped on its own, and the guides come in tiers: the other elements and the
 * screen edges are tried first, then the centre of the screen, then the grid. The first tier
 * with a guide in reach wins, and within it the guide closest to any of the element's three
 * lines (its two edges and its centre) does.
 */
internal object Snap {
    /** The guides the element snapped to, drawn while dragging. Null where it snapped to nothing worth showing. */
    data class Guides(val x: Float?, val y: Float?) {
        companion object {
            val NONE = Guides(null, null)
        }
    }

    data class Result(val position: Offset, val guides: Guides)

    /** Whether a dragged element snaps right now: snapping is on and its key, if it has one, is held */
    val isActive: Boolean
        get() = GuiConfig.snapEnabled && (GuiConfig.snapKey == Bind.EMPTY || GuiConfig.snapKey.isSatisfied())

    /** [element] left where it was dragged to, only kept on the [screen] */
    fun free(element: Rect, screen: Size) = Result(element.topLeft.keptOn(screen, element.size), Guides.NONE)

    /**
     * The [lines] of the element along one axis, positions to snap them to, and how close a line
     * has to be to one. Grid snaps are not drawn.
     */
    private class Tier(val lines: List<Float>, val reach: Float, val shown: Boolean, val nearest: (line: Float) -> Float?)

    /**
     * [element] moved onto the nearest guide in reach, of the [others] and the [screen].
     *
     * @param spacing the room left between [element] and one of the [others] it is snapped
     * beside, in pixels. Two that are lined up edge on edge are still exactly in line.
     */
    fun snap(element: Rect, others: Collection<Rect>, screen: Size, density: Density, spacing: Float = 0f): Result = with(density) {
        fun tiers(extent: Float, start: (Rect) -> Float, end: (Rect) -> Float): List<Tier> {
            fun center(rect: Rect) = (start(rect) + end(rect)) / 2
            val lines = listOf(start(element), center(element), end(element))

            // Against its neighbours, everything is taken to be half the spacing larger all
            // round. Side by side that leaves the spacing between two; in line it changes nothing.
            val half = spacing / 2
            val spacedLines = listOf(start(element) - half, center(element), end(element) + half)
            val neighbourGuides = buildList {
                if (GuiConfig.snapToEdges) {
                    add(-half)
                    add(extent + half)
                    others.forEach {
                        add(start(it) - half)
                        add(end(it) + half)
                    }
                }
                if (GuiConfig.snapToCenters) others.forEach { add(center(it)) }
            }
            val gridStep = max(4f, GuiConfig.gridSize.dp.toPx())
            return listOfNotNull(
                Tier(spacedLines, GuiConfig.snapDistanceElement.dp.toPx(), shown = true) { line ->
                    neighbourGuides.minByOrNull { abs(it - line) }
                },
                Tier(lines, GuiConfig.snapDistanceScreen.dp.toPx(), shown = true) { extent / 2 }
                    .takeIf { GuiConfig.snapToScreenCenter },
                Tier(lines, GuiConfig.snapDistanceGrid.dp.toPx(), shown = false) { line -> (line / gridStep).roundToInt() * gridStep }
                    .takeIf { GuiConfig.snapToGrid },
            )
        }

        val (shiftX, guideX) = snapAxis(tiers(screen.width, { it.left }, { it.right }))
        val (shiftY, guideY) = snapAxis(tiers(screen.height, { it.top }, { it.bottom }))
        Result((element.topLeft + Offset(shiftX, shiftY)).keptOn(screen, element.size), Guides(guideX, guideY))
    }

    /** How far to move along the axis to reach the best guide, and that guide if it should be drawn */
    private fun snapAxis(tiers: List<Tier>): Pair<Float, Float?> {
        for (tier in tiers) {
            val (line, guide) = tier.lines
                .mapNotNull { line -> tier.nearest(line)?.let { line to it } }
                .minByOrNull { (line, guide) -> abs(guide - line) }
                ?: continue
            if (abs(guide - line) <= tier.reach) return guide - line to guide.takeIf { tier.shown }
        }
        return 0f to null
    }

    private fun Offset.keptOn(screen: Size, element: Size) = Offset(
        x.coerceIn(0f, max(0f, screen.width - element.width)),
        y.coerceIn(0f, max(0f, screen.height - element.height))
    )
}
