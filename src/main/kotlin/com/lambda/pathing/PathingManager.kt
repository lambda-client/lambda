package com.lambda.pathing

import com.lambda.config.automation.AutomationConfig
import com.lambda.config.blocks.PathingRenderConfig
import com.lambda.context.Automated
import com.lambda.context.AutomatedSafeContext
import com.lambda.event.events.ConnectionEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.events.WorldEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.event.listener.UnsafeListener.Companion.listenUnsafe
import com.lambda.interaction.managers.Manager
import com.lambda.pathing.core.Stance
import com.lambda.pathing.debug.PlanningDebugChannel
import com.lambda.pathing.session.PathingSession
import com.lambda.pathing.session.PathingSession.Companion.PATHING_SOURCE
import com.lambda.pathing.session.PathingSession.State
import com.lambda.pathing.session.Telemetry
import com.lambda.util.player.MovementUtils.update
import com.lambda.util.world.ChunkPacketLoadContext

/**
 * Manager-framework glue: takes pathing requests, owns the one [PathingSession] at a time
 * and the compound [WaypointRoute], and forwards the client events the session is driven
 * by. Everything about a walk lives in the session; `api/PathingService` is the surface
 * other code talks to.
 */
object PathingManager : Manager<PathingRequest>(0) {
    /** The current (or most recent) walk; kept after it ends so the renderer still has its tape. */
    private var session: PathingSession? = null

    @Volatile
    private var lastRenderConfig: PathingRenderConfig = AutomationConfig.DEFAULT.pathingRenderConfig

    val renderConfig: PathingRenderConfig get() = lastRenderConfig

    val status: State get() = session?.state ?: State.Idle

    val telemetry: Telemetry get() = session?.telemetry() ?: Telemetry.EMPTY

    /** True while a leg is being walked or a further leg (or request) is still queued. */
    val isRouting: Boolean
        get() = session?.active == true || queuedRequest != null || waypointRoute.queuedWaypoints > 0

    /** The compound route: queued waypoints, leg tracking, and the pre-warmed next leg. */
    private val waypointRoute = WaypointRoute(PATHING_SOURCE)

    fun diagnostics(): String = buildString {
        append("status=").append(status)
        telemetry.published?.let { p ->
            append(" tape=").append(p.plan.tape.frameCount)
            append(" routeGoal=").append(p.route.goal)
            append(" finalGoal=").append(p.finalGoal)
            append(" partial=").append(p.partial)
            append(" seq=").append(p.publicationSequence)
        }
        session?.journey?.world?.let { w ->
            append(" revision=").append(w.revision)
            append(" pendingInterest=").append(w.pendingInterest)
        }
    }

    fun isFinished(request: PathingRequest): Boolean = when {
        queuedRequest === request -> false
        else -> session?.isFinished(request) ?: true
    }

    /**
     * Walks [waypoints] in order: the first leg is requested now and each arrival
     * submits the next. Any unrelated pathing request, a cancel, or a failed leg
     * drops the remainder -- continuing a route past a leg that did not arrive
     * would walk the tail from the wrong place.
     */
    fun route(automated: Automated, waypoints: List<Stance>): PathingRequest? =
        waypointRoute.start(automated, waypoints)

    fun cancel() {
        waypointRoute.drop()
        session?.cancel()
    }

    fun clear() {
        waypointRoute.drop()
        session?.let { walk ->
            walk.release()
            walk.takeJourney()?.cancel()
        }
        session = null
        PlanningDebugChannel.reset()
    }

    override fun AutomatedSafeContext.handleRequest(request: PathingRequest) {
        if (!request.fresh) return

        // A request that is not this route's own next leg replaces the route.
        if (request.goal != waypointRoute.expectedLeg) waypointRoute.drop()

        val previous = session
        var journey = previous?.takeJourney()

        // Adopt the pre-warmed next-leg journey before the reuse check below, so a
        // route continuation lands on a world already captured and a coarse state
        // already built instead of paying the cold start at every waypoint.
        waypointRoute.adoptWarmJourney(TrajectoryPlanner.resolveGoalStance(player, request.goal))?.let { warmed ->
            journey?.cancel()
            journey = warmed
        }

        val sameGoal = journey?.goal == TrajectoryPlanner.resolveGoalStance(player, request.goal)
        previous?.release()
        if (!sameGoal) {
            journey?.cancel()
            journey = null
        }
        PlanningDebugChannel.reset()

        val walk = PathingSession(request, journey, waypointRoute)
        session = walk
        lastRenderConfig = request.pathingRenderConfig

        with(walk) { start() }
    }

    init {
        listenUnsafe<ConnectionEvent.Disconnect> { clear() }
        listenUnsafe<WorldEvent.BlockUpdate.Client> { event ->
            if (event.world.isClient && event.oldState != event.newState &&
                !ChunkPacketLoadContext.isActive()
            ) {
                session?.onBlockChanged(event.pos)
                waypointRoute.onBlockChanged(event.pos)
            }
        }
        listenUnsafe<WorldEvent.ChunkEvent.Load> { event ->
            session?.onChunkEvent(event.chunk.pos.x, event.chunk.pos.z)
            waypointRoute.onChunkEvent(event.chunk.pos.x, event.chunk.pos.z)
        }
        listen<TickEvent.Pre> {
            val walk = session ?: return@listen
            with(walk) { tick() }
        }

        listen<MovementEvent.InputUpdate>({ Int.MAX_VALUE }) { event ->
            val walk = session ?: return@listen
            val input = with(walk) { inputForThisTick() } ?: return@listen
            event.input.update(
                forward = input.forward,
                strafe = input.strafe,
                jump = input.jump,
                sneak = input.sneak,
                sprint = input.sprint,
            )
        }
    }
}
