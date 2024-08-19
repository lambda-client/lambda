package com.lambda.module.hud

import com.lambda.graphics.renderer.gui.TextureRenderer.drawTexture
import com.lambda.graphics.renderer.gui.TextureRenderer.drawTextureShaded
import com.lambda.graphics.texture.MipmapTexture
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag

object Watermark : HudModule(
    name = "Watermark",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    private val shade by setting("Shade", true)

    override val width = 50.0
    override val height = 50.0

    private val normalTexture = MipmapTexture.fromResource("textures/lambda.png")
    private val monoTexture = MipmapTexture.fromResource("textures/lambda_mono.png")

    init {
        onRender {
            if (shade) drawTextureShaded(monoTexture, rect)
            else drawTexture(normalTexture, rect)
        }
    }
}