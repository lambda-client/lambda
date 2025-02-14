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
import com.lambda.graphics.renderer.esp.DynamicAABB
import com.lambda.graphics.renderer.esp.impl.DynamicESPRenderer
import com.lambda.util.extension.max
import com.lambda.util.extension.min
import java.awt.Color

fun DynamicESPRenderer.ofBox(
    box: DynamicAABB,
    filledColor: Color,
    outlineColor: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) {
    buildFilled(box, filledColor, sides)
    buildOutline(box, outlineColor, sides, outlineMode)
}

fun DynamicESPRenderer.buildFilled(
    box: DynamicAABB,
    color: Color,
    sides: Int = DirectionMask.ALL
) = faces.use {
    val boxes = box.getBoxPair() ?: return@use

    val pos11 = boxes.first.min
    val pos12 = boxes.first.max
    val pos21 = boxes.second.min
    val pos22 = boxes.second.max

    grow(8)

    val blb by lazy { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color).end() }
    val blf by lazy { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color).end() }
    val brb by lazy { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color).end() }
    val brf by lazy { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color).end() }
    val tlb by lazy { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color).end() }
    val tlf by lazy { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color).end() }
    val trb by lazy { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color).end() }
    val trf by lazy { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color).end() }

    if (sides.hasDirection(DirectionMask.EAST))  putQuad(brb, trb, trf, brf)
    if (sides.hasDirection(DirectionMask.WEST))  putQuad(blb, blf, tlf, tlb)
    if (sides.hasDirection(DirectionMask.UP))    putQuad(tlb, tlf, trf, trb)
    if (sides.hasDirection(DirectionMask.DOWN))  putQuad(blb, brb, brf, blf)
    if (sides.hasDirection(DirectionMask.SOUTH)) putQuad(blf, brf, trf, tlf)
    if (sides.hasDirection(DirectionMask.NORTH)) putQuad(blb, tlb, trb, brb)
}

fun DynamicESPRenderer.buildOutline(
    box: DynamicAABB,
    color: Color,
    sides: Int = DirectionMask.ALL,
    outlineMode: DirectionMask.OutlineMode = DirectionMask.OutlineMode.OR
) = outlines.use {
    val boxes = box.getBoxPair() ?: return@use

    val pos11 = boxes.first.min
    val pos12 = boxes.first.max
    val pos21 = boxes.second.min
    val pos22 = boxes.second.max

    grow(8)

    val blb by lazy { vec3(pos11.x, pos11.y, pos11.z).vec3(pos21.x, pos21.y, pos21.z).color(color).end() }
    val blf by lazy { vec3(pos11.x, pos11.y, pos12.z).vec3(pos21.x, pos21.y, pos22.z).color(color).end() }
    val brb by lazy { vec3(pos12.x, pos11.y, pos11.z).vec3(pos22.x, pos21.y, pos21.z).color(color).end() }
    val brf by lazy { vec3(pos12.x, pos11.y, pos12.z).vec3(pos22.x, pos21.y, pos22.z).color(color).end() }
    val tlb by lazy { vec3(pos11.x, pos12.y, pos11.z).vec3(pos21.x, pos22.y, pos21.z).color(color).end() }
    val tlf by lazy { vec3(pos11.x, pos12.y, pos12.z).vec3(pos21.x, pos22.y, pos22.z).color(color).end() }
    val trb by lazy { vec3(pos12.x, pos12.y, pos11.z).vec3(pos22.x, pos22.y, pos21.z).color(color).end() }
    val trf by lazy { vec3(pos12.x, pos12.y, pos12.z).vec3(pos22.x, pos22.y, pos22.z).color(color).end() }

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
}
