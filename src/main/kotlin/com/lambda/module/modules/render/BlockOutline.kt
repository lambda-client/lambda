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
import com.lambda.graphics.mc.ImmediateRegionESP
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.block.BlockState
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
	private val lineWidth by setting("Line Width", 0.01f, 0.001f..1.0f, 0.001f) { outline }
	private val interpolate by setting("Interpolate", true)
	private val throughWalls by setting("ESP", true)
		.onValueChange { _, to -> renderer.depthTest = !to }

	val renderer = ImmediateRegionESP("BlockOutline")

	var previous: Pair<List<Box>, BlockState>? = null

	init {
		listen<RenderEvent.Render> {
			renderer.tick()

			val hitResult = mc.crosshairTarget?.blockResult ?: return@listen
			val pos = hitResult.blockPos
			val blockState = blockState(pos)
			val boxes = blockState
				.getOutlineShape(world, pos)
				.boundingBoxes
				.mapIndexed { index, box ->
					val offset = box.offset(pos)
					val interpolated = previous?.let { previous ->
						if (!interpolate || previous.second !== blockState) null
						else lerp(mc.tickDelta, previous.first[index], offset)
					} ?: offset
					interpolated.expand(0.001)
				}

			renderer.shapes {
				boxes.forEach { box ->
					box(box, lineWidth) {
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
			previous = Pair(
				state
					.getOutlineShape(world, hitResult.blockPos).boundingBoxes
					.map { it.offset(hitResult.blockPos) },
				state
			)
		}
	}
}