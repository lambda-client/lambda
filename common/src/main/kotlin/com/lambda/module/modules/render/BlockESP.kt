package com.lambda.module.modules.render

import com.lambda.Lambda.mc
import com.lambda.graphics.renderer.esp.ChunkedESP.Companion.newChunkedESP
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.ColorUtils.setAlpha
import net.minecraft.block.Blocks
import net.minecraft.client.render.model.BakedModel
import java.awt.Color

object BlockESP : Module(
    name = "BlockESP",
    description = "Render block ESP",
    defaultTags = setOf(ModuleTag.RENDER)
) {
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
            mc.worldRenderer.reload()
        }

        val outlineColor = Color(100, 150, 255).setAlpha(0.5)
        val filledColor = outlineColor.setAlpha(0.2)

        newChunkedESP(
            { view, pos -> view.getBlockState(pos).block.defaultState == Blocks.GRASS_BLOCK.defaultState },
            { _, _ -> filledColor to outlineColor }
        )
    }
}