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

package com.lambda.util.extension

import com.lambda.Lambda.mc
import com.lambda.context.SafeContext
import com.lambda.util.extension.paintingColorCache
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.render.entity.EntityRenderer
import net.minecraft.client.render.model.BlockStateManagers
import net.minecraft.client.texture.NativeImage
import net.minecraft.client.texture.Sprite
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityType
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.entity.decoration.painting.PaintingEntity
import net.minecraft.entity.decoration.painting.PaintingVariant
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.util.Atlases
import net.minecraft.util.Identifier
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.World
import java.awt.Color
import java.lang.reflect.Modifier
import java.util.concurrent.ConcurrentHashMap

private val blockColorCache = ConcurrentHashMap<Block, Color>()

val SafeContext.worldName: String
    get() = when {
        mc.currentServerEntry != null -> "Multiplayer"; mc.isIntegratedServerRunning -> "Singleplayer"; else -> "Main Menu"
    }
val World?.isOverworld: Boolean get() = this?.registryKey == World.OVERWORLD
val World?.isNether: Boolean get() = this?.registryKey == World.NETHER
val World?.isEnd: Boolean get() = this?.registryKey == World.END
val World?.dimensionName: String
    get() = when {
        isOverworld -> "Overworld"
        isNether -> "Nether"
        isEnd -> "End"
        else -> "Unknown"
    }

fun SafeContext.collisionShape(state: BlockState, pos: BlockPos): VoxelShape =
    state.getCollisionShape(world, pos).offset(pos)

fun SafeContext.outlineShape(state: BlockState, pos: BlockPos) =
    state.getOutlineShape(world, pos).offset(pos)

/**
 * Gets the average color of a block from its texture.
 * Results are cached by block type for performance.
 * Falls back to map color if texture access fails.
 */
fun SafeContext.blockColor(state: BlockState, pos: BlockPos): Color {
    return blockColorCache.getOrPut(state.block) {
        calculateAverageTextureColor(state) ?: Color(state.getMapColor(world, pos).color, false)
    }
}

private val entityColorCache = ConcurrentHashMap<EntityType<*>, Color>()
private val itemFrameColorCache = ConcurrentHashMap<Int, Color>()
private val paintingColorCache = ConcurrentHashMap<PaintingVariant, Color>()

/**
 * Gets the average color of an entity from its texture.
 * Results are cached by entity type for performance.
 * Falls back to gray if texture access fails.
 */
fun entityColor(entity: Entity): Color {
    return when (entity) {
	    is ItemFrameEntity -> calculateItemFrameColor(entity) ?: Color.LIGHT_GRAY
	    is PaintingEntity -> paintingColorCache.getOrPut(entity.variant.value()) { calculatePaintingColor(entity) ?: Color.LIGHT_GRAY }
	    else -> entityColorCache.getOrPut(entity.type) {
		    calculateEntityTextureColor(entity) ?: Color(128, 128, 128)
	    }
    }
}

/**
 * Calculates the average color from an entity's texture.
 * Uses the entity renderer to get the texture identifier, then samples the texture.
 * Supports both living and non-living entities using reflection to find texture methods/fields.
 */
private fun calculateEntityTextureColor(entity: Entity): Color? {
    return try {
        val renderer = mc.entityRenderDispatcher.getRenderer(entity) ?: return null
        val textureId = getTextureFromRenderer(entity, renderer) ?: return null
        calculateAverageColorFromTexture(textureId)
    } catch (_: Exception) { null }
}

private fun getTextureFromRenderer(entity: Entity, renderer: EntityRenderer<*, *>): Identifier? {
    val rendererClass = renderer.javaClass

    val textureFromMethod = tryGetTextureFromMethod(entity, renderer, rendererClass)
    if (textureFromMethod != null) return textureFromMethod

    val textureFromField = tryGetTextureFromField(renderer, rendererClass)
    if (textureFromField != null) return textureFromField
    
    return null
}

/**
 * Tries to get texture by calling a getTexture(RenderState) method via reflection.
 */
