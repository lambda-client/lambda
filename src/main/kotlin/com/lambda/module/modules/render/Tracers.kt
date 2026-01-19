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

package com.lambda.module.modules.render

import com.lambda.event.events.RenderEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.prevPos
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import org.joml.component1
import org.joml.component2
import java.awt.Color

object Tracers : Module(
	name = "Tracers",
	description = "Draws lines to entities within the world",
	tag = ModuleTag.RENDER
) {
	private val color by setting("Color", Color.RED)
	private val friendColor by setting("Friend Color", Color.BLUE)
	private val width by setting("Width", 0.0004f, 0.0001f..0.01f, 0.0001f)

	val renderer = ImmediateRenderer("Tracers")

	init {
		listen<RenderEvent.Render> {
			renderer.tick()
			renderer.shapes {
				world.entities.forEach { entity ->
					val (toX, toY) = RenderMain.worldToScreenNormalized(lerp(mc.tickDelta, entity.prevPos, entity.pos)) ?: return@forEach
					screenLine(0.5f, 0.5f, toX, toY, color, width)
				}
			}
			renderer.upload()
			renderer.render()
			renderer.renderScreen()
		}
	}
}