
package com.minato.util

import net.minecraft.util.math.BlockPos
import kotlin.math.floor
import kotlin.math.pow

/**
 * A collection of Block position iterator implementations for various purposes.
 */
object BlockPosIterators {
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
	 *
	 * @see <a href="https://stackoverflow.com/questions/3706219/algorithm-for-iterating-over-an-outward-spiral-on-a-discrete-2d-grid-from-the-or">StackOverflow: Algorithm for iterating over an outward spiral on a discrete 2d grid</a>
	 *
	 */
	class SpiralIterator2d(maxDistance: Int) : MutableIterator<BlockPos?> {
		val totalPoints: Int = floor(((floor(maxDistance.toDouble()) - 0.5) * 2).pow(2.0)).toInt()
		private var deltaX: Int = 1
		private var deltaZ: Int = 0
		private var segmentLength: Int = 1
		private var currentX: Int = 0
		private var currentZ: Int = 0
		private var stepsInCurrentSegment: Int = 0
		var pointsGenerated: Int = 0

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
}
