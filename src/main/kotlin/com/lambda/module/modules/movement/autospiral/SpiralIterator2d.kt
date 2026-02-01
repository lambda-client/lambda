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

package com.lambda.module.modules.movement.autospiral

import net.minecraft.util.math.BlockPos
import kotlin.math.floor
import kotlin.math.pow


/**
 * Spiral outwards from a central position in growing squares.
 * Every point has a constant distance to its previous and following position of 1. First point returned is the starting position.
 * Generates positions like this:
 * ```text
 * 16 15 14 13 12
 * 17  4  3  2 11
 * 18  5  0  1 10
 * 19  6  7  8  9
 * 20 21 22 23 24
 * (maxDistance = 2; points returned = 25)
 * ```
 * Copy and paste source: https://stackoverflow.com/questions/3706219/algorithm-for-iterating-over-an-outward-spiral-on-a-discrete-2d-grid-from-the-or
 *
 */
class SpiralIterator2d(maxDistance: Int) : MutableIterator<BlockPos?> {
    val maxDistance: Int
    val totalPoints: Int
    private var deltaX: Int
    private var deltaZ: Int
    private var segmentLength: Int
    private var currentX: Int
    private var currentZ: Int
    private var stepsInCurrentSegment: Int
    var pointsGenerated: Int

	init {
        this.maxDistance = maxDistance
        this.totalPoints = floor(((floor(maxDistance.toDouble()) - 0.5) * 2).pow(2.0)).toInt()
        this.deltaX = 1
        this.deltaZ = 0
        this.segmentLength = 1
        this.currentX = 0
        this.currentZ = 0
        this.stepsInCurrentSegment = 0
        this.pointsGenerated = 0
    }

    override fun next(): BlockPos? {
        if (this.pointsGenerated >= this.totalPoints) return null
        val output = BlockPos(this.currentX, 0, this.currentZ)
        this.currentX += this.deltaX
        this.currentZ += this.deltaZ
        this.stepsInCurrentSegment += 1
        if (this.stepsInCurrentSegment == this.segmentLength) {
            this.stepsInCurrentSegment = 0
            val buffer = this.deltaX
            this.deltaX = -this.deltaZ
            this.deltaZ = buffer
            if (this.deltaZ == 0) {
                this.segmentLength += 1
            }
        }
        this.pointsGenerated += 1
        return output
    }

    override fun hasNext(): Boolean {
        return this.pointsGenerated < this.totalPoints
    }

    override fun remove() {
        throw UnsupportedOperationException("remove")
    }
}