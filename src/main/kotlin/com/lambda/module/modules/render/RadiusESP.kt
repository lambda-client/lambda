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
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.NamedEnum
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
	tag = ModuleTag.RENDER
) {
	private enum class Group(override val displayName: String) : NamedEnum {
		Blocks("Blocks"),
		Render("Render")
	}

	private enum class RenderGroup(override val displayName: String) : NamedEnum {
		General("General"),
		Outline("Outline")
	}

	private val beacons by setting("Beacons", true).group(Group.Blocks).onValueChange(::rebuildMesh)
	private val spawners by setting("Spawners", true).group(Group.Blocks).onValueChange(::rebuildMesh)

	private val beaconColor by setting("Beacon Color", Color(0, 255, 255, 255)) { beacons }.group(Group.Render, RenderGroup.General).onValueChange(::rebuildMesh)
	private val spawnerColor by setting("Spawner Color", Color(255, 0, 0, 255)) { spawners }.group(Group.Render, RenderGroup.General).onValueChange(::rebuildMesh)
	private var fill: Boolean by setting("Fill", true).group(Group.Render, RenderGroup.General).onValueChange(::rebuildMesh)
		.onValueChange { _, to -> if (!to) outline = true }
	private var outline: Boolean by setting("Outline", true).group(Group.Render, RenderGroup.General).onValueChange(::rebuildMesh)
		.onValueChange { _, to -> if (!to) fill = true }
	private val fillAlpha by setting("Fill Alpha", 0.1, 0.0..1.0, 0.01).group(Group.Render, RenderGroup.General).onValueChange(::rebuildMesh)
	private val worldLineConfig = WorldLineSettings(this, Group.Render, RenderGroup.Outline) { outline }.apply {
		applyEdits {
			hide(::startColor, ::endColor)
			settings.forEach { it.onValueChange(::rebuildMesh) }
		}
	}

	private val chunkedRenderer = chunkedRenderer("RadiusESP Chunked Renderer") { _, pos ->
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
		immediateRenderer("RadiusESP Immediate Renderer") { safeContext ->
			if (!beacons) return@immediateRenderer
			with(safeContext) {
				val chunks = world.chunkManager.chunks.chunks
				(0 until chunks.length()).forEach { chunk ->
					chunks.get(chunk)?.blockEntities?.values?.forEach { blockEntity ->
						val beacon = blockEntity as? BeaconBlockEntity ?: return@forEach
						val level = beacon.level
						val radius = (level * 10) + 10.0
						val box =
							Box(blockEntity.pos).expand(radius)
								.stretch(0.0, safeContext.world.height.toDouble(), 0.0)
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