private fun tryGetTextureFromMethod(entity: Entity, renderer: EntityRenderer<*, *>, rendererClass: Class<*>): Identifier? {
    return try {
        // Find getTexture method that takes a single parameter
        val getTextureMethod = rendererClass.methods.find { method ->
            method.name == "getTexture" &&
            method.parameterCount == 1 &&
            Identifier::class.java.isAssignableFrom(method.returnType)
        } ?: return null
        
        getTextureMethod.isAccessible = true
        
        // Create and update render state
        val createStateMethod = rendererClass.getMethod("createRenderState")
        val state = createStateMethod.invoke(renderer)
        
        // Try to update the state with entity data
        try {
            val updateStateMethod = rendererClass.methods.find {
                it.name == "updateRenderState" && it.parameterCount == 3
            }
            updateStateMethod?.invoke(renderer, entity, state, 0f)
        } catch (_: Exception) { /* State update is optional */ }
        
        getTextureMethod.invoke(renderer, state) as? Identifier
    } catch (_: Exception) { null }
}

/**
 * Tries to get texture from an Identifier-typed field via reflection.
 * Searches for common field names like 'texture', 'TEXTURE', etc.
 */
private fun tryGetTextureFromField(renderer: EntityRenderer<*, *>, rendererClass: Class<*>): Identifier? {
    return try {
        // Look through all fields in the class hierarchy for Identifier types
        var currentClass: Class<*>? = rendererClass
        while (currentClass != null && currentClass != EntityRenderer::class.java) {
            for (field in currentClass.declaredFields) {
                if (Identifier::class.java.isAssignableFrom(field.type)) {
                    field.isAccessible = true
                    val value = field.get(renderer)
                    if (value is Identifier) return value
                    
                    // Also try static fields
                    if (Modifier.isStatic(field.modifiers)) {
                        val staticValue = field.get(null)
                        if (staticValue is Identifier) return staticValue
                    }
                }
            }
            currentClass = currentClass.superclass
        }
        null
    } catch (_: Exception) { null }
}

private fun calculateItemFrameColor(entity: ItemFrameEntity): Color? {
    return try {
        val isGlow = entity.type == EntityType.GLOW_ITEM_FRAME
        val hasMap = entity.getMapId(entity.heldItemStack) != null
        val blockState = BlockStateManagers.getStateForItemFrame(isGlow, hasMap)
        val sprite = mc.blockRenderManager.models.getModelParticleSprite(blockState)
        calculateAverageColorFromSprite(sprite)
    } catch (_: Exception) { null }
}

private fun calculatePaintingColor(entity: PaintingEntity): Color? {
    return try {
        val variant = entity.variant.value()
        val paintingAtlas = mc.atlasManager.getAtlasTexture(Atlases.PAINTINGS)
        val sprite = paintingAtlas.getSprite(variant.assetId())
        calculateAverageColorFromSprite(sprite)
    } catch (_: Exception) { null }
}

private fun calculateAverageColorFromSprite(sprite: Sprite): Color? {
    return try {
        val contents = sprite.contents
        val image = contents.image

        val width = contents.width
        val height = contents.height

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var totalWeight = 0L

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getColorArgb(x, y)
                val alpha = ColorHelper.getAlpha(argb)

                if (alpha == 0) continue

                val r = ColorHelper.getRed(argb)
                val g = ColorHelper.getGreen(argb)
                val b = ColorHelper.getBlue(argb)

                totalR += r.toLong() * alpha
                totalG += g.toLong() * alpha
                totalB += b.toLong() * alpha
                totalWeight += alpha.toLong()
            }
        }

        if (totalWeight == 0L) return null

        val avgR = (totalR / totalWeight).toInt().coerceIn(0, 255)
        val avgG = (totalG / totalWeight).toInt().coerceIn(0, 255)
        val avgB = (totalB / totalWeight).toInt().coerceIn(0, 255)

        Color(avgR, avgG, avgB)
    } catch (_: Exception) { null }
}

/**
 * Calculates the average color from a texture identifier.
 */
