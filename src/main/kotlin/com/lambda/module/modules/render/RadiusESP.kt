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

import com.lambda.config.SettingEditor.forEachSetting
import com.lambda.config.SettingEditor.hide
import com.lambda.config.Group
import com.lambda.config.settings.blocks.WorldLineSettings
import com.lambda.config.withEdits
import com.lambda.context.SafeContext
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.setAlpha
import com.lambda.util.world.toBlockPos
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BeaconBlockEntity
import net.minecraft.util.math.Box
import java.awt.Color

@Suppress("unused")
object RadiusESP : Module(
	name = "RadiusESP",
	description = "Shows the radius for blocks with abnormal functionality",
	tag = ModuleTag.Render
) {
	private const val RenderGroup = "Render"
	private const val OutlineGroup = "Outline"

	private val beacons by setting("Beacons", true).onValueChange(::rebuildMesh)
	private val spawners by setting("Spawners", true).onValueChange(::rebuildMesh)

	@Group(RenderGroup) private val beaconColor by setting("Beacon Color", Color(0, 255, 255, 255)) { beacons }.onValueChange(::rebuildMesh)
	@Group(RenderGroup) private val spawnerColor by setting("Spawner Color", Color(255, 0, 0, 255)) { spawners }.onValueChange(::rebuildMesh)
	@Group(RenderGroup) private var fill: Boolean by setting("Box Fill", true).onValueChange(::rebuildMesh)
		.onValueChange { _, to -> if (!to) outline = true }
	@Group(RenderGroup) private var outline: Boolean by setting("Box Outline", true).onValueChange(::rebuildMesh)
		.onValueChange { _, to -> if (!to) fill = true }
	@Group(RenderGroup) private val fillAlpha by setting("Fill Alpha", 0.1, 0.0..1.0, 0.01).onValueChange(::rebuildMesh)
	@Group(RenderGroup, OutlineGroup) private val worldLineConfig by configBlock(WorldLineSettings(this))
		.withEdits {
			hide(::startColor, ::endColor)
			forEachSetting {
				visibility { old -> { old() && outline } }
				onValueChange(::rebuildMesh)
			}
		}

	private val chunkedRenderer = chunkedRenderer("RadiusESP Chunked Renderer") { pos ->
		runSafe {
			val blockPos = pos.toBlockPos()
			val blockState = blockState(blockPos)
			if (blockState.block === Blocks.SPAWNER && spawners) {
				val center = blockPos.toCenterPos()
				val box = Box(center, center).expand(16.0)
				renderBox(box, spawnerColor)
			}
		}
	}

	init {
		immediateRenderer("RadiusESP Immediate Renderer") {
			if (!beacons) return@immediateRenderer
			runSafe {
				val chunks = world.chunkManager.chunks.chunks
				(0 until chunks.length()).forEach { chunk ->
					chunks.get(chunk)?.blockEntities?.values?.forEach { blockEntity ->
						val beacon = blockEntity as? BeaconBlockEntity ?: return@forEach
						val level = beacon.level
						val radius = (level * 10) + 10.0
						val box =
							Box(blockEntity.pos).expand(radius)
								.stretch(0.0, world.height.toDouble(), 0.0)
						renderBox(box, beaconColor)
					}
				}
			}
		}
	}

	private fun RenderBuilder.renderBox(box: Box, color: Color) =
		box(box, worldLineConfig) {
			colors(color.setAlpha(fillAlpha), color)
			if (!fill) hideFill()
			if (!outline) hideOutline()
		}

	private fun rebuildMesh(ctx: SafeContext, from: Any? = null, to: Any? = null): Unit = chunkedRenderer.rebuild()
}