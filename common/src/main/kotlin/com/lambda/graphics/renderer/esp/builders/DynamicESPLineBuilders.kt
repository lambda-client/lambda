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

package com.lambda.graphics.renderer.esp.builders

import com.lambda.Lambda.mc
import com.lambda.graphics.RenderMain
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.impl.DynamicESPRenderer
import com.lambda.graphics.renderer.gui.LineRenderer
import com.lambda.util.math.minus
import net.minecraft.util.math.Vec3d
import java.awt.Color
import org.joml.Vector2d
import org.joml.Vector4f

/**
 * Draws a line in 3D space using the LineRenderer.
 *
 * @param points List of 3D points to connect with lines
 * @param color Color of the line
 * @param width Width of the line
 * @param dashiness Dashiness of the line (1.0 = solid, 0.0 = fully dashed)
 * @param dashPeriod Period of the dashes
 */
fun DynamicESPRenderer.drawLine(
    points: List<Vec3d>,
    color: Color,
    width: Double = 1.0,
    dashiness: Double = 1.0,
    dashPeriod: Double = 1.0
) {
    if (points.size < 2) return

    // Project 3D points to 2D screen coordinates
    val screenPoints = points.mapNotNull { point ->
        val screenPos = project3DTo2D(point) ?: return@mapNotNull null
        LineRenderer.Point(Vector2d(screenPos.x, screenPos.y), color)
    }

    if (screenPoints.size < 2) return

    // Draw the line using LineRenderer
    LineRenderer.lines(
        dashiness = dashiness,
        dashPeriod = dashPeriod,
        batching = false
    ) {
        line(width) {
            screenPoints.forEach { point ->
                point(point.pos, point.color)
            }
        }
    }
}

/**
 * Draws a line in 3D space using the LineRenderer.
 *
 * @param start Starting point of the line
 * @param end Ending point of the line
 * @param startColor Color at the start of the line
 * @param endColor Color at the end of the line
 * @param width Width of the line
 * @param dashiness Dashiness of the line (1.0 = solid, 0.0 = fully dashed)
 * @param dashPeriod Period of the dashes
 */
fun DynamicESPRenderer.drawLine(
    start: Vec3d,
    end: Vec3d,
    startColor: Color,
    endColor: Color = startColor,
    width: Double = 1.0,
    dashiness: Double = 1.0,
    dashPeriod: Double = 1.0
) {
    // Project 3D points to 2D screen coordinates
    val startScreen = project3DTo2D(start) ?: return
    val endScreen = project3DTo2D(end) ?: return

    // Draw the line using LineRenderer
    LineRenderer.lines(
        dashiness = dashiness,
        dashPeriod = dashPeriod,
        batching = false
    ) {
        line(width) {
            point(Vector2d(startScreen.x, startScreen.y), startColor)
            point(Vector2d(endScreen.x, endScreen.y), endColor)
        }
    }
}

/**
 * Draws a line between two dynamic boxes in 3D space using the LineRenderer.
 *
 * @param box1 First dynamic box
 * @param box2 Second dynamic box
 * @param color Color of the line
 * @param width Width of the line
 * @param dashiness Dashiness of the line (1.0 = solid, 0.0 = fully dashed)
 * @param dashPeriod Period of the dashes
 */
fun DynamicESPRenderer.drawLineBetweenBoxes(
    box1: DynamicAABB,
    box2: DynamicAABB,
    color: Color,
    width: Double = 1.0,
    dashiness: Double = 1.0,
    dashPeriod: Double = 1.0
) {
    drawLine(box1.center(), box2.center(), color, color, width, dashiness, dashPeriod)
}

/**
 * Projects a 3D point to 2D screen coordinates.
 * Returns null if the point is behind the camera or outside the screen.
 */
private fun project3DTo2D(point: Vec3d): Vector2d? {
    val transformedPoint = point - mc.gameRenderer.camera.pos

    // Create a 4D vector from the 3D point
    val vec4 = Vector4f(
        transformedPoint.x.toFloat(),
        transformedPoint.y.toFloat(),
        transformedPoint.z.toFloat(),
        1f
    )

    // Transform the point using the projection-model matrix
    RenderMain.projModel.transform(vec4)

    // Check if the point is behind the camera
    if (vec4.w <= 0f) return null

    // Perform perspective division
    vec4.div(vec4.w)

    // Convert from normalized device coordinates (-1 to 1) to screen coordinates (0 to screen width/height)
    val screenX = (vec4.x * 0.5f + 0.5f) * RenderMain.screenSize.x.toFloat()
    val screenY = (1f - (vec4.y * 0.5f + 0.5f)) * RenderMain.screenSize.y.toFloat()

    // Check if the point is outside the screen
    if (screenX < 0 || screenX > RenderMain.screenSize.x.toFloat() || screenY < 0 || screenY > RenderMain.screenSize.y.toFloat()) {
        return null
    }

    return Vector2d(screenX.toDouble(), screenY.toDouble())
}