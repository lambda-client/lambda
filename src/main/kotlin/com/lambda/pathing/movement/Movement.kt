/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.movement

import com.lambda.pathing.coarse.CoarseEdge
import com.lambda.pathing.coarse.CoarseMoveCosts
import com.lambda.pathing.coarse.MotionTemplate
import com.lambda.pathing.coarse.MotionTemplateId
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.launch.BallisticProfile
import com.lambda.pathing.world.CoarseVoxelView
import com.lambda.util.player.prediction.MovementSimulationState

/**
 * What a movement is allowed to know about the body while proposing controls.
 *
 * Deliberately narrow. The search's own anchor carries parentage, costs, beam bookkeeping
 * and visit history, none of which a movement has any business reading -- and depending on
 * it would put the search back on the other side of this interface, which is the coupling
 * the whole package exists to remove.
 */
interface BodyState {
    val state: MovementSimulationState

    val stance: Stance

    /** Frame a walk was seen to fail at, when one has been observed. Null before then. */
    val hazardFrame: Int?

    val speed: Double get() = state.velocity.horizontalLength()

    /** Current direction of travel, or null when the body is not going anywhere. */
    fun heading(): Pair<Double, Double>? =
        if (speed <= HEADING_EPSILON) null else state.velocity.x to state.velocity.z

    private companion object {
        const val HEADING_EPSILON = 1e-6
    }
}

/**
 * One template a movement offers, before the graph has numbered it.
 *
 * Ids are assigned centrally by the catalogue, so a provider never has to know what else
 * is registered -- which is what lets movements be added without renumbering anything.
 */
data class TemplateSpec(
    val dx: Int,
    val dy: Int,
    val dz: Int,
    val movement: MovementId,
    val cost: Double,
    val conditions: List<CellCondition>,
    val arc: MotionTemplate.ArcSpec? = null,
    /**
     * What this move costs when the real rise turns out to be inside a single stride.
     *
     * A one-cell step up onto a slab is half a block, which vanilla lifts the body over
     * during ordinary collision resolution -- it is a stride, not a step-up, and pricing it
     * as one made the search jump *over* slab terrain rather than walk up it. Null for
     * moves where the distinction cannot arise.
     */
    val strideCost: Double? = null,
) {
    internal fun toTemplate(id: MotionTemplateId) =
        MotionTemplate(id, dx, dy, dz, movement, cost, conditions, arc, strideCost)
}

/** What the graph is being built with, when a movement is asked for its templates. */
class MovementContext(
    val options: SimpleMoveOptions,
    val costs: CoarseMoveCosts,
    val ballistics: BallisticProfile = BallisticProfile.VANILLA,
)

/**
 * One candidate step the search is considering, and the body considering it.
 *
 * [view] is here because some controls cannot be derived from the edge. A climb is the
 * clear case: its delta is purely vertical, so the direction of travel says nothing about
 * which way the body must face, and facing the wrong way walks it off the ladder. The
 * answer is in the terrain -- which side the climbable is hung on -- and nowhere else.
 */
class DecisionContext(
    val body: BodyState,
    val edge: CoarseEdge,
    val constraints: MotionConstraints,
    val view: CoarseVoxelView,
    val ballistics: BallisticProfile = BallisticProfile.VANILLA,
)

/** Everything a control program needs to be built. */
class ProgramContext(
    val decision: TrajectoryDecision,
    val body: BodyState,
    /** The steering chain the search descended, starting at the body's stance. */
    val nodes: List<HorizontalPoint>,
    val constraints: MotionConstraints,
    val launch: LaunchTrigger?,
)

/** One simulated frame, asked whether the movement it belongs to is finished. */
class CompletionContext(
    val decision: TrajectoryDecision,
    val body: BodyState,
    val frameIndex: Int,
    val observed: MovementSimulationState,
    /** Where the body is now, in graph terms. */
    val stance: Stance,
    /** Whether the body has left the ground at any point during this transition. */
    val airborne: Boolean,
    val launch: LaunchTrigger?,
    val headingCommitFrames: Int,
)

