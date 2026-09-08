package com.lambda.pathing.world

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.core.Stance
import com.lambda.pathing.world.InterestTier
import com.lambda.pathing.world.PathingWorld
import net.minecraft.util.math.BlockPos

internal object InterestPrimer {
    fun primeBody(world: PathingWorld, pos: BlockPos) {
        world.interestBlocks(
            pos.x - BODY_INTEREST_BLOCKS, pos.y - BODY_INTEREST_Y_BLOCKS, pos.z - BODY_INTEREST_BLOCKS,
            pos.x + BODY_INTEREST_BLOCKS, pos.y + BODY_INTEREST_Y_BLOCKS, pos.z + BODY_INTEREST_BLOCKS,
            InterestTier.BODY,
        )
    }

    fun primeRoute(world: PathingWorld, route: CoarseRoutePlan) {
        route.nodes.forEach { node ->
            world.interestBlocks(
                node.x - ROUTE_INTEREST_BLOCKS, node.y - ROUTE_INTEREST_Y_BLOCKS, node.z - ROUTE_INTEREST_BLOCKS,
                node.x + ROUTE_INTEREST_BLOCKS, node.y + ROUTE_INTEREST_Y_BLOCKS, node.z + ROUTE_INTEREST_BLOCKS,
                InterestTier.CORRIDOR,
            )
        }
    }

    /** A compound route: the corridor of every leg, waypoint neighbourhoods included. */
    fun primeJourney(world: PathingWorld, start: Stance, goal: Stance, waypoints: List<Stance>) {
        var from = start
        for (waypoint in waypoints) {
            primeJourney(world, from, waypoint)
            from = waypoint
        }
        primeJourney(world, from, goal)
    }

    fun primeJourney(world: PathingWorld, start: Stance, goal: Stance) {
        world.interestBlocks(
            goal.x - GOAL_INTEREST_BLOCKS, goal.y - GOAL_INTEREST_BLOCKS, goal.z - GOAL_INTEREST_BLOCKS,
            goal.x + GOAL_INTEREST_BLOCKS, goal.y + GOAL_INTEREST_BLOCKS, goal.z + GOAL_INTEREST_BLOCKS,
            InterestTier.CORRIDOR,
        )
        val dx = goal.x - start.x
        val dy = goal.y - start.y
        val dz = goal.z - start.z
        val length = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz), 1)
        var step = CORRIDOR_SAMPLE_BLOCKS
        while (step < length) {
            val x = start.x + dx * step / length
            val y = start.y + dy * step / length
            val z = start.z + dz * step / length
            world.interestBlocks(
                x - CORRIDOR_INTEREST_BLOCKS, y - CORRIDOR_INTEREST_Y_BLOCKS, z - CORRIDOR_INTEREST_BLOCKS,
                x + CORRIDOR_INTEREST_BLOCKS, y + CORRIDOR_INTEREST_Y_BLOCKS, z + CORRIDOR_INTEREST_BLOCKS,
                InterestTier.CORRIDOR,
            )
            step += CORRIDOR_SAMPLE_BLOCKS
        }
    }

    private const val BODY_INTEREST_BLOCKS = 24
    private const val BODY_INTEREST_Y_BLOCKS = 16

    private const val GOAL_INTEREST_BLOCKS = 16

    private const val CORRIDOR_SAMPLE_BLOCKS = 24
    private const val CORRIDOR_INTEREST_BLOCKS = 8
    private const val CORRIDOR_INTEREST_Y_BLOCKS = 12

    private const val ROUTE_INTEREST_BLOCKS = 8
    private const val ROUTE_INTEREST_Y_BLOCKS = 8
}
