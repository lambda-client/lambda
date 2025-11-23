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

package com.lambda.graphics.renderer.esp

import com.lambda.graphics.pipeline.VertexBuilder
import com.lambda.graphics.renderer.esp.DirectionMask.hasDirection
import com.lambda.graphics.renderer.esp.Treed.Companion.cameraPos
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.extension.max
import com.lambda.util.extension.min
import com.lambda.util.extension.outlineShape
import com.lambda.util.math.minus
import net.minecraft.block.BlockState
import net.minecraft.block.entity.BlockEntity
import net.minecraft.entity.Entity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.shape.VoxelShape
import java.awt.Color

@DslMarker
annotation class ShapeDsl

class ShapeBuilder(
    val faces: VertexBuilder = VertexBuilder(),
    val edges: VertexBuilder = VertexBuilder(),
) {
    @ShapeDsl
    fun filled(
        box     : DynamicAABB,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
    ) = faces.apply {
        val boxes = box.pair ?: return@apply
        val camera = cameraPos

        val pos11 = boxes.first.min - camera
        val pos12 = boxes.first.max - camera
        val pos21 = boxes.second.min - camera
        val pos22 = boxes.second.max - camera

        val blb by lazy { vertex { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color) } }
        val blf by lazy { vertex { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color) } }
        val brb by lazy { vertex { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color) } }
        val brf by lazy { vertex { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color) } }
        val tlb by lazy { vertex { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color) } }
        val tlf by lazy { vertex { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color) } }
        val trb by lazy { vertex { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color) } }
        val trf by lazy { vertex { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color) } }

        if (sides.hasDirection(DirectionMask.EAST))     buildQuad(brb, trb, trf, brf)
        if (sides.hasDirection(DirectionMask.WEST))     buildQuad(blb, blf, tlf, tlb)
        if (sides.hasDirection(DirectionMask.UP))       buildQuad(tlb, tlf, trf, trb)
        if (sides.hasDirection(DirectionMask.DOWN))     buildQuad(blb, brb, brf, blf)
        if (sides.hasDirection(DirectionMask.SOUTH))    buildQuad(blf, brf, trf, tlf)
        if (sides.hasDirection(DirectionMask.NORTH))    buildQuad(blb, tlb, trb, brb)
    }

    @ShapeDsl
    fun filled(
        box         : Box,
        bottomColor : Color,
        topColor    : Color = bottomColor,
        sides       : Int = DirectionMask.ALL
    ) = faces.apply {
        val camera = cameraPos
        val pos1 = box.min - camera
        val pos2 = box.max - camera

        val blb by lazy { vertex { vec3(pos1.x, pos1.y, pos1.z).color(bottomColor) } }
        val blf by lazy { vertex { vec3(pos1.x, pos1.y, pos2.z).color(bottomColor) } }
        val brb by lazy { vertex { vec3(pos2.x, pos1.y, pos1.z).color(bottomColor) } }
        val brf by lazy { vertex { vec3(pos2.x, pos1.y, pos2.z).color(bottomColor) } }

        val tlb by lazy { vertex { vec3(pos1.x, pos2.y, pos1.z).color(topColor) } }
        val tlf by lazy { vertex { vec3(pos1.x, pos2.y, pos2.z).color(topColor) } }
        val trb by lazy { vertex { vec3(pos2.x, pos2.y, pos1.z).color(topColor) } }
        val trf by lazy { vertex { vec3(pos2.x, pos2.y, pos2.z).color(topColor) } }

        if (sides.hasDirection(DirectionMask.EAST))  buildQuad(brb, trb, trf, brf)
        if (sides.hasDirection(DirectionMask.WEST))  buildQuad(blb, blf, tlf, tlb)
        if (sides.hasDirection(DirectionMask.UP))    buildQuad(tlb, tlf, trf, trb)
        if (sides.hasDirection(DirectionMask.DOWN))  buildQuad(blb, brb, brf, blf)
        if (sides.hasDirection(DirectionMask.SOUTH)) buildQuad(blf, brf, trf, tlf)
        if (sides.hasDirection(DirectionMask.NORTH)) buildQuad(blb, tlb, trb, brb)
    }

    @ShapeDsl
    fun filled(
        pos     : BlockPos,
        state   : BlockState,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
    ) = runSafe { faces.apply {
        val shape = outlineShape(state, pos)
        if (shape.isEmpty) {
            filled(Box(pos), color, sides)
        } else {
            filled(shape, color, sides)
        }
    } }

    @ShapeDsl
    fun filled(
        pos     : BlockPos,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
    ) = runSafe { faces.apply { filled(pos, blockState(pos), color, sides) } }

    @ShapeDsl
    fun filled(
        pos     : BlockPos,
        entity  : BlockEntity,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
    ) = filled(pos, entity.cachedState, color, sides)

    @ShapeDsl
    fun filled(
        shape: VoxelShape,
        color: Color,
        sides: Int = DirectionMask.ALL,
    ) {
        shape.boundingBoxes
            .forEach { filled(it, color, color, sides) }
    }

    @ShapeDsl
    fun filled(
        box     : Box,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
    ) = filled(box, color, color, sides)

    @ShapeDsl
    fun outline(
        box     : DynamicAABB,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = edges.apply {
        val boxes = box.pair ?: return@apply
        val camera = cameraPos

        val pos11 = boxes.first.min - camera
        val pos12 = boxes.first.max - camera
        val pos21 = boxes.second.min - camera
        val pos22 = boxes.second.max - camera

        val blb by lazy { vertex { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color) } }
        val blf by lazy { vertex { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color) } }
        val brb by lazy { vertex { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color) } }
        val brf by lazy { vertex { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color) } }
        val tlb by lazy { vertex { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color) } }
        val tlf by lazy { vertex { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color) } }
        val trb by lazy { vertex { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color) } }
        val trf by lazy { vertex { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color) } }

        val hasEast     = sides.hasDirection(DirectionMask.EAST)
        val hasWest     = sides.hasDirection(DirectionMask.WEST)
        val hasUp       = sides.hasDirection(DirectionMask.UP)
        val hasDown     = sides.hasDirection(DirectionMask.DOWN)
        val hasSouth    = sides.hasDirection(DirectionMask.SOUTH)
        val hasNorth    = sides.hasDirection(DirectionMask.NORTH)

        if (mode.check(hasUp, hasNorth))     buildLine(tlb, trb)
        if (mode.check(hasUp, hasSouth))     buildLine(tlf, trf)
        if (mode.check(hasUp, hasWest))      buildLine(tlb, tlf)
        if (mode.check(hasUp, hasEast))      buildLine(trf, trb)

        if (mode.check(hasDown, hasNorth))   buildLine(blb, brb)
        if (mode.check(hasDown, hasSouth))   buildLine(blf, brf)
        if (mode.check(hasDown, hasWest))    buildLine(blb, blf)
        if (mode.check(hasDown, hasEast))    buildLine(brb, brf)

        if (mode.check(hasWest, hasNorth))   buildLine(tlb, blb)
        if (mode.check(hasNorth, hasEast))   buildLine(trb, brb)
        if (mode.check(hasEast, hasSouth))   buildLine(trf, brf)
        if (mode.check(hasSouth, hasWest))   buildLine(tlf, blf)
    }

    @ShapeDsl
    fun outline(
        box         : Box,
        bottomColor : Color,
        topColor    : Color = bottomColor,
        sides       : Int = DirectionMask.ALL,
        mode        : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = edges.apply {
        val camera = cameraPos
        val pos1 = box.min - camera
        val pos2 = box.max - camera

        val blb by lazy { vertex { vec3(pos1.x, pos1.y, pos1.z).color(bottomColor) } }
        val blf by lazy { vertex { vec3(pos1.x, pos1.y, pos2.z).color(bottomColor) } }
        val brb by lazy { vertex { vec3(pos2.x, pos1.y, pos1.z).color(bottomColor) } }
        val brf by lazy { vertex { vec3(pos2.x, pos1.y, pos2.z).color(bottomColor) } }
        val tlb by lazy { vertex { vec3(pos1.x, pos2.y, pos1.z).color(topColor) } }
        val tlf by lazy { vertex { vec3(pos1.x, pos2.y, pos2.z).color(topColor) } }
        val trb by lazy { vertex { vec3(pos2.x, pos2.y, pos1.z).color(topColor) } }
        val trf by lazy { vertex { vec3(pos2.x, pos2.y, pos2.z).color(topColor) } }

        val hasEast     = sides.hasDirection(DirectionMask.EAST)
        val hasWest     = sides.hasDirection(DirectionMask.WEST)
        val hasUp       = sides.hasDirection(DirectionMask.UP)
        val hasDown     = sides.hasDirection(DirectionMask.DOWN)
        val hasSouth    = sides.hasDirection(DirectionMask.SOUTH)
        val hasNorth    = sides.hasDirection(DirectionMask.NORTH)

        if (mode.check(hasUp, hasNorth)) buildLine(tlb, trb)
        if (mode.check(hasUp, hasSouth)) buildLine(tlf, trf)
        if (mode.check(hasUp, hasWest)) buildLine(tlb, tlf)
        if (mode.check(hasUp, hasEast)) buildLine(trf, trb)

        if (mode.check(hasDown, hasNorth)) buildLine(blb, brb)
        if (mode.check(hasDown, hasSouth)) buildLine(blf, brf)
        if (mode.check(hasDown, hasWest)) buildLine(blb, blf)
        if (mode.check(hasDown, hasEast)) buildLine(brb, brf)

        if (mode.check(hasWest, hasNorth)) buildLine(tlb, blb)
        if (mode.check(hasNorth, hasEast)) buildLine(trb, brb)
        if (mode.check(hasEast, hasSouth)) buildLine(trf, brf)
        if (mode.check(hasSouth, hasWest)) buildLine(tlf, blf)
    }

    @ShapeDsl
    fun outline(
        pos     : BlockPos,
        state   : BlockState,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe {
        val shape = outlineShape(state, pos)
        if (shape.isEmpty) {
            outline(Box(pos), color, sides, mode)
        } else {
            outline(shape, color, sides, mode)
        }
    }

    @ShapeDsl
    fun outline(
        pos     : BlockPos,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe { outline(pos, blockState(pos), color, sides, mode) }

    @ShapeDsl
    fun outline(
        pos     : BlockPos,
        entity  : BlockEntity,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe { outline(pos, entity.cachedState, color, sides, mode) }

    @ShapeDsl
    fun outline(
        shape   : VoxelShape,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) {
        shape.boundingBoxes
            .forEach { outline(it, color, sides, mode) }
    }

    @ShapeDsl
    fun outline(
        box     : Box,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) {
        outline(box, color, color, sides, mode)
    }

    @ShapeDsl
    fun box(
        pos     : BlockPos,
        state   : BlockState,
        filled  : Color,
        outline : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe {
        filled(pos, state, filled, sides)
        outline(pos, state, outline, sides, mode)
    }

    @ShapeDsl
    fun box(
        pos     : BlockPos,
        filled  : Color,
        outline : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe {
        filled(pos, filled, sides)
        outline(pos, outline, sides, mode)
    }

    @ShapeDsl
    fun box(
        box     : DynamicAABB,
        filled  : Color,
        outline : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) {
        filled(box, filled, sides)
        outline(box, outline, sides, mode)
    }

    @ShapeDsl
    fun box(
        box     : Box,
        filled  : Color,
        outline : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) {
        filled(box, filled, sides)
        outline(box, outline, sides, mode)
    }

    @ShapeDsl
    fun box(
        entity  : BlockEntity,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe {
        filled(entity.pos, entity, color, sides)
        outline(entity.pos, entity, color, sides, mode)
    }

    @ShapeDsl
    fun box(
        entity  : Entity,
        color   : Color,
        sides   : Int = DirectionMask.ALL,
        mode    : DirectionMask.OutlineMode = DirectionMask.OutlineMode.Or,
    ) = runSafe {
        filled(entity.boundingBox, color, sides)
        outline(entity.boundingBox, color, sides, mode)
    }
}
