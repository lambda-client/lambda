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

package com.lambda.module.modules.debug

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DynamicAABB.Companion.dynamicBox
import com.lambda.graphics.renderer.esp.builders.drawLine
import com.lambda.graphics.renderer.esp.builders.drawLineBetweenBoxes
import com.lambda.graphics.renderer.esp.builders.ofBox
import com.lambda.graphics.renderer.gui.LineRenderer
import net.minecraft.util.math.Vec3d
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.setAlpha
import com.lambda.util.world.entitySearch
import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.Box
import org.joml.Vector2d
import java.awt.Color

/**
 * RenderTest module demonstrates different rendering techniques:
 * 1. Drawing boxes around entities using DynamicESP
 * 2. Drawing a box at the player's position using StaticESP
 * 3. Drawing lines in 3D space:
 *    - Lines between entities
 *    - A 3D star shape around the player
 *    - A continuous line connecting multiple points
 * 4. Drawing 2D lines on the screen (a triangle and a square)
 *
 * Enable the module and toggle "Draw Lines" to see the line examples.
 * Adjust line width, dashiness, and dash period to see different line styles.
 */
object RenderTest : Module(
    name = "Render:shrimp:Test:canned_food:",
    description = "RenderTest",
    defaultTags = setOf(ModuleTag.DEBUG)
) {
    private val test1 by setting("Toggle visibility", true)
    private val test21 by setting("Hallo 1", true, visibility = ::test1)
    private val test22 by setting("Hallo Slider", 1.0, 0.0..5.0, 0.5, visibility = ::test1)
    private val test23 by setting("Hallo String", "bruh", visibility = ::test1)
    private val test31 by setting("Holla huh 1", true, visibility = { !test1 })
    private val test32 by setting("Holla buh 2", true, visibility = { !test1 })

    private val drawLines by setting("Draw Lines", true)
    private val lineWidth by setting("Line Width", 2.0, 0.5..5.0, 0.5, visibility = ::drawLines)
    private val dashiness by setting("Dashiness", 1.0, 0.0..1.0, 0.1, visibility = ::drawLines)
    private val dashPeriod by setting("Dash Period", 1.0, 0.5..5.0, 0.5, visibility = ::drawLines)

    private val outlineColor = Color(100, 150, 255).setAlpha(0.5)
    private val filledColor = outlineColor.setAlpha(0.2)
    private val lineColor = Color(255, 100, 100).setAlpha(0.8)

    init {
        listen<RenderEvent.DynamicESP> { event ->
            val entities = entitySearch<LivingEntity>(8.0).sortedBy { it.distanceTo(player) }

            // Draw boxes around entities
            entities.forEach { entity ->
                event.renderer.ofBox(entity.dynamicBox, filledColor, outlineColor)
            }

            entities.firstOrNull()?.let { first ->
                // Example of drawing lines between entities using the new drawLineBetweenBoxes function
                event.renderer.drawLineBetweenBoxes(
                    player.dynamicBox,
                    first.dynamicBox,
                    lineColor,
                    lineWidth,
                    dashiness,
                    dashPeriod
                )
            }
        }

        listen<RenderEvent.StaticESP> {
            // Draw a box at player position
            it.renderer.ofBox(Box.of(player.pos, 0.3, 0.3, 0.3), filledColor, outlineColor)

            if (drawLines) {
                val renderer = it.renderer
                val pos = player.pos
                val size = 1.0

                // Define the points of the star
                val center = pos
                val top = Vec3d(center.x, center.y + size, center.z)
                val bottom = Vec3d(center.x, center.y - size, center.z)
                val left = Vec3d(center.x - size, center.y, center.z)
                val right = Vec3d(center.x + size, center.y, center.z)
                val front = Vec3d(center.x, center.y, center.z + size)
                val back = Vec3d(center.x, center.y, center.z - size)

                // Connect the points to form a 3D star using the new drawLine function
                renderer.drawLine(top, left, Color.RED, Color.GREEN, lineWidth, dashiness, dashPeriod)
                renderer.drawLine(top, right, Color.RED, Color.GREEN, lineWidth, dashiness, dashPeriod)
                renderer.drawLine(top, front, Color.RED, Color.BLUE, lineWidth, dashiness, dashPeriod)
                renderer.drawLine(top, back, Color.RED, Color.BLUE, lineWidth, dashiness, dashPeriod)

                renderer.drawLine(bottom, left, Color.RED, Color.GREEN, lineWidth, dashiness, dashPeriod)
                renderer.drawLine(bottom, right, Color.RED, Color.GREEN, lineWidth, dashiness, dashPeriod)
                renderer.drawLine(bottom, front, Color.RED, Color.BLUE, lineWidth, dashiness, dashPeriod)
                renderer.drawLine(bottom, back, Color.RED, Color.BLUE, lineWidth, dashiness, dashPeriod)

                // Example of drawing a line connecting multiple points
                val points = listOf(top, right, front, bottom, left, back, top)
                renderer.drawLine(points, lineColor, lineWidth, dashiness, dashPeriod)
            }
        }
    }
}
