package com.lambda.pathing.search

import com.lambda.pathing.core.PathingSection
import com.lambda.pathing.rollout.SimulatedTrajectoryFrame
import com.lambda.pathing.rollout.TrajectoryRolloutTermination
import com.lambda.pathing.rollout.TrajectoryRollout
import com.lambda.pathing.rollout.TrajectoryRolloutEngine
import com.lambda.pathing.rollout.TrajectoryDiagnostic
import com.lambda.pathing.rollout.RolloutVerdict
import com.lambda.pathing.rollout.RolloutEvaluator

import com.lambda.pathing.actions.CompletionContext
import com.lambda.pathing.actions.ControlProgram
import com.lambda.pathing.actions.LaunchTrigger
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.Movement
import com.lambda.pathing.actions.families.MovementCatalog
import com.lambda.pathing.actions.ProgramContext
import com.lambda.pathing.actions.SteeringField
import com.lambda.pathing.actions.TrajectoryDecision
import com.lambda.pathing.coarse.ValueField
import com.lambda.pathing.core.Stance
import com.lambda.pathing.physics.FallRule
import com.lambda.pathing.physics.MovementSimulationState
import com.lambda.pathing.physics.PlayerPhysicsProfile
import com.lambda.pathing.physics.landingRule
import com.lambda.pathing.world.Footing
import com.lambda.pathing.world.Medium
import com.lambda.pathing.world.climbableAt
import com.lambda.pathing.world.snapshot.SnapshotSimulationEnvironment
import net.minecraft.util.math.Vec3d

internal sealed interface Outcome {
	data class Anchored(val anchor: ValueAnchor) : Outcome
	data class Arrived(val frames: List<SimulatedTrajectoryFrame>) : Outcome
	data class Rejected(val diagnostic: TrajectoryDiagnostic) : Outcome
	data class Blocked(val frame: Int, val section: PathingSection) : Outcome
}

