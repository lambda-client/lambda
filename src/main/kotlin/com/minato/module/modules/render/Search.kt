
package com.minato.module.modules.render

import com.minato.config.ConfigEditor.editTypedSettings
import com.minato.config.ConfigEditor.forEachSetting
import com.minato.config.ConfigEditor.hide
import com.minato.config.Group
import com.minato.config.blocks.ScreenLineSettings
import com.minato.config.blocks.WorldLineSettings
import com.minato.config.entries.Setting.Companion.onValueChange
import com.minato.config.settings.collections.CollectionSetting.Companion.onDeselect
import com.minato.config.settings.collections.CollectionSetting.Companion.onSelect
import com.minato.config.withEdits
import com.minato.context.SafeContext
import com.minato.graphics.mc.RenderBuilder
import com.minato.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.minato.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.minato.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.minato.graphics.util.DirectionMask
import com.minato.graphics.util.DirectionMask.buildSideMesh
import com.minato.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.minato.module.Module
import com.minato.module.tag.ModuleTag
import com.minato.threading.runSafe
import com.minato.util.BlockUtils.blockState
import com.minato.util.EntityUtils.decorationEntityMap
import com.minato.util.EntityUtils.entityGroup
import com.minato.util.extension.blockColor
import com.minato.util.extension.entityColor
import com.minato.util.extension.getBlockState
import com.minato.util.math.setAlpha
import com.minato.util.world.toBlockPos
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color
import java.util.concurrent.ConcurrentHashMap

