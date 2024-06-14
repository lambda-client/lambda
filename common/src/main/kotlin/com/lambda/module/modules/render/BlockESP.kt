package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BakedModel
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

    @JvmStatic
    val barrier by setting("Solid Barrier Block", true, "Render barrier blocks")

    // ToDo: I wanted to render this as a transparent / translucent block with a red tint.
    //  Like the red stained glass block without the texture sprite.
    //  Creating a custom baked model for this would be needed but seems really hard to do.
    //  mc.blockRenderManager.getModel(Blocks.RED_STAINED_GLASS.defaultState)
    @JvmStatic
    val model: BakedModel get() = mc.bakedModelManager.missingModel

    private val esp = newChunkedESP(
        { view, pos -> view.getBlockState(pos).block == Blocks.BEDROCK },
        { _, _ -> faceColor to outlineColor }
    )

    init {
        onToggle {
            if (barrier) mc.worldRenderer.reload()
        }
    }
}