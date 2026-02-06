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

package com.lambda.util.extension

import com.lambda.context.SafeContext
import com.lambda.util.world.FastVector
import com.lambda.util.world.toBlockPos
import com.lambda.util.world.x
import com.lambda.util.world.y
import com.lambda.util.world.z
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ColorHelper
import net.minecraft.util.shape.VoxelShape
import net.minecraft.world.World
import java.awt.Color
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

/**
 * Calculates the average color from a block's particle sprite texture.
 * Uses alpha-weighted averaging to ignore transparent pixels.
 * Applies block tints (for grass, leaves, water, etc.) using BlockColors.
 */
private fun SafeContext.calculateAverageTextureColor(state: BlockState): Color? {
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
