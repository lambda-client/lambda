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
import com.lambda.config.Tab
import com.lambda.config.applyEdits
import com.lambda.config.blocks.EntityColorSettings
import com.lambda.config.blocks.EntitySelectionSettings
import com.lambda.config.blocks.OutlineSettings
import com.lambda.config.blocks.WorldLineSettings
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

@Suppress("unused")
object ESP : Module(
	name = "ESP",
	description = "Highlight entities with smooth interpolated rendering",
	tag = ModuleTag.Render
) {
	private enum class EspMode {
		Shader,
		Box,
		//ToDo: Implement
//		Frame
	}

	private const val GeneralTab = "General"
	private const val EntitiesTab = "Entities"
	private const val ColorsTab = "Colors"

	private const val BoxOutlineGroup = "Outline"

	private enum class BoxGroup(override val displayName: String) : NamedEnum {
		Fill("Fill"),
		Outline("Outline")
	}

	@Tab(GeneralTab) private val mode by setting("Mode", EspMode.Shader)
	@Tab(GeneralTab) private val depthTest by setting("Depth Test", false, "Blend ESP renders into the world")

	//Shader Outline
	@Tab(GeneralTab) private val outlineStyle = settingBlock(OutlineSettings(this), { mode == EspMode.Shader })

	//Box
	@Tab(GeneralTab) private var drawFilled: Boolean by setting("Box Fill", true, "Fill entity boxes") { mode == EspMode.Box }
		.onValueChange { _, to -> if (!to && !drawOutline) drawOutline = true }
	@Tab(GeneralTab) private val fillAlpha by setting("Filled Alpha", 0.2, 0.0..1.0, 0.05) { mode == EspMode.Box && drawFilled }
	@Tab(GeneralTab) @Group(BoxOutlineGroup) private var drawOutline: Boolean by setting("Box Outline", true, "Draw box outlines") { mode == EspMode.Box }
		.onValueChange { _, to -> if (!to && !drawFilled) drawFilled = true }
	@Tab(GeneralTab) @Group(BoxOutlineGroup) private val outlineAlpha by setting("Outline Alpha", 0.8, 0.0..1.0, 0.05) { mode == EspMode.Box && drawOutline }
	@Tab(GeneralTab) @Group(BoxOutlineGroup) private val boxOutlineSettings =
		settingBlock(
			WorldLineSettings(this),
			{ mode == EspMode.Box && drawOutline }
		) { applyEdits { hide(::startColor, ::endColor) } }

	@Tab(EntitiesTab) private val entitySettings = EntitySelectionSettings(this)
	@Tab(ColorsTab) private val entityColors = EntityColorSettings(this)

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
				val chunks = world.chunkManager.chunks.chunks
				(0 until chunks.length()).forEach { chunk ->
					chunks.get(chunk)?.blockEntities?.values?.forEach { blockEntity ->
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
}
