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
import com.lambda.config.groups.EntityColorSettings
import com.lambda.config.groups.EntitySelectionSettings
import com.lambda.config.groups.OutlineSettings
import com.lambda.config.groups.WorldLineSettings
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.NamedEnum
import com.lambda.util.math.setAlpha
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import java.awt.Color

object ESP : Module(
	name = "ESP",
	description = "Highlight entities with smooth interpolated rendering",
	tag = ModuleTag.RENDER
) {
	private enum class Group(override val displayName: String): NamedEnum {
		General("General"),
		Shader("Shader"),
		Box("Box"),
//		Frame("Frame"),
		Entities("Entities"),
		Colors("Colors")
	}

	private val mode by setting("Mode", EspMode.Shader).group(Group.General)
	private val depthTest by setting("Depth Test", false, "Blend ESP renders into the world").group(Group.General)

	private val outlineStyle = OutlineSettings(c = this, baseGroup = arrayOf(Group.Shader)) { mode == EspMode.Shader }

	private var drawFilled: Boolean by setting("Box Fill", true, "Fill entity boxes") { mode == EspMode.Box }.group(Group.Box)
		.onValueChange { _, to -> if (!to && !drawOutline) drawOutline = true }
	private var drawOutline: Boolean by setting("Box Outline", true, "Draw box outlines") { mode == EspMode.Box }.group(Group.Box)
		.onValueChange { _, to -> if (!to && !drawFilled) drawFilled = true }
	private val boxOutlineSettings = WorldLineSettings(c = this, baseGroup = arrayOf(Group.Box)) { mode == EspMode.Box && drawOutline }.apply {
		applyEdits {
			hide(::startColor, ::endColor)
		}
	}
	private val fillAlpha by setting("Filled Alpha", 0.2, 0.0..1.0, 0.05) { mode == EspMode.Box && drawFilled }.group(Group.Box)
	private val outlineAlpha by setting("Outline Alpha", 0.8, 0.0..1.0, 0.05) { mode == EspMode.Box && drawOutline }.group(Group.Box)

	private val entitySettings = EntitySelectionSettings(c = this, baseGroup = arrayOf(Group.Entities))
	private val entityColors = EntityColorSettings(c = this, baseGroup = arrayOf(Group.Colors))

	init {
		immediateRenderer("EntityESP Immediate Renderer", depthTest = { depthTest }) { safeContext ->
			with(safeContext) {
				world.entities.forEach { entity ->
					if (!entitySettings.isSelected(entity)) return@forEach
					val color = entityColors.getColor(entity)
					drawEsp<Entity>(
						entity,
						color,
						{ worldOutline(it, outlineStyle.toStyle(color)) },
						{ listOf(it.interpolatedBox) }
					)
				}
				val chunkMap = world.chunkManager.chunks
				(0 until chunkMap.chunks.length()).forEach { chunk ->
					chunkMap.chunks.get(chunk)?.blockEntities?.values?.forEach { blockEntity ->
						if (!entitySettings.isSelected(blockEntity)) return@forEach
						val color = entityColors.getColor(blockEntity)
						drawEsp<BlockEntity>(
							blockEntity,
							color,
							{ worldOutline(it.pos, outlineStyle.toStyle(color)) },
							{ entity ->
								entity.cachedState.getOutlineShape(world, entity.pos).boundingBoxes.map { box ->
									box.offset(entity.pos)
								}
							}
						)
					}
				}
			}
		}
	}

	private fun <T> RenderBuilder.drawEsp(
		entity: T,
		color: Color,
		outline: RenderBuilder.(T) -> Unit,
		boxes: (T) -> Collection<Box>
	) {
		when (mode) {
			EspMode.Shader -> outline(entity)
			EspMode.Box -> {
				boxes(entity).forEach { box ->
					box(box, boxOutlineSettings) {
						if (!drawFilled) hideFill()
						else if (!drawOutline) hideOutline()
						colors(color.setAlpha(fillAlpha), color.setAlpha(outlineAlpha))
					}
				}
			}
		}
	}

	private enum class EspMode {
		Shader,
		Box,
		//ToDo: Implement
//		Frame
	}
}
