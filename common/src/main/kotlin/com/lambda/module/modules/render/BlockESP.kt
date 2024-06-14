package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.exclude
import com.lambda.graphics.renderer.esp.DirectionMask.mask
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.BlockUtils.blockState
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BakedModel
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction
import java.awt.Color

object BlockESP : Module(
    name = "BlockESP",
    description = "Render block ESP",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private var drawFaces: Boolean by setting("Draw Faces", true, "Draw faces of blocks").apply {
        onValueSet { _, to ->
            if (!to) drawOutlines = true
        }
    }
    val faceColor by setting("Face Color", Color(100, 150, 255, 128), "Color of the surfaces") {
        drawFaces
    }
    private var drawOutlines by setting("Draw Outlines", true, "Draw outlines of blocks").apply {
        onValueSet { _, to ->
            if (!to) drawFaces = true
        }
    }
    private val outlineColor by setting("Outline Color", Color(100, 150, 255), "Color of the outlines") {
        drawOutlines
    }
    private var clearRender: Boolean by setting("Clear Render", false, "Clear render after rendering blocks").apply {
        onValueSet { _, to ->
            if (to) {
                esp.clear()
                clearRender = false
            }
        }
    }
    private val blocks by setting("Blocks", setOf(Blocks.BEDROCK), "Render blocks")

    @JvmStatic
    val barrier by setting("Solid Barrier Block", true, "Render barrier blocks")

    // ToDo: I wanted to render this as a transparent / translucent block with a red tint.
    //  Like the red stained glass block without the texture sprite.
    //  Creating a custom baked model for this would be needed but seems really hard to do.
    //  mc.blockRenderManager.getModel(Blocks.RED_STAINED_GLASS.defaultState)
    @JvmStatic
    val model: BakedModel get() = mc.bakedModelManager.missingModel

    private val esp = newChunkedESP { view, x, y, z ->
        val blockPos = BlockPos(x, y, z)
        val state = view.getBlockState(blockPos)
        if (state.isAir) return@newChunkedESP
        if (state.block !in blocks) return@newChunkedESP

        val shape = state.getOutlineShape(view, blockPos)
        if (shape.isEmpty) return@newChunkedESP

        var sides = DirectionMask.ALL

        Direction.entries
            .filter { blockPos.offset(it).blockState(view).block in blocks }
            .forEach { sides = sides.exclude(it.mask) }

        shape.boundingBoxes.forEach { box ->
            val offsetBox = box.offset(blockPos)
            if (drawFaces) build(offsetBox, faceColor, outlineColor, sides, DirectionMask.OutlineMode.AND)
            if (drawOutlines) buildOutline(offsetBox, outlineColor, sides, DirectionMask.OutlineMode.AND)
        }
    }

    fun SafeContext.getBlock(x: Int, y: Int, z: Int): Block {
        val chunk = world.getChunk(x shr 4, z shr 4)
        val section = chunk.getSection(y shr 4)
        return section.getBlockState(x and 15, y and 15, z and 15).block
    }

    init {
        onToggle {
            if (barrier) mc.worldRenderer.reload()
        }
    }
}