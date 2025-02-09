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

package com.lambda.graphics.renderer.esp.builders

import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.renderer.esp.impl.StaticESPRenderer
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.max
import com.lambda.util.extension.min
import net.minecraft.block.BlockState
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import java.awt.Color

fun StaticESPRenderer.ofShape(
    pos: BlockPos,
    filledColor: Color,
    outlineColor: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = runSafe {
    val shape = blockState(pos).getOutlineShape(world, pos)
    ofShape(pos, shape, filledColor, outlineColor, sides, outlineMode)
}

fun StaticESPRenderer.ofShape(
    pos: BlockPos,
    state: BlockState,
    filledColor: Color,
    outlineColor: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = runSafe {
    val shape = state.getOutlineShape(world, pos)
    ofShape(pos, shape, filledColor, outlineColor, sides, outlineMode)
}

fun StaticESPRenderer.ofShape(
    pos: BlockPos,
    shape: VoxelShape,
    filledColor: Color,
    outlineColor: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) {
    shape.boundingBoxes.forEach {
        ofBox(it.offset(pos), filledColor, outlineColor, sides, outlineMode)
    }
}

fun StaticESPRenderer.ofBox(
    box: Box,
    filledColor: Color,
    outlineColor: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) {
    buildFilled(box, filledColor, sides)
    buildOutline(box, outlineColor, sides, outlineMode)
}

fun StaticESPRenderer.buildFilledShape(
    pos: BlockPos,
    state: BlockState,
    color: Color,
    sides: Int = DirectionMask.ALL,
) = runSafe {
    val shape = state.getOutlineShape(world, pos)
    buildFilledShape(shape, color, sides)
}

fun StaticESPRenderer.buildFilledShape(
    pos: BlockPos,
    color: Color,
    sides: Int = DirectionMask.ALL,
) = runSafe {
    val shape = blockState(pos).getOutlineShape(world, pos)
    buildFilledShape(shape, color, sides)
}

fun StaticESPRenderer.buildFilledShape(
    shape: VoxelShape,
    color: Color,
    sides: Int = DirectionMask.ALL,
) {
    shape.boundingBoxes
        .forEach { buildFilled(it, color, sides) }
}


fun StaticESPRenderer.buildFilled(
    box: Box,
    color: Color,
    sides: Int = DirectionMask.ALL
) = buildFilled(box, color, color, sides)

fun StaticESPRenderer.buildOutlineShape(
    pos: BlockPos,
    state: BlockState,
    color: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = runSafe {
    val shape = state.getOutlineShape(world, pos)
    buildOutlineShape(shape, color, sides, outlineMode)
}

fun StaticESPRenderer.buildOutlineShape(
    pos: BlockPos,
    color: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = runSafe {
    val shape = blockState(pos).getOutlineShape(world, pos)
    buildOutlineShape(shape, color, sides, outlineMode)
}

fun StaticESPRenderer.buildOutlineShape(
    shape: VoxelShape,
    color: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) {
    shape.boundingBoxes
        .forEach { buildOutline(it, color, sides, outlineMode) }
}

fun StaticESPRenderer.buildOutline(
    box: Box,
    color: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = buildOutline(box, color, color, sides, outlineMode)

fun StaticESPRenderer.buildFilled(
    box: Box,
    colorBottom: Color,
    colorTop: Color = colorBottom,
    sides: Int = DirectionMask.ALL
) = faces.use {
    val pos1 = box.min
    val pos2 = box.max

    grow(8)

    val blb by vertex(faceVertices, pos1.x, pos1.y, pos1.z, colorBottom)
    val blf by vertex(faceVertices, pos1.x, pos1.y, pos2.z, colorBottom)
    val brb by vertex(faceVertices, pos2.x, pos1.y, pos1.z, colorBottom)
    val brf by vertex(faceVertices, pos2.x, pos1.y, pos2.z, colorBottom)
    val tlb by vertex(faceVertices, pos1.x, pos2.y, pos1.z, colorTop)
    val tlf by vertex(faceVertices, pos1.x, pos2.y, pos2.z, colorTop)
    val trb by vertex(faceVertices, pos2.x, pos2.y, pos1.z, colorTop)
    val trf by vertex(faceVertices, pos2.x, pos2.y, pos2.z, colorTop)

    if (sides.hasDirection(DirectionMask.EAST))  putQuad(brb, trb, trf, brf)
    if (sides.hasDirection(DirectionMask.WEST))  putQuad(blb, blf, tlf, tlb)
    if (sides.hasDirection(DirectionMask.UP))    putQuad(tlb, tlf, trf, trb)
    if (sides.hasDirection(DirectionMask.DOWN))  putQuad(blb, brb, brf, blf)
    if (sides.hasDirection(DirectionMask.SOUTH)) putQuad(blf, brf, trf, tlf)
    if (sides.hasDirection(DirectionMask.NORTH)) putQuad(blb, tlb, trb, brb)

    updateFaces = true
}

fun StaticESPRenderer.buildOutline(
    box: Box,
    colorBottom: Color,
    colorTop: Color = colorBottom,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = outlines.use {
    val pos1 = box.min
    val pos2 = box.max

    grow(8)

    val blb by vertex(outlineVertices, pos1.x, pos1.y, pos1.z, colorBottom)
    val blf by vertex(outlineVertices, pos1.x, pos1.y, pos2.z, colorBottom)
    val brb by vertex(outlineVertices, pos2.x, pos1.y, pos1.z, colorBottom)
    val brf by vertex(outlineVertices, pos2.x, pos1.y, pos2.z, colorBottom)
    val tlb by vertex(outlineVertices, pos1.x, pos2.y, pos1.z, colorTop)
    val tlf by vertex(outlineVertices, pos1.x, pos2.y, pos2.z, colorTop)
    val trb by vertex(outlineVertices, pos2.x, pos2.y, pos1.z, colorTop)
    val trf by vertex(outlineVertices, pos2.x, pos2.y, pos2.z, colorTop)

    val hasEast  = sides.hasDirection(DirectionMask.EAST)
    val hasWest  = sides.hasDirection(DirectionMask.WEST)
    val hasUp    = sides.hasDirection(DirectionMask.UP)
    val hasDown  = sides.hasDirection(DirectionMask.DOWN)
    val hasSouth = sides.hasDirection(DirectionMask.SOUTH)
    val hasNorth = sides.hasDirection(DirectionMask.NORTH)

    if (outlineMode.check(hasUp, hasNorth))   putLine(tlb, trb)
    if (outlineMode.check(hasUp, hasSouth))   putLine(tlf, trf)
    if (outlineMode.check(hasUp, hasWest))    putLine(tlb, tlf)
    if (outlineMode.check(hasUp, hasEast))    putLine(trf, trb)

    if (outlineMode.check(hasDown, hasNorth)) putLine(blb, brb)
    if (outlineMode.check(hasDown, hasSouth)) putLine(blf, brf)
    if (outlineMode.check(hasDown, hasWest))  putLine(blb, blf)
    if (outlineMode.check(hasDown, hasEast))  putLine(brb, brf)

    if (outlineMode.check(hasWest, hasNorth)) putLine(tlb, blb)
    if (outlineMode.check(hasNorth, hasEast)) putLine(trb, brb)
    if (outlineMode.check(hasEast, hasSouth)) putLine(trf, brf)
    if (outlineMode.check(hasSouth, hasWest)) putLine(tlf, blf)

    updateOutlines = true
}
