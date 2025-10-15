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

package com.lambda.util

import com.lambda.Lambda.MOD_NAME
import com.lambda.Lambda.SYMBOL
import com.lambda.Lambda.VERSION
import com.lambda.Lambda.mc
import com.lambda.gui.components.ClickGuiLayout.lambdaTitleAppendixName
import net.minecraft.client.util.MacWindowUtil
import org.lwjgl.glfw.GLFW
import org.lwjgl.glfw.GLFWImage
import org.lwjgl.system.MemoryStack
import org.lwjgl.system.MemoryUtil
import java.awt.image.BufferedImage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import javax.imageio.ImageIO

object WindowUtils {
    /**
     * Updates the Lambda title for the application window.
     *
     * Constructs and sets the window title based on the current application symbol, name, version,
     * and additional dynamic details (e.g., username if `lambdaTitleAppendixName` is enabled).
     */
    @JvmStatic
    fun setLambdaTitle() {
        val name = if (lambdaTitleAppendixName) " - ${mc.session.username}" else ""
        mc.window.setTitle("$SYMBOL $MOD_NAME $VERSION - ${mc.windowTitle}$name")
    }

    /**
     * Sets the window icon for the application using a predefined set of icon sizes.
     *
     * This method constructs a list of image resource paths corresponding to different icon sizes
     * (16x16, 24x24, 32x32, 48x48, 64x64, 128x128, and 256x256) and applies them as the window icon.
     * The function utilizes the `setWindowIcon` function to handle the underlying platform-specific logic.
     */
    fun setLambdaWindowIcon() {
        val icons = listOf(16, 24, 32, 48, 64, 128, 256).map { "textures/icon/logo_$it.png" }
        setWindowIcon(*icons.toTypedArray())
    }

    /**
     * Sets the window icon using the provided list of image resource paths.
     * - On Windows/X11: uses glfwSetWindowIcon with all given sizes.
     * - On macOS: attempts to set the application Dock icon from the largest image (glfwSetWindowIcon is ignored).
     *
     * Paths are resolved via your readImage() extension (same as your texture system).
     *
     * Example:
     *   WindowIcons.setWindowIcon(
     *     "textures/icon16.png",
     *     "textures/icon32.png",
     *     "textures/icon48.png",
     *     "textures/icon128.png",
     *   )
     */
    @JvmStatic
    fun setWindowIcon(vararg iconPaths: String) {
        if (iconPaths.isEmpty()) return
        val windowHandle = mc.window?.handle ?: return

        val platform = GLFW.glfwGetPlatform()
        when (platform) {
            GLFW.GLFW_PLATFORM_WIN32,
            GLFW.GLFW_PLATFORM_X11,
            GLFW.GLFW_PLATFORM_WAYLAND,
            GLFW.GLFW_PLATFORM_NULL -> {
                val buffers = mutableListOf<ByteBuffer>()
                try {
                    MemoryStack.stackPush().use { stack ->
                        val images = GLFWImage.malloc(iconPaths.size, stack)

                        iconPaths.forEachIndexed { index, path ->
                            val img = path.readImage()
                            val pixels = toRGBA(img).also { buffers.add(it) }
                            images.position(index)
                            images.width(img.width)
                            images.height(img.height)
                            images.pixels(pixels)
                        }

                        GLFW.glfwSetWindowIcon(windowHandle, images.position(0))
                    }
                } finally {
                    buffers.forEach(MemoryUtil::memFree)
                }
            }

            GLFW.GLFW_PLATFORM_COCOA -> {
                val largest = iconPaths
                    .mapNotNull { runCatching { it.readImage() }.getOrNull() }
                    .maxByOrNull { it.width * it.height }
                    ?: return

                MacWindowUtil.setApplicationIconImage {
                    val baos = ByteArrayOutputStream()
                    ImageIO.write(largest, "PNG", baos)
                    ByteArrayInputStream(baos.toByteArray())
                }
            }

            else -> {
                // Unrecognized platform, ignore
            }
        }
    }

    private fun toRGBA(image: BufferedImage): ByteBuffer {
        val width = image.width
        val height = image.height
        val argb = IntArray(width * height)
        image.getRGB(0, 0, width, height, argb, 0, width)

        val buf = MemoryUtil.memAlloc(width * height * 4)
        for (p in argb) {
            val a = (p ushr 24) and 0xFF
            val r = (p ushr 16) and 0xFF
            val g = (p ushr 8) and 0xFF
            val b = p and 0xFF
            buf.put(r.toByte()).put(g.toByte()).put(b.toByte()).put(a.toByte())
        }
        buf.flip()
        return buf
    }
}