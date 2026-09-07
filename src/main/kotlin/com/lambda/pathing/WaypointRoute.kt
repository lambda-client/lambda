package com.lambda.pathing

import com.lambda.Lambda.LOG
import com.lambda.context.Automated
import com.lambda.pathing.core.Stance
import com.lambda.pathing.session.PlanningCancellation
import com.lambda.pathing.session.PlanningJourney
import com.lambda.pathing.world.InterestPrimer
import com.lambda.pathing.search.PublishedPath
import com.lambda.pathing.world.PathingWorld
import com.lambda.util.CommunicationUtils.info
import net.minecraft.client.network.ClientPlayerEntity

/**
 * The compound route: waypoints still to walk after the active goal. The first leg is
 * requested by [start] and each (non-partial) arrival submits the next via
 * [continueNext]. Any unrelated pathing request, a cancel, or a failed leg drops the
 * remainder -- continuing a route past a leg that did not arrive would walk the tail
 * from the wrong place.
 */
internal class WaypointRoute(private val source: String) {
    private val queue = ArrayDeque<Stance>()
    private var automated: Automated? = null

    /** The leg currently being walked; a request for any other goal replaces the route. */
    var expectedLeg: Stance? = null
        private set

    /**
     * The NEXT leg's journey, pre-warmed while the body still replays the current
     * one: its world streams capture and its coarse state exists before arrival,
     * so the waypoint handoff pays neither the capture wait nor the coarse cold
     * start -- only the fine search, from a body that arrived braked. Adopted by
     * [adoptWarmJourney] when the continuation request lands.
     */
    private var nextJourney: PlanningJourney? = null

    val queuedWaypoints: Int get() = queue.size

    /** Requests the first leg and queues the rest; null when there is nothing to walk. */
    fun start(automated: Automated, waypoints: List<Stance>): PathingRequest? {
        drop()
        val first = waypoints.firstOrNull() ?: return null
        this.automated = automated
        expectedLeg = first
        queue.addAll(waypoints.drop(1))
        return PathingRequest(automated, first).submit()
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

    /** [replaying] is whether the walk holds an execution cursor; [request] is the leg being walked. */
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
        val resolved = TrajectoryPlanner.resolveGoalStance(player, next)
        if (nextJourney?.goal == resolved) return
        nextJourney?.cancel()
        nextJourney = null

        val cancellation = PlanningCancellation()
        val preparation = when (
            val prepared = TrajectoryPlanner.prepare(
                player = player,
                goal = next,
                config = request.pathingConfig,
                turnSpeed = request.rotationConfig.turnSpeed,
                cancellation = cancellation,
                initialOverride = terminal,
            )
        ) {
            is PlanningPreparationResult.Ready -> prepared.preparation
            else -> return
        }
        val pathingWorld = PathingWorld(preparation.bounds, player.entityWorld, player)
        InterestPrimer.primeJourney(pathingWorld, preparation.start, preparation.finalGoal)
        nextJourney = PlanningJourney(
            goal = preparation.finalGoal,
            moveOptions = preparation.moveOptions,
            profile = preparation.profile,
            cancellation = cancellation,
            world = pathingWorld,
            coarseState = TrajectoryPlanner.coarseState(
                preparation, pathingWorld.snapshot, pathingWorld::chunkCapturable,
            ),
        )
        LOG.info("Pre-warming the next route leg toward {}", preparation.finalGoal)
    }

    fun continueNext(): Boolean {
        val automated = automated ?: return false
        val next = queue.removeFirstOrNull() ?: run { drop(); return false }
        expectedLeg = next
        info(
            "Route: continuing to (${next.x}, ${next.y}, ${next.z})" +
                (queue.size.takeIf { it > 0 }?.let { ", $it more after it" } ?: "") + ".",
            source,
        )
        PathingRequest(automated, next).submit()
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
