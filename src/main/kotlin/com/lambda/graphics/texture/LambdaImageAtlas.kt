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

/**
 * Central service for loading and caching textures as GPU resources for Lambda's
 * custom image rendering system.
 * 
 * Provides access to:
 * - Custom Lambda textures from resources
 * - Minecraft textures (e.g., enchantment glint)
 * - Item sprites from MC's atlas
 * 
 * All textures are returned as [ImageEntry] which contains GPU texture views,
 * UV coordinates, and dimensions for rendering.
 */
object LambdaImageAtlas {
    
    /**
     * Represents a renderable image entry with GPU resources and UV coordinates.
     * 
     * @property textureView The GPU texture view for binding
     * @property u0 Left UV coordinate (0-1)
     * @property v0 Top UV coordinate (0-1)
     * @property u1 Right UV coordinate (0-1)
     * @property v1 Bottom UV coordinate (0-1)
     * @property width Width in pixels (for aspect ratio calculations)
     * @property height Height in pixels
     */
    data class ImageEntry(
        val textureView: GpuTextureView,
        val u0: Float,
        val v0: Float,
        val u1: Float,
        val v1: Float,
        val width: Int,
        val height: Int
    ) {
        /** Aspect ratio (width / height) for maintaining proportions. */
        val aspectRatio: Float get() = if (height != 0) width.toFloat() / height.toFloat() else 1f
        
        /** Check if this is a full texture (UV spans 0-1). */
        val isFullTexture: Boolean get() = u0 == 0f && v0 == 0f && u1 == 1f && v1 == 1f
    }
    
    // Cache for MC textures loaded by identifier
    private val mcTextureCache = ConcurrentHashMap<Identifier, ImageEntry>()
    
    // Cached glint texture entry
    private var glintEntry: ImageEntry? = null
    
    // Cached missing texture entry
    private var missingEntry: ImageEntry? = null
    
    /**
     * Load a Minecraft texture by its identifier.
     * 
     * @param id The texture identifier (e.g., Identifier.ofVanilla("textures/misc/enchanted_glint_item.png"))
     * @return ImageEntry for the texture, or null if not found
     */
    // Queue for textures requested from background threads
    private val pendingLoadQueue = java.util.concurrent.ConcurrentLinkedQueue<Identifier>()
    
    /**
     * Load a Minecraft texture by its identifier.
     * 
     * Thread-safe: Can be called from any thread.
     * - If called from render thread: loads and caches immediately
     * - If called from background thread: returns cached value if available,
     *   otherwise queues for loading and returns null
     * 
     * @param id The texture identifier (e.g., Identifier.ofVanilla("textures/misc/enchanted_glint_item.png"))
     * @return ImageEntry for the texture, or null if not found/not yet loaded
     */
    fun loadMCTexture(id: Identifier): ImageEntry? {
        // Check cache first (thread-safe)
        mcTextureCache[id]?.let { return it }
        
        // If not on render thread, queue for loading and return null
        if (!RenderSystem.isOnRenderThread()) {
            pendingLoadQueue.add(id)
            return null
        }
        
        // Process any pending loads while we're on the render thread
        processPendingLoads()
        
        return mcTextureCache.getOrPut(id) {
            val textureManager = mc.textureManager
            val texture: AbstractTexture = textureManager.getTexture(id) ?: return@getOrPut null
            
            val gpuTextureView = texture.glTextureView ?: return@getOrPut null
            
            // For full textures, we use default dimensions since GpuTexture dimensions are private
            // The actual dimensions can be retrieved from the texture view's underlying texture
            ImageEntry(
                textureView = gpuTextureView,
                u0 = 0f, v0 = 0f,
                u1 = 1f, v1 = 1f,
                width = 256,  // Default size for standalone textures
                height = 256
            )
        }
    }
    
