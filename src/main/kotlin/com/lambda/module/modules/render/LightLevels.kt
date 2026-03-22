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

import com.lambda.config.applyEdits
import com.lambda.config.groups.WorldLineSettings
import com.lambda.context.SafeContext
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.lambda.graphics.mc.renderer.TickedRenderer.Companion.tickedRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.setAlpha
import com.lambda.util.math.vec3d
import com.lambda.util.world.toBlockPos
import net.minecraft.registry.tag.BlockTags
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import net.minecraft.world.LightType
import java.awt.Color

object LightLevels : Module(
	name = "LightLevels",
	description = "Shows light level. Helpful for mob-proofing areas",
	tag = ModuleTag.RENDER
) {
	private val mode: Mode by setting("Mode", Mode.Chunked)
		.onValueChange { _, _ -> chunkedRenderer.clear(); refreshChunkedRenderer(this) }
	private val minLightLevel by setting("Min Light Level", 0, 0..15).onValueChange(::refreshChunkedRenderer)
	private val renderMode by setting("Render Mode", RenderMode.Square).onValueChange(::refreshChunkedRenderer)
	private val color by setting("Color", Color.RED).onValueChange(::refreshChunkedRenderer)
	private val size by setting("Size", 14, 1..16).onValueChange(::refreshChunkedRenderer)
	private val fill by setting("Fill", false) { renderMode == RenderMode.Square }.onValueChange(::refreshChunkedRenderer)
	private val outline by setting("Outline", true) { renderMode == RenderMode.Square }.onValueChange(::refreshChunkedRenderer)
	private val fillAlpha by setting("Fill Alpha", 20, 1..100) { renderMode == RenderMode.Square && fill }.onValueChange(::refreshChunkedRenderer)
	private val worldLineConfig = WorldLineSettings(c = this) { renderMode != RenderMode.Square || outline }.apply {
		applyEdits {
			hide(::startColor, ::endColor)
			settings.forEach { it.onValueChange(::refreshChunkedRenderer) }
		}
	}
	private val depthTest by setting("Depth Test", false, "Shows renders through terrain")
	private val horizontalRange by setting("Horizontal Range", 8, 1..16) { mode == Mode.Radius }
	private val verticalRange by setting("Vertical Range", 4, 1..16) { mode == Mode.Radius }

	private val chunkedRenderer = chunkedRenderer("LightLevels Chunked Renderer", { depthTest }) { _, pos ->
		if (mode != Mode.Chunked) return@chunkedRenderer
		runSafe { buildRender(pos.toBlockPos()) }
	}

	init {
		tickedRenderer("LightLevels Ticked Renderer", { depthTest }) {
			if (mode != Mode.Radius) return@tickedRenderer
			runSafe {
				val playerPos = mc.gameRenderer.camera.pos.flooredBlockPos
				val x = playerPos.x - horizontalRange
				val z = playerPos.z - horizontalRange
				val y = playerPos.y - verticalRange
				(x..x + (horizontalRange * 2)).forEach { x ->
					(z..z + (horizontalRange * 2)).forEach { z ->
						(y..z + (verticalRange * 2)).forEach { y ->
							buildRender(BlockPos(x, y, z))
						}
					}
				}
			}
		}
	}

	context(safeContext: SafeContext)
	private fun RenderBuilder.buildRender(pos: BlockPos) = with(safeContext) {
		if (!hasSpawnPotential(pos)) return@with

		val renderVec = pos.vec3d
		val trueSize = (16 - size) / 32.0
		val corner1 = renderVec.add(trueSize, 0.001, trueSize)
		val corner2 = renderVec.add(1.0 - trueSize, 0.001, trueSize)
		val corner3 = renderVec.add(1.0 - trueSize, 0.001, 1.0 - trueSize)
		val corner4 = renderVec.add(trueSize, 0.001, 1.0 - trueSize)

		if (world.getLightLevel(LightType.BLOCK, pos) > minLightLevel) return@with

		val dashStyle = worldLineConfig.getDashStyle()
		when(renderMode) {
			RenderMode.Square -> {
				if (fill) filledQuad(corner1, corner2, corner3, corner4, color.setAlpha(fillAlpha))
				if (outline) polyline(listOf(corner1, corner2, corner3, corner4, corner1), color, worldLineConfig.width, dashStyle)
			}
			RenderMode.Cross -> {
				line(corner1, corner3, color, worldLineConfig.width, dashStyle)
				line(corner2, corner4, color, worldLineConfig.width, dashStyle)
			}
			RenderMode.Circle -> circleLine(renderVec.add(0.5, 0.0, 0.5), (size / 32.0), color, worldLineConfig.width, dashStyle = dashStyle)
		}
	}

	private fun refreshChunkedRenderer(ctx: SafeContext, from: Any? = null, to: Any? = null) {
		if (mode == Mode.Chunked) chunkedRenderer.rebuild()
	}

	private fun SafeContext.hasSpawnPotential(pos: BlockPos) =
		blockState(pos).let { state ->
			!state.isFullCube(world, pos) &&
					!state.emitsRedstonePower() &&
					state.fluidState.isEmpty &&
					!state.isIn(BlockTags.PREVENT_MOB_SPAWNING_INSIDE) &&
					pos.down().let { blockState(it).isSideSolidFullSquare(world, it, Direction.UP) }
		}

	private enum class Mode {
		Chunked,
		Radius
	}

	private enum class RenderMode {
		Square,
		Cross,
		Circle
	}
}