@Suppress("unused")
object Search : Module(
    name = "Search",
    description = "Highlight blocks within the rendered world",
    tag = ModuleTag.RENDER,
) {
    private const val FILL_GROUP = "Fill"
    private const val OUTLINE_GROUP = "Outline"
    private const val TRACERS_GROUP = "Tracers"

    private val blocks by setting("Blocks", setOf(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY), description = "Render blocks")
        .onSelect { rebuildMesh(this) }.onDeselect { rebuildMesh(this) }
    private val entities by setting("Entities", decorationEntityMap.values)
        .onSelect { rebuildMesh(this) }.onDeselect { rebuildMesh(this) }

    private val mesh by setting("Mesh", true, "Connect similar adjacent blocks").onValueChange(::rebuildMesh)

    private val useNaturalColor by setting("Use Natural Color", true, "Use the color of the block instead").onValueChange(::rebuildMesh)
    private val naturalColorAlpha by setting("Natural Color Alpha", 0.3, 0.1..1.0, 0.05) { useNaturalColor }.onValueChange(::rebuildMesh)
    private val naturalTracerAlpha by setting("Natural Tracer Alpha", 1.0, 0.1..1.0, 0.05) { useNaturalColor }.onValueChange(::rebuildMesh)
    private val minimumNaturalBrightness by setting("Min Brightness", 150, 0..255, 1) { useNaturalColor }.onValueChange(::rebuildMesh)

    @Group(FILL_GROUP) private var fill: Boolean by setting("Fill", true, "Fill the faces of blocks").onValueChange(::rebuildMesh)
        .onValueChange { _, to -> if (!to) outline = true }
    @Group(FILL_GROUP) private val blockFillColor by setting("Block Fill Color", Color(100, 150, 255, 51), "Color of the surfaces") { fill && !useNaturalColor }.onValueChange(::rebuildMesh)
    @Group(FILL_GROUP) private val entityFillColor by setting("Entity Fill Color", Color(100, 150, 255, 51)) { fill && !useNaturalColor }.onValueChange(::rebuildMesh)

    @Group(OUTLINE_GROUP) private var outline: Boolean by setting("Outline", true, "Draw the outlines of blocks").onValueChange(::rebuildMesh)
        .onValueChange { _, to -> if (!to) fill = true }
    @Group(OUTLINE_GROUP) private val blockLineColor by setting("Block Line Color", Color(100, 150, 255, 128)) { outline && !useNaturalColor }.onValueChange(::rebuildMesh)
    @Group(OUTLINE_GROUP) private val entityOutlineColor by setting("Entity Outline Color", Color(100, 150, 255, 128)) { outline && !useNaturalColor }.onValueChange(::rebuildMesh)

    @Group(OUTLINE_GROUP) private val blockOutlineMode by setting("Block Outline Mode", DirectionMask.OutlineMode.And, "Outline mode") { outline }.onValueChange(::rebuildMesh)
    @Group(OUTLINE_GROUP) private val outlineConfig by configBlock(WorldLineSettings(this))
        .withEdits {
            hide(::startColor, ::endColor)
            forEachSetting {
                visibility { old -> { old() && outline } }
                onValueChange(::rebuildMesh)
            }
        }
    @Group(TRACERS_GROUP) private val tracers by setting("Tracers", true, "Draw a line from your cursor to the highlighted position")
    @Group(TRACERS_GROUP) private val tracerConfig by configBlock(ScreenLineSettings(this))
        .withEdits {
            forEachSetting { visibility { old -> { old() && tracers } } }
            editTypedSettings(::startColor, ::endColor) {
                visibility { { !useNaturalColor } }
            }
        }

    private val tracerBlockPositions = ConcurrentHashMap<BlockPos, Pair<Vec3d, Pair<Color, Color>>>()

    val chunkedRenderer = chunkedRenderer(
	    "Search Chunked Renderer",
	    { chunkPos -> if (tracers) tracerBlockPositions.keys.removeIf { it in chunkPos } },
	    { chunkPos -> if (tracers) tracerBlockPositions.keys.removeIf { it in chunkPos } }
	) { position ->
	    runSafe {
		    val pos = position.toBlockPos()
		    val state = blockState(pos)
		    if (state.block !in blocks) return@chunkedRenderer
		    val sides = if (mesh) {
			    buildSideMesh(position) {
				    world.getBlockState(it).block in blocks
			    }
		    } else DirectionMask.ALL

		    val lineColor = getBlockColor(state, position.toBlockPos())
		    val fillColor = Color(lineColor.red, lineColor.green, lineColor.blue, (naturalColorAlpha * 255).toInt())
		    val shape = state.getOutlineShape(world, pos)
		    val boxes =
			    if (shape.isEmpty) listOf(Box(pos))
			    else shape.boundingBoxes.map { it.offset(pos) }
		    if (tracers) {
			    val center = shape
				    .boundingBoxes
				    .reduce(Box::union)
				    .offset(pos)
				    .center
			    tracerBlockPositions[pos] = Pair(center, getTracerColors(lineColor))
		    }
		    box(
			    boxes,
			    sides.inv(),
			    if (useNaturalColor) fillColor else blockFillColor,
			    if (useNaturalColor) lineColor else blockLineColor
		    )
	    }
	}

    init {
        immediateRenderer("Search Immediate Renderer") {
			runSafe {
				world.entities.forEach { entity ->
					if (entity.entityGroup.nameToDisplayNameMap[entity::class.simpleName] in entities) {
						val entityColor = getEntityColor(entity)
						box(
							listOf(entity.interpolatedBox),
							DirectionMask.NONE,
							if (useNaturalColor) entityColor.setAlpha(naturalColorAlpha) else entityFillColor,
							if (useNaturalColor) entityColor else entityOutlineColor
						)
						if (tracers) tracer(Pair(entity.interpolatedBox.center, getTracerColors(entityColor)))
					}
				}
			}
            if (tracers) tracerBlockPositions.values.forEach { tracer(it) }
        }
    }

	private fun RenderBuilder.tracer(pair: Pair<Vec3d, Pair<Color, Color>>) {
		val endPoint = worldToScreenNormalized(pair.first) ?: return
		val startColor = if (useNaturalColor) pair.second.first else tracerConfig.startColor
		val endColor = if (useNaturalColor) pair.second.second else tracerConfig.endColor
		screenLineGradient(
			0.5f, 0.5f,
			startColor,
			endPoint.x, endPoint.y,
			endColor,
			tracerConfig.width,
			tracerConfig.getDashStyle()
		)
	}

	private fun RenderBuilder.box(boxes: List<Box>, ignoreSides: Int, fillColor: Color, lineColor: Color) {
		boxes.forEach { box ->
			box(box, outlineConfig) {
				hideSides(ignoreSides)
				if (fill) fillColor(fillColor) else hideFill()
				if (!outline) hideOutline()
				else {
					outlineColor(lineColor)
					outlineConfig.getDashStyle()?.let { lineDashStyle(it) }
					outlineMode(blockOutlineMode)
				}
			}
		}
	}

	private fun getTracerColors(naturalColor: Color): Pair<Color, Color> =
		if (useNaturalColor) {
			val adjustedNaturalColor = naturalColor.setAlpha(naturalTracerAlpha)
			Pair(adjustedNaturalColor, adjustedNaturalColor)
		} else Pair(tracerConfig.startColor, tracerConfig.endColor)

	private fun getEntityColor(entity: Entity) =
		entityColor(entity).ensureMinBrightness(minimumNaturalBrightness)

	private fun SafeContext.getBlockColor(state: BlockState, pos: BlockPos) =
		blockColor(state, pos).ensureMinBrightness(minimumNaturalBrightness)

	/**
	 * Ensures a color meets the minimum brightness threshold.
	 * If the color is too dark, scales up the RGB values proportionally.
	 */
	private fun Color.ensureMinBrightness(minBrightness: Int): Color {
		if (minBrightness <= 0) return this

		val brightness = maxOf(red, green, blue)
		if (brightness >= minBrightness) return this
		if (brightness == 0) return Color(minBrightness, minBrightness, minBrightness, alpha)

		val scale = minBrightness.toFloat() / brightness
		return Color(
			(red * scale).toInt().coerceIn(0, 255),
			(green * scale).toInt().coerceIn(0, 255),
			(blue * scale).toInt().coerceIn(0, 255),
			alpha
		)
	}

	private fun rebuildMesh(ctx: SafeContext, from: Any? = null, to: Any? = null): Unit = chunkedRenderer.rebuild()
}
