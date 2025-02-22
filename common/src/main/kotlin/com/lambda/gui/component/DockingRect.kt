/*
 * Copyright 2024 Lambda
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

package com.lambda.gui.component

import com.lambda.module.modules.client.ClickGui
import com.lambda.util.math.MathUtils.roundToStep
import com.lambda.util.math.Rect
import com.lambda.util.math.Vec2d
import com.lambda.util.math.coerceIn

abstract class DockingRect {
    abstract var relativePos: Vec2d
    protected abstract val width: Double
    protected abstract val height: Double
    protected val size get() = Vec2d(width, height)

    val rect get() = Rect.basedOn(position, size)
    open val dockingBase get() = rect.center

    open val autoDocking = false
    open val allowHAlign = true
    open val allowVAlign = true

    open var dockingH = HAlign.LEFT; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePos += Vec2d.RIGHT * delta * (size.x - screenSize.x)
    }

    open var dockingV = VAlign.TOP; set(to) {
        val from = field
        field = to

        val delta = to.multiplier - from.multiplier
        relativePos += Vec2d.BOTTOM * delta * (size.y - screenSize.y)
    }

    var screenSize: Vec2d = Vec2d.ZERO

    var position: Vec2d
        get() = relativeToAbs(relativePos)
            .coerceIn(0.0, screenSize.x - size.x, 0.0, screenSize.y - size.y)
        set(value) {
            relativePos = absToRelative(value.roundToStep(ClickGui.dockingGridSize))
            if (autoDocking) autoDocking()
        }

    private val dockingOffset get() =
        (screenSize - size) * Vec2d(dockingH.multiplier, dockingV.multiplier)

    private fun relativeToAbs(posIn: Vec2d) = posIn + dockingOffset
    private fun absToRelative(posIn: Vec2d) = posIn - dockingOffset

    /**
     * Automatically adjusts the docking alignment of the component based on its position relative to the screen center.
     *
     * This function calculates horizontal and vertical center ranges (approximately the middle third of the screen) and compares
     * the component's center position (dockingBase) against these ranges. If the component is outside the center range and alignment is allowed,
     * it sets the horizontal docking (dockingH) to LEFT or RIGHT and the vertical docking (dockingV) to TOP or BOTTOM accordingly.
     * If horizontal or vertical alignment is disallowed, the docking defaults to LEFT or TOP respectively.
     */
    fun autoDocking() {
        val screenCenterX = (screenSize.x * 0.3333)..(screenSize.x * 0.6666)
        val screenCenterY = (screenSize.y * 0.3333)..(screenSize.y * 0.6666)

        val drawableCenter = dockingBase

        dockingH = if (allowHAlign) {
            when {
                drawableCenter.x < screenCenterX.start -> HAlign.LEFT
                drawableCenter.x > screenCenterX.endInclusive -> HAlign.RIGHT
                else -> HAlign.CENTER
            }
        } else HAlign.LEFT

        dockingV = if (allowVAlign) {
            when {
                drawableCenter.y < screenCenterY.start -> VAlign.TOP
                drawableCenter.y > screenCenterY.endInclusive -> VAlign.BOTTOM
                else -> VAlign.CENTER
            }
        } else VAlign.TOP
    }
}