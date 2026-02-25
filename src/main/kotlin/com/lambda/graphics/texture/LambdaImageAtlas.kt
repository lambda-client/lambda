/*
 * Copyright 2026 Lambda
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

import com.lambda.Lambda.mc
import com.mojang.blaze3d.systems.RenderSystem
import com.mojang.blaze3d.textures.GpuTextureView
import net.minecraft.client.texture.AbstractTexture
import net.minecraft.client.texture.MissingSprite
import net.minecraft.client.texture.Sprite
import net.minecraft.client.texture.SpriteAtlasTexture
import net.minecraft.item.ItemStack
import net.minecraft.util.Identifier
import java.awt.image.BufferedImage
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentHashMap

object LambdaImageAtlas {
    data class ImageEntry(
        val textureView: GpuTextureView,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val width: Int,
        val height: Int
    ) {
        val aspectRatio: Float get() = if (height != 0) width.toFloat() / height.toFloat() else 1f

        val isFullTexture: Boolean get() = u0 == 0f && v0 == 0f && u1 == 1f && v1 == 1f
    }

    private val mcTextureCache = ConcurrentHashMap<Identifier, ImageEntry>()

    private var glintEntry: ImageEntry? = null

    private var missingEntry: ImageEntry? = null

    private val pendingLoadQueue = java.util.concurrent.ConcurrentLinkedQueue<Identifier>()

    fun loadMCTexture(id: Identifier): ImageEntry? {
        mcTextureCache[id]?.let { return it }

        if (!RenderSystem.isOnRenderThread()) {
            pendingLoadQueue.add(id)
            return null
        }

        processPendingLoads()
        
        return mcTextureCache.getOrPut(id) {
            val textureManager = mc.textureManager
            val texture: AbstractTexture = textureManager.getTexture(id) ?: return@getOrPut null
            
            val gpuTextureView = texture.glTextureView ?: return@getOrPut null

            ImageEntry(
                textureView = gpuTextureView,
                u0 = 0f, v0 = 0f,
                u1 = 1f, v1 = 1f,
                width = 256,
                height = 256
            )
        }
    }

    fun processPendingLoads() {
        if (!RenderSystem.isOnRenderThread()) return
        
        var id = pendingLoadQueue.poll()
        while (id != null) {
            val textureManager = mc.textureManager
            val texture: AbstractTexture? = textureManager.getTexture(id)
            
            if (texture != null) {
                val gpuTextureView = texture.glTextureView
                if (gpuTextureView != null) {
                    mcTextureCache.putIfAbsent(id, ImageEntry(
                        textureView = gpuTextureView,
                        u0 = 0f, v0 = 0f,
                        u1 = 1f, v1 = 1f,
                        width = 256,
                        height = 256
                    ))
                }
            }
            id = pendingLoadQueue.poll()
        }
    }

    fun getGlintTexture(): ImageEntry? {
        if (glintEntry != null) return glintEntry
        
        val id = Identifier.ofVanilla("textures/misc/enchanted_glint_item.png")
        glintEntry = loadMCTexture(id)
        return glintEntry
    }

    fun getMissingTexture(): ImageEntry? {
        if (missingEntry != null) return missingEntry
        
        val textureManager = mc.textureManager
        val atlas = textureManager.getTexture(Identifier.ofVanilla("textures/atlas/blocks.png")) 
            as? SpriteAtlasTexture ?: return null
        val sprite = atlas.getSprite(MissingSprite.getMissingSpriteId())
        
        missingEntry = createEntryFromSprite(sprite, atlas)
        return missingEntry
    }

    fun getItemSprite(stack: ItemStack): ImageEntry? {
        if (stack.isEmpty) return getMissingTexture()
        
        RenderSystem.assertOnRenderThread()

        val itemId = net.minecraft.registry.Registries.ITEM.getId(stack.item)
        return loadItemTexture(itemId) ?: getMissingTexture()
    }

    fun loadItemTexture(itemId: Identifier): ImageEntry? {
        RenderSystem.assertOnRenderThread()

        val itemTextureId = Identifier.of(itemId.namespace, "textures/item/${itemId.path}.png")
        val itemEntry = loadMCTexture(itemTextureId)
        if (itemEntry != null) return itemEntry

        val blockTextureId = Identifier.of(itemId.namespace, "textures/block/${itemId.path}.png")
        return loadMCTexture(blockTextureId)
    }

    fun hasGlint(stack: ItemStack): Boolean {
        return stack.hasGlint()
    }

    private fun createEntryFromSprite(sprite: Sprite, atlas: SpriteAtlasTexture): ImageEntry? {
        val gpuTextureView = atlas.glTextureView ?: return null
        
        return ImageEntry(
            textureView = gpuTextureView,
            u0 = sprite.minU,
            v0 = sprite.minV,
            u1 = sprite.maxU,
            v1 = sprite.maxV,
            width = sprite.contents.width,
            height = sprite.contents.height
        )
    }

    private val uploadedTextureCache = ConcurrentHashMap<String, UploadedTexture>()
    private var nextTextureId = 0

    data class UploadedTexture(
        val id: String,
        val texture: net.minecraft.client.texture.NativeImageBackedTexture,
        val entry: ImageEntry
    )

    fun upload(image: BufferedImage, name: String? = null): ImageEntry? {
        RenderSystem.assertOnRenderThread()
        
        val cacheKey = name ?: "uploaded_${nextTextureId++}"

        uploadedTextureCache[cacheKey]?.let { return it.entry }

        val nativeImage = net.minecraft.client.texture.NativeImage(image.width, image.height, true)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                val a = (argb shr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val abgr = (a shl 24) or (b shl 16) or (g shl 8) or r
                nativeImage.setColor(x, y, abgr)
            }
        }

        val nativeTexture = net.minecraft.client.texture.NativeImageBackedTexture({ cacheKey }, nativeImage)
        val textureId = Identifier.of("lambda", "uploaded/$cacheKey")

        mc.textureManager.registerTexture(textureId, nativeTexture)
        val gpuTextureView = nativeTexture.glTextureView ?: return null
        
        val entry = ImageEntry(
            textureView = gpuTextureView,
            u0 = 0f, v0 = 0f,
            u1 = 1f, v1 = 1f,
            width = image.width,
            height = image.height
        )
        
        uploadedTextureCache[cacheKey] = UploadedTexture(cacheKey, nativeTexture, entry)
        return entry
    }

    fun upload(buffer: ByteBuffer, width: Int, height: Int, name: String? = null): ImageEntry? {
        RenderSystem.assertOnRenderThread()
        
        val cacheKey = name ?: "uploaded_${nextTextureId++}"

        uploadedTextureCache[cacheKey]?.let { return it.entry }

        val nativeImage = net.minecraft.client.texture.NativeImage(width, height, true)
        buffer.rewind()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                val abgr = (a shl 24) or (b shl 16) or (g shl 8) or r
                nativeImage.setColor(x, y, abgr)
            }
        }

        val nativeTexture = net.minecraft.client.texture.NativeImageBackedTexture({ cacheKey }, nativeImage)
        val textureId = Identifier.of("lambda", "uploaded/$cacheKey")
        mc.textureManager.registerTexture(textureId, nativeTexture)
        val gpuTextureView = nativeTexture.glTextureView ?: return null
        
        val entry = ImageEntry(
            textureView = gpuTextureView,
            u0 = 0f, v0 = 0f,
            u1 = 1f, v1 = 1f,
            width = width,
            height = height
        )
        
        uploadedTextureCache[cacheKey] = UploadedTexture(cacheKey, nativeTexture, entry)
        return entry
    }

    fun removeUploaded(name: String) {
        uploadedTextureCache.remove(name)?.let { uploaded ->
            mc.textureManager.destroyTexture(Identifier.of("lambda", "uploaded/$name"))
        }
    }

    fun clearCache() {
        mcTextureCache.clear()
        glintEntry = null
        missingEntry = null
        
        // Clean up uploaded textures
        uploadedTextureCache.values.forEach { uploaded ->
            mc.textureManager.destroyTexture(Identifier.of("lambda", "uploaded/${uploaded.id}"))
        }
        uploadedTextureCache.clear()
    }
}

