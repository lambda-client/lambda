
package com.minato.module.modules.render

import com.minato.config.ConfigEditor.forEachSetting
import com.minato.config.ConfigEditor.hide
import com.minato.config.Group
import com.minato.config.blocks.WorldLineSettings
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.BlockUtils.blockState
import com.minato.util.math.setAlpha
import com.minato.util.world.toBlockPos
import net.minecraft.block.Blocks
import net.minecraft.block.entity.BeaconBlockEntity
import net.minecraft.block.entity.BlockEntity
import net.minecraft.util.math.Box
import java.awt.Color

@Suppress("unused")
object RadiusESP : Module(
	name = "RadiusESP",
	description = "Shows the radius for blocks with abnormal functionality",
	tag = ModuleTag.RENDER
) {
	private const val RENDER_GROUP = "Render"
	private const val OUTLINE_GROUP = "Outline"

	private val beacons by setting("Beacons", true).onValueChange(::rebuildMesh)
	private val showMaxBeaconRange by setting("Show Max Beacon Range", true) { beacons }.onValueChange(::rebuildMesh)
	private val spawners by setting("Spawners", true).onValueChange(::rebuildMesh)

	@Group(RENDER_GROUP) private val beaconColor by setting("Beacon Color", Color(0, 255, 255, 255)) { beacons }.onValueChange(::rebuildMesh)
	@Group(RENDER_GROUP) private val spawnerColor by setting("Spawner Color", Color(255, 0, 0, 255)) { spawners }.onValueChange(::rebuildMesh)
	@Group(RENDER_GROUP) private var fill: Boolean by setting("Box Fill", true).onValueChange(::rebuildMesh)
		.onValueChange { _, to -> if (!to) outline = true }
	@Group(RENDER_GROUP) private var outline: Boolean by setting("Box Outline", true).onValueChange(::rebuildMesh)
		.onValueChange { _, to -> if (!to) fill = true }
	@Group(RENDER_GROUP) private val fillAlpha by setting("Fill Alpha", 0.1, 0.0..1.0, 0.01).onValueChange(::rebuildMesh)
	@Group(RENDER_GROUP, OUTLINE_GROUP) private val worldLineConfig by configBlock(WorldLineSettings(this))
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
						if (!showMaxBeaconRange) {
							withBeaconBox(blockEntity, level) { box ->
								renderBox(box, beaconColor)
							}
							return@runSafe
						}

						withBeaconBox(blockEntity, 4) { box ->
							renderBox(box, beaconColor)
						}
					}
				}
			}
		}
	}

	private fun SafeContext.withBeaconBox(blockEntity: BlockEntity, level: Int, block: (Box) -> Unit) {
		val radius = (level * 10) + 10.0
		val box =
			Box(blockEntity.pos).expand(radius)
				.stretch(0.0, world.height.toDouble(), 0.0)
		block(box)
	}

	private fun RenderBuilder.renderBox(
		box: Box,
		color: Color
	) = box(box, worldLineConfig) {
		colors(color.setAlpha(fillAlpha), color)
		if (!fill) hideFill()
		if (!outline) hideOutline()
	}

	private fun rebuildMesh(ctx: SafeContext, from: Any? = null, to: Any? = null): Unit = chunkedRenderer.rebuild()
}