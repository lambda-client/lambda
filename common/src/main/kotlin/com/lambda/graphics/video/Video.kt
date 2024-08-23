package com.lambda.graphics.video

import com.lambda.graphics.buffer.BufferUsage
import com.lambda.graphics.buffer.pbo.PixelBuffer
import com.lambda.graphics.texture.Texture
import com.lambda.graphics.video.JCodecUtils.toBufferedImage
import com.pngencoder.PngEncoder
import java.io.File
import java.nio.ByteBuffer
import javax.imageio.ImageIO


class Video(
    private val input: String,
) {
    private val decoderStream = JCodecUtils.demuxVideo(input)

    val width = decoderStream.mediaInfo.dim.width
    val height = decoderStream.mediaInfo.dim.height

    private val pbo = PixelBuffer(width, height, buffers = 2, bufferUsage = BufferUsage.STREAM)
    val texture = Texture()

    fun upload() {
        val picture = decoderStream.nativeFrame
        if (picture == null) {
            decoderStream.seekToFramePrecise(0)
            return
        }

        val image = toBufferedImage(picture)

        val bytes = PngEncoder()
            .withBufferedImage(image)
            .toBytes()

        val buffer =
            ByteBuffer.allocateDirect(bytes.size)
                .put(bytes)
                .flip()

        pbo.mapTexture(texture.id, buffer)
    }
}
