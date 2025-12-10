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
import com.lambda.config.settings.collections.CollectionSetting.Companion.onDeselect
import com.lambda.config.settings.collections.CollectionSetting.Companion.onSelect
import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.graphics.renderer.esp.ShapeBuilder
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.extension.blockColor
import com.lambda.util.extension.getBlockState
import com.lambda.util.world.toBlockPos
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BlockStateModel
import net.minecraft.util.math.BlockPos
import java.awt.Color

object BlockESP : Module(
    name = "BlockESP",
    description = "Render block ESP",
    tag = ModuleTag.RENDER,
) {
    private val searchBlocks by setting("Search Blocks", true, "Search for blocks around the player")
    private val blocks by setting("Blocks", setOf(Blocks.BEDROCK), description = "Render blocks") { searchBlocks }
        .onSelect { rebuildMesh(this, null, null) }
        .onDeselect { rebuildMesh(this, null, null) }

    private var drawFaces: Boolean by setting("Draw Faces", true, "Draw faces of blocks") { searchBlocks }.onValueChange(::rebuildMesh).onValueChange { _, to -> if (!to) drawOutlines = true }
    private var drawOutlines: Boolean by setting("Draw Outlines", true, "Draw outlines of blocks") { searchBlocks }.onValueChange(::rebuildMesh).onValueChange { _, to -> if (!to) drawFaces = true }
    private val mesh by setting("Mesh", true, "Connect similar adjacent blocks") { searchBlocks }.onValueChange(::rebuildMesh)

    private val useBlockColor by setting("Use Block Color", false, "Use the color of the block instead") { searchBlocks }.onValueChange(::rebuildMesh)
    private val faceColor by setting("Face Color", Color(100, 150, 255, 51), "Color of the surfaces") { searchBlocks && drawFaces && !useBlockColor }.onValueChange(::rebuildMesh)
    private val outlineColor by setting("Outline Color", Color(100, 150, 255, 128), "Color of the outlines") { searchBlocks && drawOutlines && !useBlockColor }.onValueChange(::rebuildMesh)

    private val outlineMode by setting("Outline Mode", DirectionMask.OutlineMode.And, "Outline mode") { searchBlocks }.onValueChange(::rebuildMesh)

    @JvmStatic
    val barrier by setting("Solid Barrier Block", true, "Render barrier blocks")

    // ToDo: I wanted to render this as a transparent / translucent block with a red tint.
    //  Like the red stained glass block without the texture sprite.
    //  Creating a custom baked model for this would be needed but seems really hard to do.
    //  mc.blockRenderManager.getModel(Blocks.RED_STAINED_GLASS.defaultState)
    @JvmStatic
    val model: BlockStateModel get() = mc.bakedModelManager.missingModel

    private val esp = newChunkedESP { world, position ->
        val state = world.getBlockState(position)
        if (state.block !in blocks) return@newChunkedESP

        val sides = if (mesh) {
            buildSideMesh(position) {
                world.getBlockState(it).block in blocks
            }
        } else DirectionMask.ALL

        build(state, position.toBlockPos(), sides)
    }

    private fun ShapeBuilder.build(
        state: BlockState,
        pos: BlockPos,
        sides: Int,
    ) = runSafe {
        val blockColor = blockColor(state, pos)

        if (drawFaces) filled(pos, state, if (useBlockColor) blockColor else faceColor, sides)
        if (drawOutlines) outline(pos, state, if (useBlockColor) blockColor else outlineColor, sides, outlineMode)
    }

    private fun rebuildMesh(ctx: SafeContext, from: Any?, to: Any?): Unit = esp.rebuild()
}
