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

import com.lambda.config.Group
import com.lambda.config.applyEdits
import com.lambda.config.settings.blocks.OutlineSettings
import com.lambda.config.settings.blocks.WorldLineSettings
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.ChatUtils.colors
import com.lambda.util.extension.tickDelta
import com.lambda.util.math.lerp
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import net.minecraft.client.toast.SystemToast.hide
import net.minecraft.util.math.Box
import java.awt.Color

object BlockOutline : Module(
	name = "BlockOutline",
	description = "Overrides the default block outline rendering",
	tag = ModuleTag.Render
) {
	private enum class Mode {
		Boxes,
		Outline
	}

	private val mode by setting("Mode", Mode.Boxes)
	private val interpolate by setting("Interpolate", true) { mode == Mode.Boxes }
	private val depthTest by setting("Depth Test", true)

	private const val BoxFillGroup = "Box Fill"
	private const val BoxOutlineGroup = "Box Outline"
	private const val OutlineGroup = "Outline"

	@Group(BoxFillGroup) private val fill by setting("Fill", true) { mode == Mode.Boxes }
	@Group(BoxFillGroup) private val fillColor by setting("Fill Color", Color(255, 255, 255, 20)) { fill && mode == Mode.Boxes }
	@Group(BoxOutlineGroup) private val boxOutline by setting("Box Outline", true) { mode == Mode.Boxes }
	@Group(BoxOutlineGroup) private val boxOutlineColor by setting("Box Outline Color", Color(255, 255, 255, 120)) { mode == Mode.Boxes && boxOutline }
	@Group(BoxOutlineGroup) private val lineConfig by settingBlock(WorldLineSettings(this)) {
		applyEdits {
			hide(::startColor, ::endColor)
			forEachSetting {
				visibility { old -> { old() && mode == Mode.Boxes } }
			}
		}
	}

	@Group(OutlineGroup) private val outlineColor by setting("Outline Color", boxOutlineColor) { mode == Mode.Outline }
	@Group(OutlineGroup) private val outlineStyle by settingBlock(OutlineSettings(this)) {
		applyEdits {
			forEachSetting {
				visibility { old -> { old() && mode == Mode.Outline } }
			}
		}
	}

	var previous: List<Box>? = null

	init {
		immediateRenderer("BlockOutline Immediate Renderer", depthTest = { depthTest }) { safeContext ->
			with(safeContext) {
				val hitResult = mc.crosshairTarget?.blockResult ?: return@with
				val pos = hitResult.blockPos
				if (mode == Mode.Outline) {
					worldOutline(pos, outlineStyle.toStyle(outlineColor))
					return@with
				}
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

				boxes.forEach { box ->
					box(box, lineConfig) {
						colors(fillColor, boxOutlineColor)
						if (!fill) hideFill()
						if (!boxOutline) hideOutline()
					}
				}
			}
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