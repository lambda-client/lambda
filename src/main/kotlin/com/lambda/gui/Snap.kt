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

import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import com.lambda.config.settings.complex.Bind
import com.lambda.util.InputUtils.isSatisfied
import com.lambda.util.math.toCompose
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

internal object Snap {
    private const val MIN_GRID_STEP = 4f
    private val GRID_COLOR = Color.White.copy(alpha = 0.11f)

    data class Guides(val x: Float?, val y: Float?) {
        companion object {
            val NONE = Guides(null, null)
        }
    }

    data class Result(val position: Offset, val guides: Guides)

    private class Target(val shift: Float, val guide: Float?)

    val isActive: Boolean
        get() = GuiConfig.snapEnabled && (GuiConfig.snapKey == Bind.EMPTY || GuiConfig.snapKey.isSatisfied())

    fun gridStep(density: Density) = with(density) { max(MIN_GRID_STEP, GuiConfig.gridSize.dp.toPx()) }

    fun free(element: Rect, area: Rect) = Result(element.topLeft.keptIn(area, element.size), Guides.NONE)

    fun snap(element: Rect, others: Collection<Rect>, area: Rect, density: Density, spacing: Float = 0f): Result = with(density) {
        fun axis(start: (Rect) -> Float, end: (Rect) -> Float): Target {
            fun center(rect: Rect) = (start(rect) + end(rect)) / 2
            val from = start(element)
            val size = end(element) - from
            fun startAt(at: Float, guide: Float? = at) = Target(at - from, guide)
            fun endAt(at: Float, guide: Float? = at) = Target(at - size - from, guide)
            fun centerAt(at: Float, guide: Float? = at) = Target(at - size / 2 - from, guide)

            val neighbours = buildList {
                if (GuiConfig.snapToEdges) {
                    add(startAt(start(area)))
                    add(endAt(end(area)))
                    others.forEach { other ->
                        add(startAt(start(other)))
                        add(endAt(end(other)))
                        add(startAt(end(other) + spacing, end(other) + spacing / 2))
                        add(endAt(start(other) - spacing, start(other) - spacing / 2))
                    }
                }
                if (GuiConfig.snapToCenters) others.forEach { add(centerAt(center(it))) }
            }
            val middle = center(area)
            val screenCenter = listOf(startAt(middle), centerAt(middle), endAt(middle)).takeIf { GuiConfig.snapToScreenCenter }.orEmpty()
            val step = gridStep(density)
            fun onGrid(line: Float) = (line / step).roundToInt() * step
            val grid = listOf(
                startAt(onGrid(from), null),
                centerAt(onGrid(from + size / 2), null),
                endAt(onGrid(from + size), null),
            ).takeIf { GuiConfig.snapToGrid }.orEmpty()

            return listOf(
                neighbours to GuiConfig.snapDistanceElement.dp.toPx(),
                screenCenter to GuiConfig.snapDistanceScreen.dp.toPx(),
                grid to GuiConfig.snapDistanceGrid.dp.toPx(),
            ).firstNotNullOfOrNull { (targets, reach) ->
                targets.minByOrNull { abs(it.shift) }?.takeIf { abs(it.shift) <= reach }
            } ?: Target(0f, null)
        }

        val x = axis({ it.left }, { it.right })
        val y = axis({ it.top }, { it.bottom })
        Result((element.topLeft + Offset(x.shift, y.shift)).keptIn(area, element.size), Guides(x.guide, y.guide))
    }

    fun Modifier.snapLines(drop: () -> Result?): Modifier = drawWithContent {
        val guides = drop()?.guides.takeIf { GuiConfig.showSnapLines }
        if (guides != null && isActive && GuiConfig.snapToGrid) drawGrid()
        drawContent()
        if (guides != null) {
            val color = GuiConfig.snapLineColor.toCompose()
            guides.x?.let { drawLine(color, Offset(it, 0f), Offset(it, size.height), Stroke.HairlineWidth) }
            guides.y?.let { drawLine(color, Offset(0f, it), Offset(size.width, it), Stroke.HairlineWidth) }
        }
    }

    private fun DrawScope.drawGrid() {
        val step = gridStep(this)
        generateSequence(0f) { it + step }.takeWhile { it <= size.width }
            .forEach { drawLine(GRID_COLOR, Offset(it, 0f), Offset(it, size.height), Stroke.HairlineWidth) }
        generateSequence(0f) { it + step }.takeWhile { it <= size.height }
            .forEach { drawLine(GRID_COLOR, Offset(0f, it), Offset(size.width, it), Stroke.HairlineWidth) }
    }

    private fun Offset.keptIn(area: Rect, element: Size) = Offset(
        x.coerceIn(area.left, max(area.left, area.right - element.width)),
        y.coerceIn(area.top, max(area.top, area.bottom - element.height))
    )
}
