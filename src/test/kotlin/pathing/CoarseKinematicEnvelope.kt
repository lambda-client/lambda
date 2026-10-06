package pathing

import com.lambda.pathing.actions.CoarseMoveCosts
import kotlin.math.hypot

internal class CoarseKinematicEnvelope(
	private val maxHorizontalBlocksPerTick: Double,
	private val maxAscentBlocksPerTick: Double,
	private val maxDescentBlocksPerTick: Double,
	private val minimumTransitionTicks: Double = 1.0,
) {
	fun lowerBoundTicks(dx: Int, dy: Int, dz: Int): Double =
		lowerBoundTicks(hypot(dx.toDouble(), dz.toDouble()), dy)

	fun lowerBoundTicks(horizontalDistance: Double, dy: Int): Double {
		val horizontal = horizontalDistance / maxHorizontalBlocksPerTick
		val vertical = when {
			dy > 0 -> dy / maxAscentBlocksPerTick
			dy < 0 -> -dy / maxDescentBlocksPerTick
			else -> 0.0
		}
		return maxOf(minimumTransitionTicks, horizontal, vertical)
	}

	fun moveCosts(): CoarseMoveCosts = CoarseMoveCosts(
		cardinalWalk = lowerBoundTicks(1, 0, 0),
		diagonalWalk = lowerBoundTicks(1, 0, 1),
		stepUp = lowerBoundTicks(1, 1, 0),
		walkOff = { depth -> lowerBoundTicks(1, -depth, 0) },
		jumpCandidate = { distance, verticalOffset -> lowerBoundTicks(distance, verticalOffset) },
		drop = { span, depth -> lowerBoundTicks(span, -depth, 0) },
	)
}
