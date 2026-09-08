package com.lambda.pathing.search

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.actions.TrajectoryDecision
import net.minecraft.util.math.Vec3d

class CandidatePath(val points: List<Vec3d>, val best: Boolean)

/**
 * What a live anchor is to the search, strongest claim first.
 *
 * An anchor is claimed by every role it qualifies for; the lowest ordinal wins, so the
 * committed line reads as spine even though its anchors are also open, and an ancestor
 * nothing claims reads as interior scaffolding.
 */
enum class SearchNodeRole { SPINE, BEST, OPEN, PARKED, SPENT, INTERIOR }

class SearchTreeNode(
    val position: Vec3d,
    val elapsed: Int,
    val guide: Double,
    val role: SearchNodeRole,
)

class SearchTreeEdge(
    val from: Vec3d,
    val to: Vec3d,
    val role: SearchNodeRole,
    val via: MovementId?,
    /** Body positions between [from] and [to], so the edge is drawn as the rollout ran, not as a chord. */
    val trace: List<Vec3d> = emptyList(),
)

/** The live anchor tree, sampled for drawing: the spine is the committed tape, the rest is the search. */
class SearchTreeView(
    val nodes: List<SearchTreeNode>,
    val edges: List<SearchTreeEdge>,
    val totalAnchors: Int,
    val truncated: Boolean,
)

/** The counters behind [SearchExhaustion], sampled while the search is still running. */
class SearchStatsView(
    val expansions: Int,
    val windowExpansions: Int,
    val windowBudget: Int,
    val guideExpansions: Int,
    val temperature: Double,
    val open: Int,
    val parked: Int,
    val blocked: Int,
    val spent: Int,
    val admitted: Int,
    val merged: Int,
    val restarts: Int,
    val rootFrame: Int,
    val publishedFrame: Int,
    val horizonEnd: Int,
    val cursorFrame: Int,
    val bestScore: Int?,
    val elapsedMillis: Long,
)

interface SearchProbe {
    val candidatesEnabled: Boolean get() = false

    /** Whether anyone is drawing the anchor tree. Building it walks every live anchor. */
    val treeEnabled: Boolean get() = false

    fun tree(view: SearchTreeView) {}

    fun stats(view: SearchStatsView) {}

    fun decision(action: TrajectoryDecision, rejected: Boolean, frame: Int) {}

    /**
     * One expansion with its full identity: the stance it grew from, the decision tried,
     * and the rejection it produced (null when the rollout anchored, arrived, or blocked).
     * Lets a harness attribute search work to coarse route edges; [decision] is the cheap tally.
     */
    fun expansion(from: Stance, action: TrajectoryDecision, diagnostic: TrajectoryDiagnostic?) {}

    fun attempt(rollout: TrajectoryRollout, certified: Boolean, diagnostic: TrajectoryDiagnostic?) {}

    fun blocked(
        frame: Int,
        sectionX: Int,
        sectionY: Int,
        sectionZ: Int,
        capturable: Boolean,
        stance: Stance,
        movement: MovementId,
    ) {}

    fun sync(sections: Int, mutations: Int, chunks: Int, routeAffected: Boolean, extending: Boolean) {}

    /** A polled anchor discarded before any rollout: fork-dropped, starved, parked, skipped (incumbent), or out of actions. */
    fun discarded(stance: Stance, elapsed: Int, reason: String, forkLife: Int) {}

    /** One frontier admission and how it ended: enqueued, unmapped, unreachable, dominated, capped, pruned. */
    fun admission(stance: Stance, elapsed: Int, outcome: String) {}

    /** After a tape restart re-rooted on [seed]: what the frontier holds and what the field says of the seed. */
    fun restartRooted(seed: Stance, elapsed: Int, guide: Double, open: Int, parked: Int) {}

    /** The search re-targeted onto the next leg of a compound route at [waypoint]. */
    fun legSwitched(waypoint: Stance, elapsed: Int, expansions: Int) {}

    /**
     * The body executed into a published brake tail (a dead stop; re-rooting onto the brake
     * discards every frontier anchor). [open], [parked] and [deepestElapsed] say whether a
     * publication was impossible or merely refused.
     */
    fun braked(tipElapsed: Int, executing: Int, open: Int, parked: Int, deepestElapsed: Int) {}

    /** One frontier poll, with the exact ordering values that won it: determinism forensics. */
    fun polled(stance: Stance, elapsed: Int, orderBits: Long, boundBits: Long, sequence: Long) {}

    /**
     * One finish-sweep attempt: whether the guide chain reached the goal, whether a
     * terminal run sealed, and the body's speed when it tried.
     */
    fun finishAttempt(stance: Stance, elapsed: Int, speed: Double, chainReached: Boolean, sealed: Boolean) {}

    /**
     * The frontier drained and the search restarted from the tape's continuation. [moving]:
     * tip restart (body keeps walking) versus brake restart (body will halt). [drops] are
     * branches executed past their fork, [spent] anchors that exhausted their vocabulary.
     */
    fun restarted(moving: Boolean, seedElapsed: Int, executing: Int, expansions: Int, drops: Int, spent: Int) {}

    /**
     * A publication was refused while the body was inside the runway window. [reason] names
     * the gate, built only by probes that read it; attribute refusals to gates before
     * changing any (docs/decisions/publication-protocol.md).
     */
    fun publishRefused(reason: () -> String, anchorElapsed: Int, tipElapsed: Int, executing: Int) {}

    fun candidates(lines: List<CandidatePath>) {}

    /**
     * The constructive spine pass finished. [reachedElapsed] is how many certified
     * frames it chained, [rollouts] what it spent, [stalledAt] the stance it could not
     * advance past when it stopped short of seeding a full solution (null when the
     * finisher sealed the course), and [diagnostic] the last rejection at that stance.
     */
    fun spine(reachedElapsed: Int, rollouts: Int, stalledAt: Stance?, diagnostic: TrajectoryDiagnostic?) {}

    companion object {
        val NONE: SearchProbe = object : SearchProbe {}
    }
}
