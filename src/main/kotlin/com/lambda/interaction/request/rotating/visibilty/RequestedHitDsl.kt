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

package com.lambda.interaction.request.rotating.visibilty

import net.minecraft.entity.LivingEntity
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@DslMarker
annotation class RequestedHitDsl

@RequestedHitDsl
fun blockHit(
    blockPos: BlockPos,
    sides: Set<Direction>,
    reach: Double
) = RequestedHit.Block(blockPos, sides, reach)

@RequestedHitDsl
fun blockHit(
    blockPos: BlockPos,
    side: Direction,
    reach: Double
) = RequestedHit.Block(blockPos, setOf(side), reach)

@RequestedHitDsl
fun entityHit(
    entity: LivingEntity,
    reach: Double
) = RequestedHit.Entity(entity, reach)