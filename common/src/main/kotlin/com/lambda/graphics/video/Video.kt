package com.lambda.graphics.video

import com.lambda.Lambda.LOG
import com.lambda.graphics.buffer.pbo.PixelBuffer
import com.lambda.graphics.texture.Texture
import org.bytedeco.ffmpeg.avcodec.AVCodecContext
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer
import kotlin.random.Random

class Video(
    codecContext: AVCodecContext,
    private val iterator: Iterator<ByteBuffer>,
) : Texture() {
    val width = codecContext.width()
    val height = codecContext.height()
    private val frameTime = 1000 / codecContext.framerate().num()

    private val buffer: ByteBuffer
        get() = if (iterator.hasNext()) iterator.next() else ByteBuffer.allocate(0)

    private val pbo = PixelBuffer(width, height, channels = 3) {
        // Bind the texture
        glBindTexture(GL_TEXTURE_2D, id)

        // Tell OpenGL that we are using tightly packed data
        // If we don't do this, the alignment will truncate
        // to 16 bytes because we only have 24 bytes and computers
        // don't like this
        glPixelStorei(GL_UNPACK_ALIGNMENT, 1)

        // Allocate texture storage
        glTexImage2D(GL_TEXTURE_2D, 0, GL_RGB, width, height, 0, GL_RGB, GL_UNSIGNED_BYTE, 0)

        // Set the texture parameters
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)

        // Unbind the texture
        glBindTexture(GL_TEXTURE_2D, 0)
    }

    private var lastTime = System.currentTimeMillis()
    private var delta = 0L

    fun transfer() {
        with(pbo) {
            delta = System.currentTimeMillis() - lastTime

            if (delta > frameTime) {
                upload(buffer) { pbo ->
                    // Bind the texture and PBO
                    glBindTexture(GL_TEXTURE_2D, id)
                    glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo)

                    // Copy pixels from PBO to texture object
                    // Use offset instead of pointer
                    glTexSubImage2D(
                        GL_TEXTURE_2D,        // Target
                        0,                    // Mipmap level
                        0, 0,                 // x and y offset
                        width, height,        // width and height of the texture (set to your size)
                        GL_RGB,               // Format (depends on your data)
                        GL_UNSIGNED_BYTE,     // Type (depends on your data)
                        0                     // PBO offset (for asynchronous transfer)
                    )
                }?.let(LOG::error)

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
