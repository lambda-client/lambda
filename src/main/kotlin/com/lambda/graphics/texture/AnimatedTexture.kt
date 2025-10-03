/*
 * Copyright 2025 Lambda
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

import com.lambda.util.LambdaResource
import com.lambda.util.stream
import org.lwjgl.BufferUtils
import org.lwjgl.stb.STBImage
import java.nio.ByteBuffer


class AnimatedTexture(path: LambdaResource) : Texture(image = null) {
    private val gif: ByteBuffer // Do NOT free this pointer
    private val frameDurations: IntArray // Array of frame duration milliseconds as ints
    val channels: Int
    val frames: Int

    private val blockSize: Int
        get() = width * height * channels

    private var currentFrame = 0
    private var lastUpload = 0L

    override fun bind(slot: Int) { update(); super.bind(slot) }

    fun update() {
        val now = System.currentTimeMillis()

        if (now - lastUpload >= frameDurations[currentFrame]) {
            // This is cool because instead of having a buffer for each frame we can
            // just move the frame's block on each update
            // 0 memory allocation and few cpu cycles
            val slice = gif
                .position(blockSize * currentFrame)
                .limit(blockSize * (currentFrame + 1))

            update(slice, width, height)
            gif.clear()

            currentFrame = (currentFrame + 1) % frames
            lastUpload = now
        }
    }

    init {
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
        gif = STBImage.stbi_load_gif_from_memory(buffer, pDelays, pWidth, pHeight, pLayers, pChannels, 4)
            ?: throw IllegalStateException("There was an unknown error while loading the gif file")

        initialized = true
        width = pWidth.get()
        height = pHeight.get()
        frames = pLayers.get()
        channels = pChannels.get()
        frameDurations = IntArray(frames)

        pDelays.getIntBuffer(frames).get(frameDurations)
    }
}
