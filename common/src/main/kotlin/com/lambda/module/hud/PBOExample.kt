package com.lambda.module.hud

import com.lambda.graphics.renderer.gui.TextureRenderer.drawTexture
import com.lambda.graphics.video.Video
import com.lambda.module.HudModule
import com.lambda.module.tag.ModuleTag

object PBOExample : HudModule(
    name = "PBOExample",
    description = "Test the pbo impl",
    defaultTags = setOf(ModuleTag.CLIENT),
) {
    override val width: Double
        get() = video.width.toDouble()
    override val height: Double
        get() = video.height.toDouble()

    private val video = Video.fromResource("C:\\Users\\Kamigen\\Downloads\\pizza.mp4")

    init {
        onRender {
            video.transfer()

            drawTexture(video, rect)
        }
    }
}