    /**
     * Process any pending texture load requests.
     * Must be called from the render thread.
     */
    fun processPendingLoads() {
        if (!RenderSystem.isOnRenderThread()) return
        
        var id = pendingLoadQueue.poll()
        while (id != null) {
            // Try to load the texture (will add to cache if successful)
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
    
    /**
     * Get the enchantment glint texture for overlay rendering.
     */
    fun getGlintTexture(): ImageEntry? {
        if (glintEntry != null) return glintEntry
        
        val id = Identifier.ofVanilla("textures/misc/enchanted_glint_item.png")
        glintEntry = loadMCTexture(id)
        return glintEntry
    }
    
    /**
     * Get the missing/error texture.
     */
    fun getMissingTexture(): ImageEntry? {
        if (missingEntry != null) return missingEntry
        
        val textureManager = mc.textureManager
        val atlas = textureManager.getTexture(Identifier.ofVanilla("textures/atlas/blocks.png")) 
            as? SpriteAtlasTexture ?: return null
        val sprite = atlas.getSprite(MissingSprite.getMissingSpriteId())
        
        missingEntry = createEntryFromSprite(sprite, atlas)
        return missingEntry
    }
    
    /**
     * Get the sprite for an ItemStack from Minecraft's item atlas.
     * 
     * This extracts the 2D item texture with proper UV coordinates
     * for rendering flat item representations.
     * 
     * @param stack The ItemStack to get the sprite for
     * @return ImageEntry with sprite UV coordinates, or missing texture if not found
     */
    fun getItemSprite(stack: ItemStack): ImageEntry? {
        if (stack.isEmpty) return getMissingTexture()
        
        RenderSystem.assertOnRenderThread()
        
        // Get the item's registry ID and use it to load the texture
        val itemId = net.minecraft.registry.Registries.ITEM.getId(stack.item)
        return loadItemTexture(itemId) ?: getMissingTexture()
    }
    
    /**
     * Load an item texture by trying common texture paths.
     * 
     * This is useful for simple items where you know the item ID.
     * For block items, it will try both item and block texture paths.
     * 
     * @param itemId The item's registry ID (e.g., Identifier.ofVanilla("diamond"))
     * @return ImageEntry for the texture, or null if not found
     */
    fun loadItemTexture(itemId: Identifier): ImageEntry? {
        RenderSystem.assertOnRenderThread()
        
        // Try item texture first (most common)
        val itemTextureId = Identifier.of(itemId.namespace, "textures/item/${itemId.path}.png")
        val itemEntry = loadMCTexture(itemTextureId)
        if (itemEntry != null) return itemEntry
        
        // Try block texture for block items
        val blockTextureId = Identifier.of(itemId.namespace, "textures/block/${itemId.path}.png")
        return loadMCTexture(blockTextureId)
    }
    
    /**
     * Check if an ItemStack has enchantment glint.
     */
    fun hasGlint(stack: ItemStack): Boolean {
        return stack.hasGlint()
    }
    
    /**
     * Create an ImageEntry from a Minecraft Sprite.
     */
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
    
    // ============================================================================
    // Custom Texture Upload
    // ============================================================================
    
    // Cache for uploaded custom textures
    private val uploadedTextureCache = ConcurrentHashMap<String, UploadedTexture>()
    private var nextTextureId = 0
    
    /**
     * Represents an uploaded custom texture with its GPU resources.
     */
    data class UploadedTexture(
        val id: String,
        val texture: net.minecraft.client.texture.NativeImageBackedTexture,
        val entry: ImageEntry
    )
    
    /**
     * Upload a BufferedImage as a texture for rendering.
     * The texture is cached and can be reused.
     * 
     * @param image The BufferedImage to upload
     * @param name Optional name for caching (if same name is used, returns cached version)
     * @return ImageEntry for the uploaded texture
     */
    fun upload(image: BufferedImage, name: String? = null): ImageEntry? {
        RenderSystem.assertOnRenderThread()
        
        val cacheKey = name ?: "uploaded_${nextTextureId++}"
        
        // Check cache first
        uploadedTextureCache[cacheKey]?.let { return it.entry }
        
        // Convert BufferedImage to NativeImage
        val nativeImage = net.minecraft.client.texture.NativeImage(image.width, image.height, true)
        for (y in 0 until image.height) {
            for (x in 0 until image.width) {
                val argb = image.getRGB(x, y)
                // Convert ARGB to ABGR (NativeImage uses ABGR)
                val a = (argb shr 24) and 0xFF
                val r = (argb shr 16) and 0xFF
                val g = (argb shr 8) and 0xFF
                val b = argb and 0xFF
                val abgr = (a shl 24) or (b shl 16) or (g shl 8) or r
                nativeImage.setColor(x, y, abgr)
            }
        }
        
        // Create texture and upload
        val nativeTexture = net.minecraft.client.texture.NativeImageBackedTexture({ cacheKey }, nativeImage)
        val textureId = Identifier.of("lambda", "uploaded/$cacheKey")
        
        // Get texture view via texture manager after registration
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
    
    /**
     * Upload a ByteBuffer as a texture for rendering.
     * The buffer should contain RGBA pixel data.
     * 
     * @param buffer The ByteBuffer containing RGBA pixel data
     * @param width Width of the image in pixels
     * @param height Height of the image in pixels
     * @param name Optional name for caching
     * @return ImageEntry for the uploaded texture
     */
    fun upload(buffer: ByteBuffer, width: Int, height: Int, name: String? = null): ImageEntry? {
        RenderSystem.assertOnRenderThread()
        
        val cacheKey = name ?: "uploaded_${nextTextureId++}"
        
        // Check cache first
        uploadedTextureCache[cacheKey]?.let { return it.entry }
        
        // Create NativeImage from ByteBuffer
        val nativeImage = net.minecraft.client.texture.NativeImage(width, height, true)
        buffer.rewind()
        for (y in 0 until height) {
            for (x in 0 until width) {
                val r = buffer.get().toInt() and 0xFF
                val g = buffer.get().toInt() and 0xFF
                val b = buffer.get().toInt() and 0xFF
                val a = buffer.get().toInt() and 0xFF
                // NativeImage uses ABGR
                val abgr = (a shl 24) or (b shl 16) or (g shl 8) or r
                nativeImage.setColor(x, y, abgr)
            }
        }
        
        // Create texture and upload
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
    
    /**
     * Remove an uploaded texture from cache and release GPU resources.
     * 
     * @param name The name used when uploading the texture
     */
    fun removeUploaded(name: String) {
        uploadedTextureCache.remove(name)?.let { uploaded ->
            mc.textureManager.destroyTexture(Identifier.of("lambda", "uploaded/$name"))
        }
    }
    
    /**
     * Clear all cached textures. Should be called on resource reload.
     */
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

