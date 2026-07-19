
package com.minato.module.modules.render

import com.minato.config.ConfigEditor.forEachSetting
import com.minato.config.ConfigEditor.hide
import com.minato.config.Group
import com.minato.config.Tab
import com.minato.config.blocks.EntityColorSettings
import com.minato.config.blocks.EntitySelectionSettings
import com.minato.config.blocks.OutlineSettings
import com.minato.config.blocks.WorldLineSettings
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.withEdits
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.NamedEnum
import com.minato.util.math.setAlpha
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.Box
import java.awt.Color

@Suppress("unused")
object Esp : Module(
	name = "ESP",
	description = "Highlight entities with smooth interpolated rendering",
	tag = ModuleTag.RENDER
) {
	private enum class EspMode {
		Shader,
		Box,
		//ToDo: Implement
//		Frame
	}

	private const val GENERAL_TAB = "General"
	private const val ENTITIES_TAB = "Entities"
	private const val COLORS_TAB = "Colors"

	private const val BOX_OUTLINE_GROUP = "Outline"

	private enum class BoxGroup(override val displayName: String) : NamedEnum {
		Fill("Fill"),
		Outline("Outline")
	}

	@Tab(GENERAL_TAB) private val mode by setting("Mode", EspMode.Shader)
	@Tab(GENERAL_TAB) private val depthTest by setting("Depth Test", false, "Blend ESP renders into the world")

	//Shader Outline
	@Tab(GENERAL_TAB) private val outlineStyle by configBlock(OutlineSettings(this))
		.withEdits { forEachSetting { visibility { old -> { old() && mode == EspMode.Shader } } } }

	//Box
	@Tab(GENERAL_TAB) private var drawFilled: Boolean by setting("Box Fill", true, "Fill entity boxes") { mode == EspMode.Box }
		.onValueChange { _, to -> if (!to && !drawOutline) drawOutline = true }
	@Tab(GENERAL_TAB) private val fillAlpha by setting("Filled Alpha", 0.2, 0.0..1.0, 0.05) { mode == EspMode.Box && drawFilled }
	@Tab(GENERAL_TAB) @Group(BOX_OUTLINE_GROUP) private var drawOutline: Boolean by setting("Box Outline", true, "Draw box outlines") { mode == EspMode.Box }
		.onValueChange { _, to -> if (!to && !drawFilled) drawFilled = true }
	@Tab(GENERAL_TAB) @Group(BOX_OUTLINE_GROUP) private val outlineAlpha by setting("Outline Alpha", 0.8, 0.0..1.0, 0.05) { mode == EspMode.Box && drawOutline }
	@Tab(GENERAL_TAB) @Group(BOX_OUTLINE_GROUP) private val boxOutlineSettings by configBlock(WorldLineSettings(this))
		.withEdits {
			forEachSetting { visibility { old -> { old() && mode == EspMode.Box && drawOutline } } }
			hide(::startColor, ::endColor)
		}

	@Tab(ENTITIES_TAB) private val entitySettings by configBlock(EntitySelectionSettings(this))
	@Tab(COLORS_TAB) private val entityColors by configBlock(EntityColorSettings(this))

	init {
		immediateRenderer("EntityESP Immediate Renderer", depthTest = { depthTest }) {
			runSafe {
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
