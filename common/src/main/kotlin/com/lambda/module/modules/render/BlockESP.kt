/*
 * Copyright 2024 Lambda
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
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.graphics.renderer.esp.builders.buildFilled
import com.lambda.graphics.renderer.esp.builders.buildOutline
import com.lambda.graphics.renderer.esp.impl.StaticESPRenderer
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.extension.getBlockState
import com.lambda.util.world.fastVectorOf
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BakedModel
import net.minecraft.util.math.Box
import java.awt.Color

object BlockESP : Module(
    name = "BlockESP",
    description = "Render block ESP",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private var drawFaces: Boolean by setting("Draw Faces", true, "Draw faces of blocks").apply {
        onValueSet { _, to ->
            esp.rebuild()
            if (!to) drawOutlines = true
        }
    }

    private val faceColor: Color by setting("Face Color", Color(100, 150, 255, 51), "Color of the surfaces") {
        drawFaces
    }.apply {
        onValueSet { _, _ -> esp.rebuild() }
    }

    private var drawOutlines: Boolean by setting("Draw Outlines", true, "Draw outlines of blocks").apply {
        onValueSet { _, to ->
            esp.rebuild()
            if (!to) drawFaces = true
        }
    }

    private val outlineColor: Color by setting("Outline Color", Color(100, 150, 255, 128), "Color of the outlines") {
        drawOutlines
    }.apply {
        onValueSet { _, _ -> esp.rebuild() }
    }

    private val outlineMode: DirectionMask.OutlineMode by setting("Outline Mode", DirectionMask.OutlineMode.AND, "Outline mode").apply {
        onValueSet { _, _ -> esp.rebuild() }
    }

    private val mesh: Boolean by setting("Mesh", true, "Connect similar adjacent blocks").apply {
        onValueSet { _, _ -> esp.rebuild() }
    }

    private val blocks: Set<Block> by setting("Blocks", setOf(Blocks.BEDROCK), "Render blocks").apply {
        onValueSet { _, _ -> esp.rebuild() }
    }

    @JvmStatic
    val barrier by setting("Solid Barrier Block", true, "Render barrier blocks")

    // ToDo: I wanted to render this as a transparent / translucent block with a red tint.
    //  Like the red stained glass block without the texture sprite.
    //  Creating a custom baked model for this would be needed but seems really hard to do.
    //  mc.blockRenderManager.getModel(Blocks.RED_STAINED_GLASS.defaultState)
    @JvmStatic
    val model: BakedModel get() = mc.bakedModelManager.missingModel

    init {
        onToggle {
            if (barrier) mc.worldRenderer.reload()
        }
    }

    private val esp = newChunkedESP { world, x, y, z ->
        val position = fastVectorOf(x, y, z)
        val state = world.getBlockState(position)
        if (state.block !in blocks) return@newChunkedESP

        val sides = if (mesh) {
            buildSideMesh(position) {
                world.getBlockState(it).block in blocks
            }
        } else DirectionMask.ALL

        build(
            // big hack
            Box(x.toDouble(), y.toDouble(), z.toDouble(), x.toDouble() + 1, y.toDouble() + 1, z.toDouble() + 1),
            sides
        )
    }

    private fun StaticESPRenderer.build(
        box: Box,
        sides: Int,
    ) {
        if (drawFaces) {
            buildFilled(box, faceColor, sides)
        }
        if (drawOutlines) {
            buildOutline(box, outlineColor, sides, outlineMode)
        }
    }
}
