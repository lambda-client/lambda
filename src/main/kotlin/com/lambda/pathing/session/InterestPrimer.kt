package com.lambda.pathing.session

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.streaming.InterestTier
import com.lambda.pathing.world.streaming.PathingWorld
import kotlin.math.abs
import net.minecraft.util.math.BlockPos

internal object InterestPrimer {
	fun primeBody(world: PathingWorld, pos: BlockPos) {
		world.interestAround(pos.x, pos.y, pos.z, BODY_INTEREST_BLOCKS, BODY_INTEREST_Y_BLOCKS, InterestTier.BODY)
	}

	fun primeRoute(world: PathingWorld, route: CoarseRoutePlan) {
		route.nodes.forEach { node ->
			world.interestAround(node.x, node.y, node.z, ROUTE_INTEREST_BLOCKS, ROUTE_INTEREST_Y_BLOCKS, InterestTier.CORRIDOR)
		}
	}

	fun primeJourney(world: PathingWorld, start: Stance, goal: Stance, waypoints: List<Stance>) {
		var from = start
		for (waypoint in waypoints) {
			primeJourney(world, from, waypoint)
			from = waypoint
		}
		primeJourney(world, from, goal)
	}

	fun primeJourney(world: PathingWorld, start: Stance, goal: Stance) {
		val below = (goal.y - start.y).coerceAtLeast(0)
		val above = (start.y - goal.y).coerceAtLeast(0)
		world.interestBlocks(
			goal.x - GOAL_INTEREST_BLOCKS, goal.y - GOAL_INTEREST_BLOCKS - below, goal.z - GOAL_INTEREST_BLOCKS,
			goal.x + GOAL_INTEREST_BLOCKS, goal.y + GOAL_INTEREST_BLOCKS + above, goal.z + GOAL_INTEREST_BLOCKS,
			InterestTier.CORRIDOR,
		)
		val dx = goal.x - start.x
		val dy = goal.y - start.y
		val dz = goal.z - start.z
		val length = maxOf(abs(dx), abs(dz), 1)
		if (length <= maxOf(NEAR_JOURNEY_BLOCKS, world.loadedRadiusBlocks)) {
			world.interestBlocks(
				minOf(start.x, goal.x) - JOURNEY_BOX_MARGIN_BLOCKS, minOf(start.y, goal.y) - JOURNEY_BOX_MARGIN_BLOCKS, minOf(start.z, goal.z) - JOURNEY_BOX_MARGIN_BLOCKS,
				maxOf(start.x, goal.x) + JOURNEY_BOX_MARGIN_BLOCKS, maxOf(start.y, goal.y) + JOURNEY_BOX_MARGIN_BLOCKS, maxOf(start.z, goal.z) + JOURNEY_BOX_MARGIN_BLOCKS,
				InterestTier.CORRIDOR,
			)
			return
		}
		var step = CORRIDOR_SAMPLE_BLOCKS
		while (step < length) {
			world.interestAround(
				start.x + dx * step / length, start.y + dy * step / length, start.z + dz * step / length,
				CORRIDOR_INTEREST_BLOCKS, CORRIDOR_INTEREST_Y_BLOCKS, InterestTier.CORRIDOR,
			)
			step += CORRIDOR_SAMPLE_BLOCKS
		}
	}

	private const val BODY_INTEREST_BLOCKS = 24
	private const val BODY_INTEREST_Y_BLOCKS = 16

	private const val GOAL_INTEREST_BLOCKS = 16

	private const val NEAR_JOURNEY_BLOCKS = 96
	private const val JOURNEY_BOX_MARGIN_BLOCKS = 16

	private const val CORRIDOR_SAMPLE_BLOCKS = 24
	private const val CORRIDOR_INTEREST_BLOCKS = 8
	private const val CORRIDOR_INTEREST_Y_BLOCKS = 12

	private const val ROUTE_INTEREST_BLOCKS = 8
	private const val ROUTE_INTEREST_Y_BLOCKS = 8
}

private fun PathingWorld.interestAround(x: Int, y: Int, z: Int, horizontal: Int, vertical: Int, tier: InterestTier) =
	interestBlocks(x - horizontal, y - vertical, z - horizontal, x + horizontal, y + vertical, z + horizontal, tier)

internal fun PathingWorld.interestAround(terminal: Stance, tier: InterestTier) =
	interestAround(terminal.x, terminal.y, terminal.z, TERMINAL_INTEREST_BLOCKS, TERMINAL_INTEREST_Y_BLOCKS, tier)

private const val TERMINAL_INTEREST_BLOCKS = 32
private const val TERMINAL_INTEREST_Y_BLOCKS = 16
