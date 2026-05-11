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
import com.lambda.config.groups.ScreenLineSettings
import com.lambda.config.groups.WorldLineSettings
import com.lambda.config.settings.collections.CollectionSetting.Companion.onDeselect
import com.lambda.config.settings.collections.CollectionSetting.Companion.onSelect
import com.lambda.context.SafeContext
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.mc.renderer.RendererUtils.worldToScreenNormalized
import com.lambda.graphics.util.DirectionMask
import com.lambda.graphics.util.DirectionMask.buildSideMesh
import com.lambda.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.EntityUtils.decorationEntityMap
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.NamedEnum
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.entityColor
import com.lambda.util.extension.getBlockState
import com.lambda.util.math.setAlpha
import com.lambda.util.world.toBlockPos
import io.ktor.util.collections.ConcurrentMap
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import java.awt.Color

object Search : Module(
    name = "Search",
    description = "Highlight blocks within the rendered world",
    tag = ModuleTag.RENDER,
) {
    private enum class Group(override val displayName: String) : NamedEnum {
        General("General"),
        Fill("Fill"),
        Outline("Outline"),
        Tracers("Tracers")
    }

    private val blocks by setting("Blocks", setOf(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY), description = "Render blocks").group(Group.General)
        .onSelect { rebuildMesh(this) }.onDeselect { rebuildMesh(this) }
    private val entities by setting("Entities", decorationEntityMap.values).group(Group.General)
        .onSelect { rebuildMesh(this) }.onDeselect { rebuildMesh(this) }

    private var fill: Boolean by setting("Fill", true, "Fill the faces of blocks").group(Group.Fill).onValueChange(::rebuildMesh)
        .onValueChange { _, to -> if (!to) outline = true }
    private var outline: Boolean by setting("Outline", true, "Draw the outlines of blocks").group(Group.Outline).onValueChange(::rebuildMesh)
        .onValueChange { _, to -> if (!to) fill = true }
    private val mesh by setting("Mesh", true, "Connect similar adjacent blocks").group(Group.General).onValueChange(::rebuildMesh)

    private val useNaturalColor by setting("Use Natural Color", true, "Use the color of the block instead").group(Group.General).onValueChange(::rebuildMesh)
    private val naturalColorAlpha by setting("Natural Color Alpha", 0.3, 0.1..1.0, 0.05) { useNaturalColor }.group(Group.General).onValueChange(::rebuildMesh)
    private val naturalTracerAlpha by setting("Natural Tracer Alpha", 1.0, 0.1..1.0, 0.05) { useNaturalColor }.group(Group.General).onValueChange(::rebuildMesh)
    private val minimumNaturalBrightness by setting("Min Brightness", 150, 0..255, 1) { useNaturalColor }.group(Group.General).onValueChange(::rebuildMesh)

    private val blockFillColor by setting("Block Fill Color", Color(100, 150, 255, 51), "Color of the surfaces") { fill && !useNaturalColor }.group(Group.Fill).onValueChange(::rebuildMesh)
    private val blockLineColor by setting("Block Line Color", Color(100, 150, 255, 128)) { outline && !useNaturalColor }.group(Group.Outline).onValueChange(::rebuildMesh)
    private val entityFillColor by setting("Entity Fill Color", Color(100, 150, 255, 51)) { fill && !useNaturalColor }.group(Group.Fill).onValueChange(::rebuildMesh)
    private val entityOutlineColor by setting("Entity Outline Color", Color(100, 150, 255, 128)) { outline && !useNaturalColor }.group(Group.Outline).onValueChange(::rebuildMesh)

    private val blockOutlineMode by setting("Block Outline Mode", DirectionMask.OutlineMode.And, "Outline mode") { outline }.group(Group.Outline, WorldLineSettings.Group.General).onValueChange(::rebuildMesh)
    private val outlineConfig = WorldLineSettings(this, Group.Outline, prefix = "Outline ") { outline }.apply {
        applyEdits {
            hide(::startColor, ::endColor)
            settings.forEach { it.onValueChange(::rebuildMesh) }
        }
    }
    private val tracers by setting("Tracers", true, "Draw a line from your cursor to the highlighted position").group(Group.Tracers)
    private val tracerConfig = ScreenLineSettings(this, Group.Tracers, prefix = "Tracer ") { tracers }.apply {
        applyEdits {
            editTyped(::startColor, ::endColor) {
                visibility { { !useNaturalColor } }
            }
        }
    }

    private val tracerBlockPositions = ConcurrentMap<BlockPos, Pair<Vec3d, Pair<Color, Color>>>()

    val chunkedRenderer = chunkedRenderer("Search Chunked Renderer") { world, position ->
        runSafe {
            val pos = position.toBlockPos()
            val state = world.getBlockState(pos)
            if (state.block !in blocks) {
                tracerBlockPositions.remove(pos)
                return@chunkedRenderer
            }

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
                val center =
                    shape
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
        immediateRenderer("Search Immediate Renderer") { safeContext ->
            safeContext.world.entities.forEach { entity ->
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
            if (tracers) tracerBlockPositions.values.forEach { tracer(it) }
        }

        listen<WorldEvent.ChunkEvent.Unload> { event ->
            if (tracers) tracerBlockPositions.keys.removeIf { it in event.chunk.pos }
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
