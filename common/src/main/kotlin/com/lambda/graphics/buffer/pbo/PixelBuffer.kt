package com.lambda.graphics.buffer.pbo

import com.lambda.graphics.buffer.BufferUsage
import com.lambda.graphics.texture.TextureUtils.setupTexture
import org.lwjgl.opengl.GL45C.*
import java.nio.ByteBuffer
import kotlin.system.measureNanoTime

class PixelBuffer(
    private val width: Int,
    private val height: Int,
    private val buffers: Int = 2,
    private val bufferUsage: BufferUsage = BufferUsage.DYNAMIC,
) {
    private val pboIds = IntArray(buffers)
    private var writeIdx = 0 // Used to copy pixels from the PBO to the texture
    private var uploadIdx = 0 // Used to upload data to the PBO

    fun mapTexture(id: Int, buffer: ByteBuffer) {
        val time = measureNanoTime {
            upload(buffer) { allocate ->
                // Bind the texture
                glBindTexture(GL_TEXTURE_2D, id)

                if (allocate) {
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
                    glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)

                    // Allocate the texture memory
                    glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA, width, height, 0, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }
                else {
                    // Update the texture
                    glTextureSubImage2D(GL_TEXTURE_2D, 0, 0, 0, width, height, GL_RGBA, GL_UNSIGNED_BYTE, 0)
                }
            }
        }

        println("PBO => texture $id took $time nanoseconds")
    }

    fun upload(data: ByteBuffer, process: (Boolean) -> Unit) {
        uploadIdx = (writeIdx + 1) % buffers

        // Bind the next PBO to update pixel values
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[uploadIdx])

        // Allocate the memory for the buffer
        process(true)

        // Map the buffer into the memory
        val bufferData = glMapBuffer(GL_PIXEL_UNPACK_BUFFER, GL_WRITE_ONLY)
        if (bufferData != null) {
            bufferData.put(data)

            // Release the buffer
            glUnmapBuffer(GL_PIXEL_UNPACK_BUFFER)
        } else {
            println("Failed to map the PBO")
        }

        // Bind the current PBO for writing
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[writeIdx])

        // Copy the pixel values from the PBO to the texture
        process(false)

        // Unbind the PBO
        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0)

        println("Uploaded data to PBO $uploadIdx at $bufferData")

        // Swap the indices
        writeIdx = uploadIdx
    }

    // Called when no references to the object exist
    fun finalize() {
        // Delete the PBOs
        glDeleteBuffers(pboIds)
    }

    init {
        if (buffers < 1) throw IllegalArgumentException("Buffers must be greater than or equal to 1")

        // Generate the PBOs
        glGenBuffers(pboIds)

        // Fill the buffers with null data to allocate the memory spaces
        repeat(buffers) {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pboIds[it])
            glBufferData(GL_PIXEL_UNPACK_BUFFER, width * height * 4L, GL_STREAM_DRAW)
        }

        glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0) // Unbind the buffer
    }
}
