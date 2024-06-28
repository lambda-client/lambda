package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.lambda.graphics.renderer.esp.ESPRenderer
import com.lambda.graphics.renderer.esp.global.buildFilled
import com.lambda.graphics.renderer.esp.global.buildOutline
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BakedModel
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
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

    private val esp = newChunkedESP { view, x, y, z ->
        val blockPos = BlockPos(x, y, z)
        val state = view.getBlockState(blockPos)
        if (state.block !in blocks) return@newChunkedESP

        val sides = if (mesh) {
            buildSideMesh(blockPos) {
                it.blockState(view).block in blocks
            }
        } else DirectionMask.ALL

        build(Box(blockPos), sides)
    }

    private fun ESPRenderer.build(
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