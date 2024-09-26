package com.lambda.graphics.video

import com.lambda.graphics.buffer.BufferUsage
import com.lambda.graphics.buffer.pbo.PixelBuffer
import com.lambda.graphics.texture.Texture
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.bytedeco.ffmpeg.avutil.AVFrame
import org.lwjgl.opengl.GL45C.*
import java.lang.Thread.sleep
import java.nio.ByteBuffer

class Video(
    codecContext: AVCodecContext,
    private val iterator: Iterator<AVFrame>,
) : Texture() {
    val width = codecContext.width()
    val height = codecContext.height()
    private val frameTime = 1 / codecContext.framerate().num()

    private val avFrame: AVFrame? get() = if (iterator.hasNext()) iterator.next() else null
    private val buffer: ByteBuffer? get() = avFrame?.asByteBuffer()

    private val pbo = PixelBuffer(width * height * 4L, buffers = 1, bufferUsage = BufferUsage.DYNAMIC) {
        glBindTexture(GL_TEXTURE_2D, id)

        // Allocate texture storage
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0)

        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
    }

    private val startTime = System.nanoTime()
    private var lastTime = System.nanoTime()
    private var delta = 0L

    fun transfer() {
        pbo.use {
            delta = System.nanoTime() - lastTime

            if (delta > frameTime) {
                upload(buffer ?: return@use) {
                    glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }

                lastTime = System.nanoTime()
            }
        }
    }

    companion object {
        /**
         * Retrieves a video from the resources folder.
         *
         * @param path The path to the image.
         */
        fun fromResource(path: String): Video {
            val ctx = AVUtils.createContext(path) ?:
                throw IllegalStateException("Could not create context from path: $path")

            val iterator = AVUtils.frameIterator(ctx) ?:
                throw IllegalStateException("Could not create frame iterator: $path")

            return Video(ctx.first, iterator)
        }
    }
}
