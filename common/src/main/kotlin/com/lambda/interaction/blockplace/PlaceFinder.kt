package com.lambda.interaction.blockplace

import com.lambda.context.SafeContext
import com.lambda.interaction.blockplace.PlaceInteraction.canPlaceAt
import com.lambda.interaction.blockplace.PlaceInteraction.isClickable
import com.lambda.interaction.visibilty.VisibilityChecker.getVisibleSurfaces
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.math.VecUtils.getHitVec
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import java.util.*
import kotlin.collections.Collection

class PlaceFinder(
    private val basePos: BlockPos,
    private val maxAttempts: Int,
    range: Double,
    private val visibleCheck: Boolean,
    private val sides: Set<Direction>
) {
    private val rangeSq = range * range

    private val Collection<PlaceInfo>.selectClosest get() = minByOrNull { it.eyeDistanceSq }
    private val Collection<PlaceInfo>.selectBest get() = this.let { infos ->
        if (infos.isEmpty()) return@let null
        val lowestStepAmount = infos.minOf { it.placeSteps }
        infos.filter { it.placeSteps == lowestStepAmount }.selectClosest
    }

    companion object {
        fun SafeContext.buildPlaceInfo(
            basePos: BlockPos,
            maxAttempts: Int = 4,
            range: Double = 3.25,
            visibleCheck: Boolean = true,
            sides: Set<Direction> = EnumSet.allOf(Direction::class.java)
        ) = PlaceFinder(basePos, maxAttempts, range, visibleCheck, sides).build(this)
    }

    private fun build(
        ctx: SafeContext,
        pos: BlockPos = basePos,
        attempts: Int = 0
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
        val eye = player.eyePos

        val clickPos = pos.offset(side)
        val clickSide = side.opposite

        val hitVec = clickPos.getHitVec(clickSide)
        val distSq = eye distSq hitVec

        if (distSq > rangeSq) return null
        if (clickPos.blockState(world).isClickable) return null

        if (visibleCheck) {
            val box = Box(clickPos)
            val visible = box.getVisibleSurfaces(eye)
            if (clickSide !in visible) return null
        }

        return PlaceInfo(clickPos, clickSide, pos, hitVec, distSq, attempts)
    }
}