package com.lambda.module.hud

import com.lambda.graphics.renderer.gui.TextureRenderer.drawTexture
import com.lambda.graphics.video.Video
import com.lambda.module.HudModule

object VideoTest : HudModule(
    name = "VideoTest",
    defaultTags = setOf(),
) {
    override val width = 430.0
    override val height = 548.0

    private val video = Video("C:\\Users\\Kamigen\\Documents\\weed.mp4")

    init {
        onRender {
            //video.upload()
            //drawTexture(video.texture, rect)
        }
    }
}
