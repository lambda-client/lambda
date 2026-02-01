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
    private val maxDistance: Int
    private val NUMBER_OF_POINTS: Int
    private var di: Int
    private var dj: Int
    private var segment_length: Int
    private var i: Int
    private var j: Int
    private var segment_passed: Int
    private var k: Int

    init {
        this.maxDistance = maxDistance
        this.NUMBER_OF_POINTS = floor(((floor(maxDistance.toDouble()) - 0.5) * 2).pow(2.0)).toInt()
        this.di = 1
        this.dj = 0
        this.segment_length = 1
        this.i = 0
        this.j = 0
        this.segment_passed = 0
        this.k = 0
    }

    override fun next(): BlockPos? {
        if (this.k >= this.NUMBER_OF_POINTS) return null
        val output = BlockPos(this.i, 0, this.j)
        this.i += this.di
        this.j += this.dj
        this.segment_passed += 1
        if (this.segment_passed == this.segment_length) {
            this.segment_passed = 0
            val buffer = this.di
            this.di = -this.dj
            this.dj = buffer
            if (this.dj == 0) {
                this.segment_length += 1
            }
        }
        this.k += 1
        return output
    }

    override fun hasNext(): Boolean {
        return this.k < this.NUMBER_OF_POINTS
    }

    override fun remove() {
        throw UnsupportedOperationException("remove")
    }
}