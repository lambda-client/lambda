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

import com.lambda.Lambda.mc
import com.lambda.config.applyEdits
import com.lambda.config.groups.WorldLineSettings
import com.lambda.config.settings.collections.CollectionSetting.Companion.onDeselect
import com.lambda.config.settings.collections.CollectionSetting.Companion.onSelect
import com.lambda.context.SafeContext
import com.lambda.graphics.mc.renderer.ChunkedRenderer.Companion.chunkedEsp
import com.lambda.graphics.util.DirectionMask
import com.lambda.graphics.util.DirectionMask.buildSideMesh
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.NamedEnum
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.getBlockState
import com.lambda.util.world.toBlockPos
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BlockStateModel
import net.minecraft.util.math.Box
import java.awt.Color

object Search : Module(
    name = "Search",
    description = "Highlight blocks within the rendered world",
    tag = ModuleTag.RENDER,
) {
    private val blocks by setting("Blocks", setOf(Blocks.CHEST, Blocks.ENDER_CHEST, Blocks.NETHER_PORTAL, Blocks.END_PORTAL, Blocks.END_PORTAL_FRAME), description = "Render blocks")
        .onSelect { rebuildMesh(this) }
        .onDeselect { rebuildMesh(this) }

    private var drawFaces: Boolean by setting("Draw Faces", true, "Draw faces of blocks").onValueChange(::rebuildMesh).onValueChange { _, to -> if (!to) drawOutlines = true }
    private var drawOutlines: Boolean by setting("Draw Outlines", true, "Draw outlines of blocks").onValueChange(::rebuildMesh).onValueChange { _, to -> if (!to) drawFaces = true }
    private val mesh by setting("Mesh", true, "Connect similar adjacent blocks").onValueChange(::rebuildMesh)

    private val useBlockColor by setting("Use Block Color", false, "Use the color of the block instead").onValueChange(::rebuildMesh)
    private val blockColorAlpha by setting("Block Color Alpha", 0.3, 0.1..1.0, 0.05) { useBlockColor }.onValueChange(::rebuildMesh)

    private val faceColor by setting("Face Color", Color(100, 150, 255, 51), "Color of the surfaces") { drawFaces && !useBlockColor }.onValueChange(::rebuildMesh)
    private val lineColor by setting("Line Color", Color(100, 150, 255, 128)) { !useBlockColor }.onValueChange(::rebuildMesh)
    private val outlineMode by setting("Outline Mode", DirectionMask.OutlineMode.And, "Outline mode").onValueChange(::rebuildMesh)
    private val lineConfig = WorldLineSettings("", this).apply {
        applyEdits {
            hide(::startColor, ::endColor)
        }
    }

    @JvmStatic
    val barrier by setting("Solid Barrier Block", true, "Render barrier blocks")

    // ToDo: I wanted to render this as a transparent / translucent block with a red tint.
    //  Like the red stained glass block without the texture sprite.
    //  Creating a custom baked model for this would be needed but seems really hard to do.
    //  mc.blockRenderManager.getModel(Blocks.RED_STAINED_GLASS.defaultState)
    @JvmStatic
    val model: BlockStateModel get() = mc.bakedModelManager.missingModel

    private val esp = chunkedEsp("BlockESP") { world, position ->
        val state = world.getBlockState(position)
        if (state.block !in blocks) return@chunkedEsp

        val sides = if (mesh) {
            buildSideMesh(position) {
                world.getBlockState(it).block in blocks
            }
        } else DirectionMask.ALL

        runSafe {
            // TODO: Add custom color option when map options are implemented
            val extractedColor = blockColor(state, position.toBlockPos())
            val finalColor = Color(extractedColor.red, extractedColor.green, extractedColor.blue, (blockColorAlpha * 255).toInt())
            val pos = position.toBlockPos()
            val shape = state.getOutlineShape(world, pos)
            val worldBox = if (shape.isEmpty) Box(pos) else shape.boundingBox.offset(pos)
            box(worldBox, lineConfig.width) {
                val hiddenSides = sides.inv()
                hideSides(hiddenSides)
                if (drawFaces) fillColor(if (useBlockColor) finalColor else faceColor) else hideFill()
                if (!drawOutlines) hideOutline()
                else {
                    outlineColor(if (useBlockColor) extractedColor else lineColor)
                    lineConfig.getDashStyle()?.let { lineDashStyle(it) }
                    outlineMode(this@Search.outlineMode)
                }
            }
        }
    }

    init {
        onEnable { esp.rebuildAll() }
        onDisable { esp.close() }
    }

    private fun rebuildMesh(ctx: SafeContext, from: Any? = null, to: Any? = null): Unit = esp.rebuild()
}
