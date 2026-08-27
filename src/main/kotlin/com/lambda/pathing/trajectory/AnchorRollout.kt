package com.lambda.pathing.trajectory

import com.lambda.pathing.coarse.CoarseValueField
import com.lambda.pathing.movement.CompletionContext
import com.lambda.pathing.core.HorizontalPoint
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.LaunchTrigger
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.movement.MovementCatalog
import com.lambda.pathing.movement.ProgramContext
import com.lambda.pathing.movement.TerminalApproach
import com.lambda.pathing.movement.TrajectoryDecision
import com.lambda.pathing.world.center
import com.lambda.pathing.prediction.simulation.PlayerPhysicsProfile
import com.lambda.pathing.prediction.SnapshotSimulationEnvironment
import kotlin.math.hypot

internal sealed interface Outcome {
    data class Anchored(val anchor: ValueAnchor) : Outcome
    data class Arrived(val frames: List<SimulatedTrajectoryFrame>, val stopFrame: Int) : Outcome
    data class Rejected(val diagnostic: TrajectoryDiagnostic) : Outcome

    data class Blocked(val frame: Int, val sectionX: Int, val sectionY: Int, val sectionZ: Int) : Outcome
}

internal class AnchorRollout(
    private val movements: MovementCatalog,
    private val field: CoarseValueField,
    private val config: MotionConstraints,
    private val searchConfig: ValueFieldSearchConfig,
    private val environment: SnapshotSimulationEnvironment,
    private val profile: PlayerPhysicsProfile,

    private val goalPoint: () -> HorizontalPoint,
    private val attempts: AttemptAccumulator,
    private val progressOf: (Stance) -> Int,
    private val probe: SearchProbe,
) {
    /**
     * A landing with mapped stances beside it is open terrain; one without is an
     * isolated pad. Two neighbours is the threshold rather than one so a cell at the
     * edge of a platform still counts as open -- it has the platform behind it.
     */
    private fun openLanding(target: Stance): Boolean {
        var mapped = 0
        if (field.isMapped(Stance(target.x + 1, target.y, target.z))) mapped++
        if (field.isMapped(Stance(target.x - 1, target.y, target.z))) mapped++
        if (field.isMapped(Stance(target.x, target.y, target.z + 1))) mapped++
        if (field.isMapped(Stance(target.x, target.y, target.z - 1))) mapped++
        return mapped >= 2
    }

    /**
     * A rollout split into phases so a batch can run its simulations concurrently:
     * [prepare] on the coordinator (it reads the guide field, whose memoisation is not
     * thread-safe), [execute] on any worker (pure simulation over the snapshot plus
     * state confined to this object), [complete] back on the coordinator (probes,
     * field post-checks, anchor construction). [transition] is their composition and
     * the serial path is byte-identical to what it always did.
     */
    internal class PreparedRollout(
        val anchor: ValueAnchor,
        val action: TrajectoryDecision,
        internal val movement: com.lambda.pathing.movement.Movement,
        internal val points: List<HorizontalPoint>,
        internal val program: com.lambda.pathing.movement.ControlProgram,
        internal val evaluator: RolloutEvaluator,
        internal val descentAllowance: Double,
        internal val frameCount: Int,
        internal val launch: LaunchTrigger?,
    ) {
        internal var raw: TrajectoryRollout? = null
        internal var failure: TrajectoryDiagnostic? = null
        internal var stopFrame: Int? = null
        internal var eventFrame: Int? = null
        internal var eventStance: Stance = anchor.stance
    }

    fun prepare(anchor: ValueAnchor, action: TrajectoryDecision): PreparedRollout? {
        val chain = field.chain(
            anchor.stance, action.step, searchConfig.chainLength, anchor.heading(),
        )
        val points = chain.map { it.center(environment) }
        val movement = movements[action.movement] ?: return null

        val launch = when (action) {
            is TrajectoryDecision.Launch -> LaunchTrigger(action.delayFrames)
            is TrajectoryDecision.Heading -> action.delayFrames?.let { LaunchTrigger(it) }
            else -> null
        }
        val program = movement.program(
            ProgramContext(
                decision = action,
                body = anchor,
                nodes = points,
                constraints = config,
                launch = launch,
                openLanding = action.step?.let { openLanding(it) } ?: false,
            )
        )
        val descentAllowance = movement.descentAllowance(action)
        return PreparedRollout(
            anchor = anchor,
            action = action,
            movement = movement,
            points = points,
            program = program,
            evaluator = RolloutEvaluator(
                anchor.state, points, goalPoint(), config,
                allowHorizontalContact = movement.pressesIntoTerrain,
                descentAllowance = descentAllowance,
            ),
            descentAllowance = descentAllowance,
            frameCount = maxOf(searchConfig.maxTransitionFrames, movement.transitionFrames(action)),
            launch = launch,
        )
    }

    fun execute(prepared: PreparedRollout) {
        val anchor = prepared.anchor
        val action = prepared.action
        val movement = prepared.movement
        var previous = anchor.state
        var airborne = false

        prepared.raw = TrajectoryRolloutEngine.rollout(
            initialState = anchor.state,
            profile = profile,
            environment = environment,
            program = prepared.program,
            frameCount = prepared.frameCount,
        ) { frame ->
            val verdict = prepared.evaluator.observe(frame.index, frame.state, previous)
            previous = frame.state
            when (verdict) {
                is RolloutVerdict.Failed -> {
                    prepared.failure = verdict.diagnostic
                    true
                }

                is RolloutVerdict.Stopped -> {
                    prepared.stopFrame = verdict.frame
                    true
                }

                is RolloutVerdict.Continue -> {
                    if (!frame.state.onGround) airborne = true

                    if (!frame.state.onGround && !movement.completesAirborne) {
                        false
                    } else {
                        val stance = ValueFieldAnchorSearch.stanceOf(frame.state)
                        val done = movement.completed(
                            CompletionContext(
                                decision = action,
                                body = anchor,
                                frameIndex = frame.index,
                                observed = frame.state,
                                stance = stance,
                                airborne = airborne,
                                launch = prepared.launch,
                                headingCommitFrames = searchConfig.headingCommitFrames,
                            )
                        )

                        val moving = frame.state.velocity.horizontalLength() > config.stoppedSpeed ||
                            action is TrajectoryDecision.Drop || movement.completesAirborne
                        if (done && moving && stance != anchor.stance) {
                            prepared.eventFrame = frame.index
                            prepared.eventStance = stance
                            true
                        } else {
                            false
                        }
                    }
                }
            }
        }
    }

    fun transition(anchor: ValueAnchor, action: TrajectoryDecision, hazardFrame: Int?): Outcome {
        val prepared = prepare(anchor, action)
            ?: return Outcome.Rejected(TrajectoryDiagnostic.NoStop(0, 0.0, anchor.speed))
        execute(prepared)
        return complete(prepared, hazardFrame)
    }

    fun complete(prepared: PreparedRollout, hazardFrame: Int?): Outcome {
        val anchor = prepared.anchor
        val action = prepared.action
        val points = prepared.points
        val descentAllowance = prepared.descentAllowance
        val rollout = checkNotNull(prepared.raw) { "complete() before execute()" }
        val failure = prepared.failure
        val stopFrame = prepared.stopFrame
        val eventFrame = prepared.eventFrame
        var eventStance = prepared.eventStance

        record(anchor, action, rollout, failure, stopFrame != null)

        stopFrame?.let { return Outcome.Arrived(rollout.frames, it) }
        failure?.let { return Outcome.Rejected(it) }
        (rollout.termination as? TrajectoryRolloutTermination.Blocked)?.let {
            return Outcome.Blocked(it.frame, it.sectionX, it.sectionY, it.sectionZ)
        }
        val frame = eventFrame ?: return Outcome.Rejected(
            evaluate(rollout, points, goalPoint(), config, descentAllowance).diagnostic
                ?: TrajectoryDiagnostic.NoStop(rollout.frames.size, 0.0, anchor.speed),
        )

        val frames = rollout.frames.take(frame + 1)

        var cornerCatch = 0
        if (!field.isStance(eventStance)) {
            // A corner catch: the body stands on a pad's edge with its centre floored
            // into the air cell beside it. The support block names the real stance.
            // Priced like the scrape it is, at two collision events: a clean centred
            // landing typically pays a few brake frames the corner catch skips, so one
            // event (four frames) measured as a dead heat on the drop staircase and the
            // wobbling tape kept winning. Two makes clean strictly better wherever it
            // exists, while a course whose only way onward is the corner (the diagonal
            // zig-zag) still gets it.
            val supported = ValueFieldAnchorSearch.supportedStanceOf(frames.last().state)
                ?.takeIf { field.isStance(it) }
            if (supported == null) {
                return Outcome.Rejected(
                    TrajectoryDiagnostic.FellBelowRoute(frame, 0.0)
                )
            }
            eventStance = supported
            cornerCatch = 2
        }
        if (!field.isMapped(eventStance)) {

            return if (!field.view.isKnown(eventStance.x, eventStance.y - 1, eventStance.z)) {
                Outcome.Blocked(frame, eventStance.x shr 4, (eventStance.y - 1) shr 4, eventStance.z shr 4)
            } else {
                Outcome.Rejected(TrajectoryDiagnostic.FellBelowRoute(frame, 0.0))
            }
        }
        if (anchor.hasVisited(eventStance)) {
            return Outcome.Rejected(
                TrajectoryDiagnostic.RepeatedCoarseStance(frame)
            )
        }

        return Outcome.Anchored(
            ValueAnchor(
                state = frames.last().state,
                stance = eventStance,
                elapsed = anchor.elapsed + frames.size,
                collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames) + cornerCatch,
                launchMargin = anchor.launchMargin + launchMargin(frames, hazardFrame),
                inputSwitches = anchor.inputSwitches + inputSwitches(anchor.inputs.lastOrNull(), frames),
                parent = anchor,
                inputs = frames.map { it.input },
                boundary = anchor.elapsed + frames.size,
            ).also { it.via = action.movement },
        )
    }

    private fun record(
        anchor: ValueAnchor,
        action: TrajectoryDecision,
        rollout: TrajectoryRollout,
        failure: TrajectoryDiagnostic?,
        stopped: Boolean,
    ) {
        probe.attempt(rollout, stopped, failure)
        val goal = goalPoint()
        attempts.record(PlanAttempt(
            parameters = TerminalApproach(
                sprint = action.sprint,
                lookAheadNodes = (action as? TrajectoryDecision.Walk)?.lookAheadNodes ?: ValueFieldAnchorSearch.LOOK_AHEAD_NODES,
                brakeDistance = config.brakeDistances.first(),
                stepUpJumpLeadDistance = null,
            ),
            simulatedFrames = rollout.frames.size,
            finalGoalError = hypot(
                rollout.finalState.position.x - goal.x,
                rollout.finalState.position.z - goal.z,
            ),
            finalHorizontalSpeed = rollout.finalState.velocity.horizontalLength(),
            diagnostic = failure,
            blockedProgress = progressOf(anchor.stance),
        ))
    }
}
