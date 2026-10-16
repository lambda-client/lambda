package com.lambda.pathing.coarse

import com.lambda.pathing.actions.MotionTemplate
import com.lambda.pathing.core.planarLength
import kotlin.math.abs
import kotlin.math.hypot

internal class HeuristicCaps private constructor(
	private val axisTicksPerBlock: Double,
	private val diagonalTicksPerPair: Double,
	private val ascentTicksPerBlock: Double,
	private val descentTicksPerBlock: Double,
	private val straightTicksPerBlock: Double,
) {
	fun estimate(offsetX: Int, offsetY: Int, offsetZ: Int): Double {
		val dx = abs(offsetX)
		val dz = abs(offsetZ)
		val paired = minOf(dx, dz)
		val straight = maxOf(dx, dz) - paired

		val octile = paired * diagonalTicksPerPair + straight * axisTicksPerBlock
		val straightLine = planarLength(dx.toDouble(), dz.toDouble()) * straightTicksPerBlock
		val horizontal = maxOf(octile, straightLine)
		val vertical = when {
			offsetY > 0 -> offsetY * ascentTicksPerBlock
			offsetY < 0 -> -offsetY * descentTicksPerBlock
			else -> 0.0
		}
		return maxOf(horizontal, vertical)
	}

	companion object {

		fun of(templates: List<MotionTemplate>): HeuristicCaps {
			var axis = Double.POSITIVE_INFINITY
			var diagonal = Double.POSITIVE_INFINITY
			var ascent = Double.POSITIVE_INFINITY
			var descent = Double.POSITIVE_INFINITY
			var straight = Double.POSITIVE_INFINITY
			for (template in templates) {
				val reach = hypot(template.dx.toDouble(), template.dz.toDouble())
				if (reach > 0.0) straight = minOf(straight, template.minimumTicks / reach)
			}
			for (template in templates) {
				val dx = abs(template.dx)
				val dz = abs(template.dz)
				when {
					dx > 0 && dz == 0 -> axis = minOf(axis, template.minimumTicks / dx)
					dz > 0 && dx == 0 -> axis = minOf(axis, template.minimumTicks / dz)
					dx == dz && dx > 0 -> diagonal = minOf(diagonal, template.minimumTicks / dx)
				}
				if (template.dy > 0) ascent = minOf(ascent, template.minimumTicks / template.dy)
				if (template.dy < 0) descent = minOf(descent, template.minimumTicks / abs(template.dy))
			}

			axis = minOf(axis, diagonal)
			diagonal = minOf(diagonal, 2.0 * axis)

			for (template in templates) {
				val a = abs(template.dx)
				val b = abs(template.dz)
				if (a == 0 || b == 0 || a == b) continue
				val pairs = minOf(a, b)
				val straights = abs(a - b)
				val claimed = pairs * diagonal + straights * axis
				if (!claimed.isFinite() || claimed <= template.minimumTicks) continue

				val roomForDiagonal = (template.minimumTicks - straights * axis) / pairs
				if (roomForDiagonal >= axis) {
					diagonal = roomForDiagonal
				} else {
					val even = template.minimumTicks / (pairs + straights)
					axis = even
					diagonal = even
				}
			}

			return HeuristicCaps(axis.finiteOrZero(), diagonal.finiteOrZero(), ascent.finiteOrZero(), descent.finiteOrZero(), straight.finiteOrZero())
		}

		private fun Double.finiteOrZero() = if (isFinite()) this else 0.0
	}
}
