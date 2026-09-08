package com.lambda.pathing

import com.lambda.Lambda.LOG
import com.lambda.context.Automated
import com.lambda.pathing.core.ArrivalMode
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PlanningCancellation
import com.lambda.pathing.session.PlanningJourney
import com.lambda.pathing.world.InterestPrimer
import com.lambda.pathing.world.PathingWorld
import com.lambda.pathing.search.PublishedPath
import com.lambda.util.CommunicationUtils.info
import net.minecraft.client.network.ClientPlayerEntity

/**
 * The compound route. Waypoints are grouped into requests: a run of walk-through
 * waypoints and the stand-still waypoint (or final goal) that ends it make one request,
 * walked as one continuous tape by one search. Each (non-partial) arrival submits the
 * next group via [continueNext]. Any unrelated pathing request, a cancel, or a failed
 * leg drops the remainder -- continuing a route past a leg that did not arrive would
 * walk the tail from the wrong place. See docs/decisions/arrival.md.
 */
internal class WaypointRoute(private val source: String) {
    /** A request's worth of route: the walk-through waypoints and the goal that ends the group. */
    private class Group(val through: List<Stance>, val goal: Stance) {
        val size: Int get() = through.size + 1
    }

    private val queue = ArrayDeque<Group>()
    private var automated: Automated? = null

    /** The goal of the group currently being walked; a request for any other goal replaces the route. */
    var expectedLeg: Stance? = null
        private set

    /**
     * The NEXT group's journey, pre-warmed while the body still replays the current one:
     * its world streams capture and its coarse states exist before arrival, so the
     * handoff pays neither the capture wait nor the coarse cold start. Adopted by
     * [adoptWarmJourney] when the continuation request lands.
     */
    private var nextJourney: PlanningJourney? = null

    /** Waypoints still to walk after the active request, across every queued group. */
    val queuedWaypoints: Int get() = queue.sumOf { it.size }

    /** Requests the first group and queues the rest; null when there is nothing to walk. */
    fun start(automated: Automated, waypoints: List<Stance>, intermediate: ArrivalMode): PathingRequest? {
        drop()
        if (waypoints.isEmpty()) return null
        this.automated = automated
        val groups = ArrayList<Group>()
        val through = ArrayList<Stance>()
        waypoints.forEachIndexed { index, waypoint ->
            val last = index == waypoints.lastIndex
            if (!last && intermediate == ArrivalMode.WALK_THROUGH) {
                through += waypoint
            } else {
                groups += Group(through.toList(), waypoint)
                through.clear()
            }
        }
        val first = groups.first()
        queue.addAll(groups.drop(1))
        expectedLeg = first.goal
        return PathingRequest(automated, first.goal, first.through).submit()
    }

    fun drop() {
        queue.clear()
        automated = null
        expectedLeg = null
        nextJourney?.cancel()
        nextJourney = null
    }

    /** The pre-warmed journey, surrendered when the incoming request matches its goal. */
    fun adoptWarmJourney(resolvedGoal: Stance): PlanningJourney? {
        val warmed = nextJourney?.takeIf { it.goal == resolvedGoal } ?: return null
        nextJourney = null
        return warmed
    }

    /** [replaying] is whether the walk holds an execution cursor; [request] is the group being walked. */
    fun primeNextLeg(
        player: ClientPlayerEntity,
        request: PathingRequest,
        replaying: Boolean,
        published: PublishedPath?,
    ) {
        if (automated == null) return
        val next = queue.firstOrNull() ?: return
        // Only while replaying a COMPLETE tape: its terminal is where the body
        // will actually stand at the handoff, so bounds and interest are primed
        // from there, not from wherever the body happens to be mid-leg.
        if (!replaying) return
        val running = published ?: return
        if (running.partial) return
        val terminal = running.plan.frames.lastOrNull()?.state ?: return
        val resolved = TrajectoryPlanner.resolveGoalStance(player, next.goal)
        if (nextJourney?.goal == resolved) return
        nextJourney?.cancel()
        nextJourney = null

        val cancellation = PlanningCancellation()
        val preparation = when (
            val prepared = TrajectoryPlanner.prepare(
                player = player,
                goal = next.goal,
                config = request.pathingConfig,
                turnSpeed = request.rotationConfig.turnSpeed,
                cancellation = cancellation,
                initialOverride = terminal,
                waypoints = next.through,
            )
        ) {
            is PlanningPreparationResult.Ready -> prepared.preparation
            else -> return
        }
        val pathingWorld = PathingWorld(preparation.bounds, player.entityWorld, player)
        InterestPrimer.primeJourney(pathingWorld, preparation.start, preparation.finalGoal, preparation.waypoints)
        nextJourney = PlanningJourney(
            goal = preparation.finalGoal,
            waypoints = preparation.waypoints,
            moveOptions = preparation.moveOptions,
            profile = preparation.profile,
            cancellation = cancellation,
            world = pathingWorld,
            legs = TrajectoryPlanner.journeyLegs(
                preparation, pathingWorld.snapshot, pathingWorld::chunkCapturable,
            ),
        )
        LOG.info("Pre-warming the next route leg toward {}", preparation.finalGoal)
    }

    fun continueNext(): Boolean {
        val automated = automated ?: return false
        val next = queue.removeFirstOrNull() ?: run { drop(); return false }
        expectedLeg = next.goal
        info(
            "Route: continuing to (${next.goal.x}, ${next.goal.y}, ${next.goal.z})" +
                (next.through.size.takeIf { it > 0 }?.let { " through $it waypoint(s)" } ?: "") +
                (queuedWaypoints.takeIf { it > 0 }?.let { ", $it more after it" } ?: "") + ".",
            source,
        )
        PathingRequest(automated, next.goal, next.through).submit()
        return true
    }

    /** The pre-warmed leg streams its capture alongside the active journey's. */
    fun advanceCapture(budgetMillis: Double) {
        nextJourney?.world?.advance(budgetMillis)
    }

    fun onBlockChanged(pos: net.minecraft.util.math.BlockPos) {
        nextJourney?.world?.onBlockChanged(pos)
    }

    fun onChunkEvent(x: Int, z: Int) {
        nextJourney?.world?.onChunkEvent(x, z)
    }
}
