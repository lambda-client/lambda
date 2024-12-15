/*
 * Copyright 2024 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.graphics.texture

import com.lambda.graphics.buffer.pixel.PixelBuffer
import com.lambda.util.Communication.logError
import com.lambda.util.LambdaResource
import com.lambda.util.stream
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer
import kotlin.properties.Delegates


class AnimatedTexture(
    private val path: LambdaResource,
) : Texture(null) {
    lateinit var frameDurations: IntArray
    var width by Delegates.notNull<Int>()
    var height by Delegates.notNull<Int>()
    var channels by Delegates.notNull<Int>()
    var frames by Delegates.notNull<Int>()

    val blockSize: Int
        get() = width * height * channels

    lateinit var gif: ByteBuffer // Do NOT free this pointer
    var pbo: PixelBuffer

    var currentFrame = 0
    var lastUpload = 0L

    override fun bind(slot: Int) {
        update()
        super.bind(slot)
    }

    fun update() {
        if (System.currentTimeMillis() - lastUpload >= frameDurations[currentFrame]) {
            val slice = gif
                .position(blockSize * currentFrame)
                .limit(blockSize * (currentFrame + 1))

            pbo.upload(slice, offset = 0)
                ?.let { err -> logError("Error uploading to PBO", err) }

            gif.clear()

            currentFrame = (currentFrame+1) % frames
            lastUpload = System.currentTimeMillis()
        }
    }

    private fun readGif() {
        val bytes = path.stream.readAllBytes()
        val buffer = ByteBuffer.allocateDirect(bytes.size)

        buffer.put(bytes)
        buffer.flip()

        val pDelays = BufferUtils.createPointerBuffer(1)
        val pWidth = BufferUtils.createIntBuffer(1)
        val pHeight = BufferUtils.createIntBuffer(1)
        val pLayers = BufferUtils.createIntBuffer(1)
        val pChannels = BufferUtils.createIntBuffer(1)

        // The buffer contains packed frames that can be extracted as follows:
        // limit = width * height * channels * [frame number]
        gif = STBImage.stbi_load_gif_from_memory(buffer, pDelays, pWidth, pHeight, pLayers, pChannels, 4)!!

        width = pWidth.get()
        height = pHeight.get()
        frames = pLayers.get()
        channels = pChannels.get()

        frameDurations = IntArray(frames)
        pDelays.getIntBuffer(frames).get(frameDurations)
    }

    init {
        readGif()
        pbo = PixelBuffer(width, height, this@AnimatedTexture, format = GL_RGBA)
    }
}