private fun calculateAverageColorFromTexture(textureId: Identifier): Color? {
    return try {
        // Try to load the texture from the resource manager
        val resource = mc.resourceManager.getResource(textureId).orElse(null) ?: run {
            // If that fails, try constructing a texture path with .png extension
            val pngId = Identifier.of(textureId.namespace, textureId.path + ".png")
            mc.resourceManager.getResource(pngId).orElse(null)
        } ?: return null
        
        resource.inputStream.use { inputStream ->
            val image = NativeImage.read(inputStream)
            
            val width = image.width
            val height = image.height
            
            var totalR = 0L
            var totalG = 0L
            var totalB = 0L
            var totalWeight = 0L
            
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val abgr = image.getColorArgb(x, y)
                    val alpha = ColorHelper.getAlpha(abgr)
                    
                    if (alpha == 0) continue
                    
                    val r = ColorHelper.getRed(abgr)
                    val g = ColorHelper.getGreen(abgr)
                    val b = ColorHelper.getBlue(abgr)
                    
                    totalR += r.toLong() * alpha
                    totalG += g.toLong() * alpha
                    totalB += b.toLong() * alpha
                    totalWeight += alpha.toLong()
                }
            }
            
            image.close()
            
            if (totalWeight == 0L) return null
            
            val avgR = (totalR / totalWeight).toInt().coerceIn(0, 255)
            val avgG = (totalG / totalWeight).toInt().coerceIn(0, 255)
            val avgB = (totalB / totalWeight).toInt().coerceIn(0, 255)
            
            Color(avgR, avgG, avgB)
        }
    } catch (_: Exception) { null }
}

/**
 * Calculates the average color from a block's particle sprite texture.
 * Uses alpha-weighted averaging to ignore transparent pixels.
 * Applies block tints (for grass, leaves, water, etc.) using BlockColors.
 */
private fun calculateAverageTextureColor(state: BlockState): Color? {
    return try {
        val sprite = mc.blockRenderManager.models.getModelParticleSprite(state)
        val contents = sprite.contents
        val image = contents.image

        val width = contents.width
        val height = contents.height

        var totalR = 0L
        var totalG = 0L
        var totalB = 0L
        var totalWeight = 0L

        for (y in 0 until height) {
            for (x in 0 until width) {
                val argb = image.getColorArgb(x, y)
                val alpha = ColorHelper.getAlpha(argb)

                if (alpha == 0) continue

                val r = ColorHelper.getRed(argb)
                val g = ColorHelper.getGreen(argb)
                val b = ColorHelper.getBlue(argb)

                totalR += r.toLong() * alpha
                totalG += g.toLong() * alpha
                totalB += b.toLong() * alpha
                totalWeight += alpha.toLong()
            }
        }

        if (totalWeight == 0L) return null

        var avgR = (totalR / totalWeight).toInt().coerceIn(0, 255)
        var avgG = (totalG / totalWeight).toInt().coerceIn(0, 255)
        var avgB = (totalB / totalWeight).toInt().coerceIn(0, 255)

        val tint = mc.blockColors.getColor(state, null, null, 0)
        if (tint != -1) {
            val tintR = ColorHelper.getRed(tint)
            val tintG = ColorHelper.getGreen(tint)
            val tintB = ColorHelper.getBlue(tint)

            avgR = (avgR * tintR / 255).coerceIn(0, 255)
            avgG = (avgG * tintG / 255).coerceIn(0, 255)
            avgB = (avgB * tintB / 255).coerceIn(0, 255)
        }

        Color(avgR, avgG, avgB)
    } catch (_: Exception) { null }
}

fun World.getBlockState(x: Int, y: Int, z: Int): BlockState {
    if (isOutOfHeightLimit(y)) return Blocks.VOID_AIR.defaultState

    val chunk = getChunk(x shr 4, z shr 4)
    val sectionIndex = getSectionIndex(y)
    if (sectionIndex < 0) return Blocks.VOID_AIR.defaultState

    val section = chunk.getSection(sectionIndex)
    return section.getBlockState(x and 0xF, y and 0xF, z and 0xF)
}

fun World.getFluidState(x: Int, y: Int, z: Int): FluidState {
    if (isOutOfHeightLimit(y)) return Fluids.EMPTY.defaultState

    val chunk = getChunk(x shr 4, z shr 4)
    val sectionIndex = getSectionIndex(y)
    if (sectionIndex < 0) return Fluids.EMPTY.defaultState

    val section = chunk.getSection(sectionIndex)
    return section.getFluidState(x and 0xF, y and 0xF, z and 0xF)
}

fun World.getBlockState(vec: FastVector): BlockState = getBlockState(vec.x, vec.y, vec.z)
fun World.getBlockEntity(vec: FastVector) = getBlockEntity(vec.toBlockPos())
fun World.getFluidState(vec: FastVector): FluidState = getFluidState(vec.x, vec.y, vec.z)
