/*
 * Copyright 2025 Lambda
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
import com.lambda.graphics.RenderMain
import com.lambda.graphics.mc.RenderBuilder
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedRenderer
import com.lambda.graphics.mc.renderer.ImmediateRenderer.Companion.immediateRenderer
import com.lambda.graphics.util.DirectionMask
import com.lambda.graphics.util.DirectionMask.buildSideMesh
import com.lambda.graphics.util.DynamicAABB.Companion.interpolatedBox
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.EntityUtils.decorationEntityMap
import com.lambda.util.EntityUtils.entityGroup
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.getBlockState
import com.lambda.util.world.toBlockPos
import io.ktor.util.collections.ConcurrentMap
import net.minecraft.block.Blocks
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import org.apache.commons.lang3.StringUtils.center
import java.awt.Color

object Search : Module(
    name = "Search",
    description = "Highlight blocks within the rendered world",
    tag = ModuleTag.RENDER,
) {
    private val blocks by setting("Blocks", setOf(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME, Blocks.END_GATEWAY), description = "Render blocks")
        .onSelect { rebuildMesh(this) }.onDeselect { rebuildMesh(this) }
    private val entities by setting("Entities", decorationEntityMap.values)
        .onSelect { rebuildMesh(this) }.onDeselect { rebuildMesh(this) }

    private var fill: Boolean by setting("Fill", true, "Fill the faces of blocks").onValueChange(::rebuildMesh).onValueChange { _, to -> if (!to) outline = true }
    private var outline: Boolean by setting("Outline", true, "Draw the outlines of blocks").onValueChange(::rebuildMesh).onValueChange { _, to -> if (!to) fill = true }
    private val tracers by setting("Tracers", true, "Draw a line from your cursor to the highlighted position")
    private val mesh by setting("Mesh", true, "Connect similar adjacent blocks").onValueChange(::rebuildMesh)

    private val useNaturalColor by setting("Use Natural Color", false, "Use the color of the block instead").onValueChange(::rebuildMesh)
    private val naturalColorAlpha by setting("Natural Color Alpha", 0.3, 0.1..1.0, 0.05) { useNaturalColor }.onValueChange(::rebuildMesh)

    private val blockFillColor by setting("Block Fill Color", Color(100, 150, 255, 51), "Color of the surfaces") { fill && !useNaturalColor }.onValueChange(::rebuildMesh)
    private val blockLineColor by setting("Block Line Color", Color(100, 150, 255, 128)) { outline && !useNaturalColor }.onValueChange(::rebuildMesh)
    private val entityFillColor by setting("Entity Fill Color", Color(100, 150, 255, 51)) { fill }
    private val entityOutlineColor by setting("Entity Outline Color", Color(100, 150, 255, 128)) { outline }

    private val blockOutlineMode by setting("Block Outline Mode", DirectionMask.OutlineMode.And, "Outline mode") { outline }.onValueChange(::rebuildMesh)
    private val worldLineConfig = WorldLineSettings("Outline ", this) { outline }.apply {
        applyEdits {
            hide(::startColor, ::endColor)
        }
    }
    private val screenLineConfig = ScreenLineSettings("Tracer ", this)

    private val tracerBlockPositions = ConcurrentMap<BlockPos, Vec3d>()

    val chunkedRenderer = chunkedRenderer("Chunked Search") { world, position ->
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

            val lineColor = blockColor(state, position.toBlockPos())
            val fillColor = Color(lineColor.red, lineColor.green, lineColor.blue, (naturalColorAlpha * 255).toInt())
            val shape = state.getOutlineShape(world, pos)
            val boxes = if (shape.isEmpty) listOf(Box(pos)) else shape.boundingBoxes.map { it.offset(pos) }
            if (tracers) tracerBlockPositions[pos] = shape.boundingBoxes.reduce(Box::union).offset(pos).center
	        box(boxes, sides.inv(), if (useNaturalColor) fillColor else blockFillColor, if (useNaturalColor) lineColor else blockLineColor)
        }
    }

    init {
        immediateRenderer("Immediate Search") { safeContext ->
            safeContext.world.entities.forEach { entity ->
                if (entity.entityGroup.nameToDisplayNameMap[entity::class.simpleName] in entities) {
                    box(listOf(entity.interpolatedBox), DirectionMask.NONE, entityFillColor, entityOutlineColor)
                    if (tracers) tracer(entity.interpolatedBox.center)
                }
            }
            if (tracers) tracerBlockPositions.values.forEach { tracer(it) }
        }

        listen<WorldEvent.ChunkEvent.Unload> { event ->
            if (tracers) tracerBlockPositions.keys.removeIf { it in event.chunk.pos }
        }
    }

    private fun RenderBuilder.tracer(pos: Vec3d) {
        val endPoint = RenderMain.worldToScreenNormalized(pos) ?: return
        screenLineGradient(0.5f, 0.5f, screenLineConfig.startColor, endPoint.x, endPoint.y, screenLineConfig.endColor, screenLineConfig.width, screenLineConfig.getDashStyle())
    }

    private fun RenderBuilder.box(boxes: List<Box>, ignoreSides: Int, fillColor: Color, lineColor: Color) {
        boxes.forEach { box ->
            box(box, worldLineConfig.width) {
                hideSides(ignoreSides)
                if (fill) fillColor(fillColor) else hideFill()
                if (!outline) hideOutline()
                else {
                    outlineColor(lineColor)
                    worldLineConfig.getDashStyle()?.let { lineDashStyle(it) }
                    outlineMode(this@Search.blockOutlineMode)
                }
            }
        }
    }

    private fun rebuildMesh(ctx: SafeContext, from: Any? = null, to: Any? = null): Unit = chunkedRenderer.rebuild()
}
