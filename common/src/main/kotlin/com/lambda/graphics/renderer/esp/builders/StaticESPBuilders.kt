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
import net.minecraft.util.math.Vec3d
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
) {
    buildFilled(box, color, color, sides)
}

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
) {
    buildOutline(box, color, color, sides, outlineMode)
}

fun StaticESPRenderer.buildFilled(
    box: Box,
    colorBottom: Color,
    colorTop: Color = colorBottom,
    sides: Int = DirectionMask.ALL
) = faceBuilder.use {
    val pos1 = box.min
    val pos2 = box.max

    val blb by lazy { vertex { vec3(pos1.x, pos1.y, pos1.z).color(colorBottom) } }
    val blf by lazy { vertex { vec3(pos1.x, pos1.y, pos2.z).color(colorBottom) } }
    val brb by lazy { vertex { vec3(pos2.x, pos1.y, pos1.z).color(colorBottom) } }
    val brf by lazy { vertex { vec3(pos2.x, pos1.y, pos2.z).color(colorBottom) } }

    val tlb by lazy { vertex { vec3(pos1.x, pos2.y, pos1.z).color(colorTop) } }
    val tlf by lazy { vertex { vec3(pos1.x, pos2.y, pos2.z).color(colorTop) } }
    val trb by lazy { vertex { vec3(pos2.x, pos2.y, pos1.z).color(colorTop) } }
    val trf by lazy { vertex { vec3(pos2.x, pos2.y, pos2.z).color(colorTop) } }

    if (sides.hasDirection(DirectionMask.EAST))  buildQuad(brb, trb, trf, brf)
    if (sides.hasDirection(DirectionMask.WEST))  buildQuad(blb, blf, tlf, tlb)
    if (sides.hasDirection(DirectionMask.UP))    buildQuad(tlb, tlf, trf, trb)
    if (sides.hasDirection(DirectionMask.DOWN))  buildQuad(blb, brb, brf, blf)
    if (sides.hasDirection(DirectionMask.SOUTH)) buildQuad(blf, brf, trf, tlf)
    if (sides.hasDirection(DirectionMask.NORTH)) buildQuad(blb, tlb, trb, brb)
}

fun StaticESPRenderer.buildOutline(
    box: Box,
    colorBottom: Color,
    colorTop: Color = colorBottom,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = outlineBuilder.use {
    val pos1 = box.min
    val pos2 = box.max

    val blb by lazy { vertex { vec3(pos1.x, pos1.y, pos1.z).color(colorBottom) } }
    val blf by lazy { vertex { vec3(pos1.x, pos1.y, pos2.z).color(colorBottom) } }
    val brb by lazy { vertex { vec3(pos2.x, pos1.y, pos1.z).color(colorBottom) } }
    val brf by lazy { vertex { vec3(pos2.x, pos1.y, pos2.z).color(colorBottom) } }
    val tlb by lazy { vertex { vec3(pos1.x, pos2.y, pos1.z).color(colorTop) } }
    val tlf by lazy { vertex { vec3(pos1.x, pos2.y, pos2.z).color(colorTop) } }
    val trb by lazy { vertex { vec3(pos2.x, pos2.y, pos1.z).color(colorTop) } }
    val trf by lazy { vertex { vec3(pos2.x, pos2.y, pos2.z).color(colorTop) } }

    val hasEast  = sides.hasDirection(DirectionMask.EAST)
    val hasWest  = sides.hasDirection(DirectionMask.WEST)
    val hasUp    = sides.hasDirection(DirectionMask.UP)
    val hasDown  = sides.hasDirection(DirectionMask.DOWN)
    val hasSouth = sides.hasDirection(DirectionMask.SOUTH)
    val hasNorth = sides.hasDirection(DirectionMask.NORTH)

    if (outlineMode.check(hasUp, hasNorth))   buildLine(tlb, trb)
    if (outlineMode.check(hasUp, hasSouth))   buildLine(tlf, trf)
    if (outlineMode.check(hasUp, hasWest))    buildLine(tlb, tlf)
    if (outlineMode.check(hasUp, hasEast))    buildLine(trf, trb)

    if (outlineMode.check(hasDown, hasNorth)) buildLine(blb, brb)
    if (outlineMode.check(hasDown, hasSouth)) buildLine(blf, brf)
    if (outlineMode.check(hasDown, hasWest))  buildLine(blb, blf)
    if (outlineMode.check(hasDown, hasEast))  buildLine(brb, brf)

    if (outlineMode.check(hasWest, hasNorth)) buildLine(tlb, blb)
    if (outlineMode.check(hasNorth, hasEast)) buildLine(trb, brb)
    if (outlineMode.check(hasEast, hasSouth)) buildLine(trf, brf)
    if (outlineMode.check(hasSouth, hasWest)) buildLine(tlf, blf)
}

fun StaticESPRenderer.buildLine(
    start: Vec3d,
    end: Vec3d,
    color: Color,
) = outlineBuilder.use {
    val v1 by lazy { vertex { vec3(start.x, start.y, start.z).color(color) } }
    val v2 by lazy { vertex { vec3(end.x, end.y, end.z).color(color) } }
    buildLine(v1, v2)
}
