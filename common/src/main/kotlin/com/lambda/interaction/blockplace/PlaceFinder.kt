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

package com.lambda.interaction.blockplace

import com.lambda.context.SafeContext
import com.lambda.interaction.blockplace.PlaceInteraction.canPlaceAt
import com.lambda.interaction.blockplace.PlaceInteraction.isClickable
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.distSq
import com.lambda.util.math.getHitVec
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*

class PlaceFinder(
    private val basePos: BlockPos,
    private val maxAttempts: Int,
    range: Double,
    private val eyes: Vec3d,
    private val visibleCheck: Boolean,
    private val sides: Set<Direction>,
) {
    private val rangeSq = range * range

    private val Collection<PlaceInfo>.selectClosest get() = minByOrNull { it.eyeDistanceSq }
    private val Collection<PlaceInfo>.selectBest
        get() = this.let { infos ->
            if (infos.isEmpty()) return@let null
            val lowestStepAmount = infos.minOf { it.placeSteps }
            infos.filter { it.placeSteps == lowestStepAmount }.selectClosest
        }

    companion object {
        fun SafeContext.buildPlaceInfo(
            basePos: BlockPos,
            maxAttempts: Int = 4,
            range: Double = 3.25,
            eyes: Vec3d = player.eyePos,
            visibleCheck: Boolean = true,
            sides: Set<Direction> = EnumSet.allOf(Direction::class.java),
        ) = PlaceFinder(basePos, maxAttempts, range, eyes, visibleCheck, sides).build(this)
    }

    private fun build(
        ctx: SafeContext,
        pos: BlockPos = basePos,
        attempts: Int = 0,
    ): PlaceInfo? = with(ctx) {
        if (sides.isEmpty()) return null
        if (!canPlaceAt(pos)) return null

        sides.mapNotNull { checkSide(pos, it, attempts) }.selectClosest?.let {
            return it
        }

        if (attempts > maxAttempts) return null

        return sides.mapNotNull { side ->
            build(this, pos.offset(side), attempts + 1)
        }.selectBest
    }

    private fun SafeContext.checkSide(pos: BlockPos, side: Direction, attempts: Int): PlaceInfo? {
        val clickPos = pos.offset(side)
        val clickSide = side.opposite

        val hitVec = clickPos.getHitVec(clickSide)
        val distSq = eyes distSq hitVec

        if (distSq > rangeSq) return null
        if (blockState(clickPos).isClickable) return null

        if (visibleCheck) {
            val box = Box(clickPos)
            val visible = box.getVisibleSurfaces(eyes)
            if (clickSide !in visible) return null
        }

        return PlaceInfo(clickPos, clickSide, pos, hitVec, distSq, attempts)
    }
}
