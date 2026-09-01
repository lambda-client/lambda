package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseRoutePlan
import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.prediction.simulation.MovementSimulationState
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import java.util.concurrent.ExecutorService

object ValueFieldAnchorSearch {

    fun search(
        route: CoarseRoutePlan,
        catalog: MovementCatalog,
        field: CoarseValueField,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        environment: SnapshotSimulationEnvironment,
        config: MotionConstraints = MotionConstraints(),
        searchConfig: ValueFieldSearchConfig = ValueFieldSearchConfig(),
        onSafePrefix: ((MotionPlanResult.Success) -> Unit)? = null,
        cursorFrame: (() -> Int?)? = null,
        clock: SearchClock = SystemSearchClock(),
        cancelled: () -> Boolean = { false },

        worldWait: ((Long) -> Boolean)? = null,

        worldSync: ((CoarseRoutePlan) -> WorldSyncResult)? = null,

        sectionCapturable: ((Int, Int) -> Boolean)? = null,

        expandGuide: ((Double) -> Unit)? = null,

        adoptedSequence: (() -> Long)? = null,

        finalGoal: Stance? = null,

        probe: SearchProbe = SearchProbe.NONE,

        /**
         * Where the search reports what it had spent and unlocked when it stopped.
         *
         * A callback rather than a log line: this runs inside plain unit tests, and the
         * mod's logger cannot static-initialise outside a Minecraft runtime.
         */
        onExhaustion: ((SearchExhaustion) -> Unit)? = null,

        /**
         * Rollouts per expansion batch. At 1 the search is the exact serial search; above
         * it, up to this many frontier decisions are selected in rank order, simulated
         * concurrently on [executor], and their outcomes applied in the same order --
         * deterministic for a fixed setting, though a different search than serial
         * (batch members cannot see each other's failures until the batch lands).
         */
        parallelism: Int = 1,
        executor: ExecutorService? = null,
    ): MotionPlanResult {
        if (cancelled()) return MotionPlanResult.Cancelled
        val unsupported = route.edges.mapTo(HashSet()) { it.movement }
            .filterTo(HashSet()) { !catalog.supports(it) }
        if (unsupported.isNotEmpty()) return MotionPlanResult.UnsupportedRoute(unsupported)

        return AnchorSearchSession(
            route, catalog, field, initialState, profile, environment, config, searchConfig,
            onSafePrefix, cursorFrame, clock, cancelled, worldWait, worldSync, sectionCapturable,
            expandGuide, adoptedSequence, finalGoal, probe, onExhaustion,
            parallelism = if (executor != null) parallelism.coerceAtLeast(1) else 1,
            executor = executor,
        ).run()
    }

    internal fun stanceOf(state: MovementSimulationState): Stance =
        Stance.of(state.position, state.onGround)

    /**
     * Where a grounded state is actually standing, when flooring its centre names a
     * cell that is not a stance at all: the body caught a lone pad with its edge and
     * hangs its centre over the air beside it. On the diagonal zig-zag fixture every
     * corner catch of the far pad was rejected as FellBelowRoute this way. A FALLBACK
     * only -- a landing whose floored cell is a real stance keeps its name, so clean
     * centred landings and sloppy corner catches stay distinguishable and the drop
     * staircase's hold-the-line quality gate keeps meaning something.
     */
    internal fun supportedStanceOf(state: MovementSimulationState): Stance? {
        if (!state.onGround) return null
        val support = state.supportingBlockPos ?: return null
        val base = Stance.of(state.position, true)
        if (support.x == base.x && support.z == base.z) return null
        return Stance(support.x, support.y + 1, support.z)
    }
}
