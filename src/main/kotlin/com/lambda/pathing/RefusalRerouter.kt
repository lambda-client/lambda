package com.lambda.pathing

import com.lambda.pathing.coarse.CoarsePlanner
import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.core.VoxelPos
import kotlin.time.Duration
import org.apache.logging.log4j.LogManager

internal class RefusalRerouter(
    private val planner: CoarsePlanner,
    private val field: CoarseValueField,
    private val reroute: () -> CoarseRoutePlan?,
    private val cancelled: () -> Boolean,
    private val maxRounds: Int = DEFAULT_MAX_ROUNDS,
) {
    fun walk(initial: CoarseRoutePlan, attempt: (CoarseRoutePlan) -> PathPlanResult): PathPlanResult {
        var route = initial
        var rounds = 0
        while (true) {
            val outcome = attempt(route)

            val refusal = (outcome as? PathPlanResult.Failed)?.failure
                as? PlanningFailure.NoCertifiedMotion
            val stalledFrom = refusal?.stalledFrom
            val stalledTo = refusal?.stalledTo
            if (stalledFrom == null || stalledTo == null || cancelled() || rounds >= maxRounds) {
                return outcome
            }

            LOG.info(
                "Demoting coarse arrivals at {} from around {} after a trajectory refusal; rerouting",
                stalledTo, stalledFrom,
            )
            for (dx in -1..1) for (dy in -1..1) for (dz in -1..1) {
                planner.demoteEdge(stalledFrom.offset(dx, dy, dz), stalledTo)
            }
            field.invalidate(
                setOf(
                    PathingSection.containing(VoxelPos(stalledFrom.x, stalledFrom.y, stalledFrom.z)),
                    PathingSection.containing(VoxelPos(stalledTo.x, stalledTo.y, stalledTo.z)),
                ),
            )
            planner.repair(timeBudget = Duration.INFINITE, cancelled = cancelled)
            val next = reroute() ?: return outcome
            if (next.nodes == route.nodes) return outcome
            route = next
            rounds++
        }
    }

    private companion object {
        const val DEFAULT_MAX_ROUNDS = 3

        val LOG = LogManager.getLogger("NeoLambda-Pathing")
    }
}
