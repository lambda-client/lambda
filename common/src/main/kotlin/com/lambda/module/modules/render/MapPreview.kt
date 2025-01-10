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

package com.lambda.module.modules.render

import com.lambda.graphics.buffer.pixel.PixelBuffer
import com.lambda.graphics.texture.Texture
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.math.Vec2d
import net.minecraft.item.FilledMapItem
import net.minecraft.item.ItemStack
import org.lwjgl.BufferUtils
import org.lwjgl.opengl.GL11.GL_RGB
import org.lwjgl.opengl.GL11.GL_RGBA
import org.lwjgl.opengl.GL45C.GL_TEXTURE_2D
import org.lwjgl.opengl.GL45C.glBindTexture

object MapPreview : Module(
    name = "MapPreview",
    description = "Preview maps in your inventory",
    defaultTags = setOf(ModuleTag.RENDER)
) {
    private val scale by setting("Scale", 0.7, 0.1..1.0, 0.05)

    private val buffer = BufferUtils.createByteBuffer(128*128)
    private val texture = Texture(buffer, 128, 128, format = GL_RGB, levels = 1)

    @JvmStatic
    fun drawMap(stack: ItemStack, x: Int, y: Int) = runSafe {
        val state = FilledMapItem.getMapState(stack, world) ?: return@runSafe
        buffer.put(state.colors)
        buffer.flip()

        val base = Vec2d(x, y)

        texture.bind()
        texture.update(buffer, 128, 128)

        texture.draw(base, scale)
    }
}