internal class AnchorRollout(
	private val movements: MovementCatalog,
	private val field: ValueField,
	private val config: MotionConstraints,
	private val environment: SnapshotSimulationEnvironment,
	private val profile: PlayerPhysicsProfile,
	private val route: RouteState,
	private val attempts: AttemptAccumulator,
	private val probe: SearchProbe,
	private val routePoints: RoutePoints,
	private val visits: VisitIndex,
) {
	private val climbingAt: (Vec3d) -> Boolean = { field.view.climbableAt(it) }

	private val landingRuleOf: (MovementSimulationState) -> FallRule = { environment.landingRule(it) }

	private fun openLanding(target: Stance): Boolean {
		var mapped = 0
		if (field.isMapped(Stance(target.x + 1, target.y, target.z))) mapped++
		if (field.isMapped(Stance(target.x - 1, target.y, target.z))) mapped++
		if (field.isMapped(Stance(target.x, target.y, target.z + 1))) mapped++
		if (field.isMapped(Stance(target.x, target.y, target.z - 1))) mapped++
		return mapped >= OPEN_LANDING_NEIGHBORS
	}

	private val recentRollouts = object : LinkedHashMap<ValueAnchor, ArrayDeque<List<SimulatedTrajectoryFrame>>>(RECENT_ANCHORS, 0.75f, true) {
		override fun removeEldestEntry(eldest: MutableMap.MutableEntry<ValueAnchor, ArrayDeque<List<SimulatedTrajectoryFrame>>>) = size > RECENT_ANCHORS
	}

	internal class PreparedRollout(
		val anchor: ValueAnchor,
		val action: TrajectoryDecision,
		internal val movement: Movement,
		internal val program: ControlProgram,
		internal val evaluator: RolloutEvaluator,
		internal val frameCount: Int,
		internal val launch: LaunchTrigger?,
		internal val siblings: List<List<SimulatedTrajectoryFrame>>,
	) {
		internal var raw: TrajectoryRollout? = null
		internal var failure: TrajectoryDiagnostic? = null
		internal var stopFrame: Int? = null
		internal var eventFrame: Int? = null
		internal var eventStance: Stance = anchor.stance
	}

	fun prepare(anchor: ValueAnchor, action: TrajectoryDecision): PreparedRollout? {
		val chain = field.chain(anchor.stance, action.step, SteeringField.PURSUIT_CHAIN_LENGTH)
		val points = routePoints.of(chain)
		val movement = movements[action.movement] ?: return null

		val launch = action.delayFrames?.let { LaunchTrigger(it) }
		val program = movement.program(
			ProgramContext(
				decision = action,
				body = anchor,
				nodes = points,
				constraints = config,
				launch = launch,
				openLanding = openLanding(action.step),
			)
		)
		return PreparedRollout(
			anchor = anchor,
			action = action,
			movement = movement,
			program = program,
			evaluator = RolloutEvaluator(
				anchor.state, points, route.goalPoint, config,
				allowHorizontalContact = movement.inMedium || Footing.involvesPartial(field.view, action.step.x, action.step.y, action.step.z) ||
						action.delayFrames != null && Footing.involvesPartial(field.view, anchor.stance.x, anchor.stance.y, anchor.stance.z),
				descentAllowance = movement.descentAllowance(action),
				climbing = climbingAt,
				landingRule = landingRuleOf,
				lava = { environment.lavaWithin(it) },
			),
			frameCount = maxOf(TRANSITION_FRAMES, movement.transitionFrames(action)),
			launch = launch,
			siblings = recentRollouts[anchor]?.toList().orEmpty(),
		)
	}

	fun execute(prepared: PreparedRollout) {
		val anchor = prepared.anchor
		val action = prepared.action
		val movement = prepared.movement
		var airborne = false

		prepared.raw = TrajectoryRolloutEngine.rollout(
			initialState = anchor.state,
			profile = profile,
			environment = environment,
			program = prepared.program,
			frameCount = prepared.frameCount,
			siblings = prepared.siblings,
		) { frame ->
			when (val verdict = prepared.evaluator.observe(frame.index, frame.state)) {
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

					val stance = if (!frame.state.onGround && !movement.inMedium) null else holdingStanceOf(field.view, frame.state)
					if (stance == null) {
						false
					} else {
						val done = movement.completed(
							CompletionContext(
								decision = action,
								body = anchor,
								frameIndex = frame.index,
								observed = frame.state,
								stance = stance,
								airborne = airborne,
								launch = prepared.launch,
							)
						)

						val moving = frame.state.velocity.horizontalLength() > MotionConstraints.STOPPED_SPEED ||
								action.leavesGround || movement.inMedium

						val onLadderTop = frame.state.onGround && !movement.inMedium &&
								field.view.medium(stance.x, stance.y - 1, stance.z) == Medium.CLIMBABLE &&
								!field.isStance(stance)
						if (done && moving && stance != anchor.stance && !onLadderTop) {
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

	fun transition(anchor: ValueAnchor, action: TrajectoryDecision): Outcome {
		val prepared = prepare(anchor, action)
			?: return Outcome.Rejected(TrajectoryDiagnostic.NoStop(0, 0.0, anchor.speed))
		execute(prepared)
		return complete(prepared)
	}

	fun complete(prepared: PreparedRollout): Outcome {
		val anchor = prepared.anchor
		val action = prepared.action
		val rollout = checkNotNull(prepared.raw) { "complete() before execute()" }
		if (rollout.frames.isNotEmpty()) {
			val siblings = recentRollouts.getOrPut(anchor) { ArrayDeque(SIBLINGS_PER_ANCHOR) }
			if (siblings.size == SIBLINGS_PER_ANCHOR) siblings.removeFirst()
			siblings.addLast(rollout.frames)
		}
		val failure = prepared.failure
		val stopFrame = prepared.stopFrame
		val eventFrame = prepared.eventFrame
		var eventStance = prepared.eventStance

		attempts.record(rollout, stopFrame != null, route.goalPoint, failure)

		if (stopFrame != null) return Outcome.Arrived(rollout.frames)
		failure?.let { return Outcome.Rejected(it) }
		(rollout.termination as? TrajectoryRolloutTermination.Blocked)?.let {
			return Outcome.Blocked(it.frame, it.section)
		}
		val frame = eventFrame ?: return Outcome.Rejected(prepared.evaluator.terminalDiagnostic(rollout))

		val frames = rollout.frames

		var cornerCatch = 0
		if (!field.isStance(eventStance)) {
			val supported = supportedStanceOf(frames.last().state)
				?.takeIf { field.isStance(it) && holdsBody(field.view, it, frames.last().state) }
			if (supported == null) {
				return Outcome.Rejected(
					TrajectoryDiagnostic.FellBelowRoute(frame, 0.0)
				)
			}
			eventStance = supported
			cornerCatch = CORNER_CATCH_COLLISIONS
		}
		if (!field.isMapped(eventStance)) {
			return if (!field.view.isKnown(eventStance.x, eventStance.y - 1, eventStance.z)) {
				Outcome.Blocked(frame, PathingSection(eventStance.x shr 4, (eventStance.y - 1) shr 4, eventStance.z shr 4))
			} else {
				Outcome.Rejected(TrajectoryDiagnostic.FellBelowRoute(frame, 0.0))
			}
		}
		if (visits.visited(anchor, eventStance)) {
			return Outcome.Rejected(
				TrajectoryDiagnostic.RepeatedCoarseStance(frame, eventStance)
			)
		}

		return Outcome.Anchored(
			ValueAnchor(
				state = frames.last().state,
				stance = eventStance,
				elapsed = anchor.elapsed + frames.size,
				collisionEvents = anchor.collisionEvents + collisionEvents(anchor.state, frames) + cornerCatch,
				inputSwitches = anchor.inputSwitches + inputSwitches(anchor.inputs.lastOrNull(), frames),
				parent = anchor,
				inputs = frames.map { it.input },
				decision = action,
				trace = probe.traceOf(frames),
				jumps = anchor.jumps + groundJumps(anchor.state, frames),
			),
		)
	}

	private companion object {
		const val RECENT_ANCHORS = 64
		const val SIBLINGS_PER_ANCHOR = 32
		const val TRANSITION_FRAMES = 40
		const val OPEN_LANDING_NEIGHBORS = 2
		const val CORNER_CATCH_COLLISIONS = 2
	}
}