/**
 * One way of getting from one stance to another.
 *
 * A movement owns its whole vertical slice, and that is the point. Adding swimming, diving
 * or riding used to mean editing the cell record, the template builder, a closed move-kind
 * enum, the decision vocabulary, the program selector, the completion test and the route
 * validator. Now it means writing one of these.
 *
 * The four hooks correspond to the four questions the two layers actually ask:
 *
 * - [templates] -- what edges does this contribute to the graph, and what do they cost?
 * - [decisions] -- given a body and one of those edges, what controls are worth simulating?
 * - [program] -- turn a decision into per-tick input.
 * - [completed] -- when has this transition finished?
 *
 * [completed] earns its place. The search's built-in rule was "grounded, moving, and the
 * stance changed", which is wrong for a drop (it changes stance while still walking on the
 * take-off block), wrong for a swimmer (never grounded) and wrong for a climber (no
 * horizontal stance change at all).
 */
interface Movement {
    val id: MovementId

    fun templates(context: MovementContext): List<TemplateSpec>

    fun decisions(context: DecisionContext): List<TrajectoryDecision>

    /**
     * Whether this movement wants a say on an edge some other movement produced.
     *
     * The graph decides an edge is walkable from geometry, but geometry is a mask and the
     * body still sometimes has to leave the ground on one -- a lip the mask called
     * passable, a shape the walk catches on. Rather than build that exception into the
     * search, a movement volunteers: jumping offers a hop on any step that does not
     * descend, and nothing else has an opinion.
     */
    fun offersFor(edge: CoarseEdge): Boolean = false

    /**
     * Whether a body using this movement can be at [stance] at all.
     *
     * The graph only enumerates edges out of cells it considers occupiable, and that test
     * used to be hardcoded to the walking one: solid floor, clear body, clear head. A
     * climber has no floor and a swimmer has no head clearance in the sense meant here, so
     * under that rule neither could exist anywhere -- their edges were never even asked
     * for. Movements that live on the ground can leave this alone.
     */
    fun occupies(view: CoarseVoxelView, stance: Stance): Boolean = false

    /**
     * Whether a transition of this movement can finish while the body is off the ground.
     *
     * The search asks [completed] only on grounded frames, which is right for everything
     * that walks: a walk, a hop and a drop all finish by touching down, and consulting them
     * mid-flight would anchor the body in mid-air over a stance it has not reached. A
     * climber never touches down at all, so under that rule its own [completed] was never
     * reached and a ladder route could be planned but never flown.
     *
     * Movements that leave this alone keep the grounded rule exactly as it was.
     */
    val completesAirborne: Boolean get() = false

    /**
     * Whether pressing into terrain is how this movement moves.
     *
     * A walk that touches a wall has failed -- it has run into something and stopped
     * getting anywhere, and refusing it there is what keeps the search off geometry the
     * grid called passable. A climb is the exact inverse: vanilla only re-asserts a
     * climbing body's rise while it is horizontally colliding, so contact with the wall is
     * not the failure, it *is* the ascent. Under the blanket rule every rung of every
     * ladder was rejected on the frame it first worked.
     */
    val pressesIntoTerrain: Boolean get() = false

    /**
     * How far below its own route nodes this movement legitimately descends, in blocks.
     *
     * The evaluator kills a rollout that sinks below the lowest node it was given, and for
     * every movement so far that has been exactly right: a walk, a jump or a drop that ends
     * up under its own path has fallen off something. A bounce inverts it. Diving below both
     * endpoints is not a failure of the move, it *is* the move -- the fall is what supplies
     * the impulse -- so under the blanket rule every bounce was rejected on the frame it
     * started working, which is the same shape of mistake [pressesIntoTerrain] exists to fix
     * for a climb.
     *
     * Asked of the decision rather than the movement because the depth is solved per edge:
     * a six-block pit and a three-block one are the same movement.
     */
    fun descentAllowance(decision: TrajectoryDecision): Double = 0.0

    /**
     * Frames this movement's transition needs, when the search's own budget is too short.
     *
     * The budget exists to stop a rollout wandering, and for a walk or a jump it is generous
     * -- a jump is a dozen ticks. A bounce is a fall, a reflection and a long flight, thirty
     * to forty ticks before the body is anywhere, so the default cuts it off mid-air and the
     * transition is discarded for never having finished rather than for having failed.
     *
     * Returning zero keeps the search's budget, which is what every other movement wants.
     */
    fun transitionFrames(decision: TrajectoryDecision): Int = 0

    fun program(context: ProgramContext): ControlProgram

    fun completed(context: CompletionContext): Boolean
}
