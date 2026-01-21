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
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.util.math.Box
import java.awt.Color

object BlockOutline : Module(
	name = "BlockOutline",
	description = "Overrides the default block outline rendering",
	tag = ModuleTag.RENDER
) {
	private val fill by setting("Fill", true)
	private val fillColor by setting("Fill Color", Color(255, 255, 255, 20)) { fill }
	private val outline by setting("Outline", true)
	private val outlineColor by setting("Outline Color", Color(255, 255, 255, 120)) { outline }
	private val lineWidth by setting("Line Width", 5, 1..50, 1) { outline }
	private val interpolate by setting("Interpolate", true)
	private val throughWalls by setting("ESP", true)
		.onValueChange { _, to -> renderer.depthTest = !to }

	val renderer = ImmediateRenderer("BlockOutline")

	var previous: List<Box>? = null

	init {
		listen<RenderEvent.Render> {
			renderer.tick()

			val hitResult = mc.crosshairTarget?.blockResult ?: return@listen
			val pos = hitResult.blockPos
			val blockState = blockState(pos)
			val boxes = blockState
				.getOutlineShape(world, pos)
				.boundingBoxes
				.let { boxes ->
					boxes.mapIndexed { index, box ->
						val offset = box.offset(pos)
						val interpolated = previous?.let { previous ->
							if (!interpolate || previous.size < boxes.size) null
							else lerp(mc.tickDelta, previous[index], offset)
						} ?: offset
						interpolated.expand(0.0001)
					}
				}

			renderer.shapes {
				boxes.forEach { box ->
					box(box, lineWidth * 0.001f) {
						colors(fillColor, outlineColor)
						if (!fill) hideFill()
						if (!outline) hideOutline()
					}
				}
			}

			renderer.upload()
			renderer.render()
		}

		listen<TickEvent.Post> {
			val hitResult = mc.crosshairTarget?.blockResult ?: return@listen
			val state = blockState(hitResult.blockPos)
			previous = state
				.getOutlineShape(world, hitResult.blockPos).boundingBoxes
				.map { it.offset(hitResult.blockPos) }
		}
	}
}