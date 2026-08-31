package com.lambda.pathing.trajectory

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.TrajectoryDecision
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
)

/**
 * The live anchor tree: what the trajectory search is actually holding right now.
 *
 * The coarse graph has always been drawable and the trajectory search never was, which
 * left the expensive half of the planner invisible -- a session burning a hundred
 * thousand expansions looked exactly like one burning four hundred. Roles are what make
 * it readable: the spine is the tape the body is committed to, and everything else is
 * the search arguing about what should replace it.
 */
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
     *
     * [decision] stays for its cheap tally consumers; this hook exists so a harness can
     * attribute search work to coarse route edges -- which edge eats the attempts, and
     * whether its failures are one diagnostic repeated byte-identically (a grind) or
     * varied (a search still learning).
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

    /**
     * The body ran out of certified motion and executed into a published brake tail.
     *
     * This is the stall the walk shows as a dead stop, and it is expensive beyond the
     * pause: re-rooting onto the brake discards every frontier anchor, because the body
     * has now pressed inputs those anchors' tapes do not contain. [open] and [parked] are
     * what the search had available at that moment and [deepestElapsed] how far the best
     * of it reached, which together say whether the publication was impossible or merely
     * refused.
     */
    fun braked(tipElapsed: Int, executing: Int, open: Int, parked: Int, deepestElapsed: Int) {}

    /** One frontier poll, with the exact ordering values that won it: determinism forensics. */
    fun polled(stance: Stance, elapsed: Int, orderBits: Long, boundBits: Long, sequence: Long) {}

    /**
     * One finish-sweep attempt: whether the guide chain reached the goal at all, whether
     * a terminal run sealed, and how fast the body was when it tried. The finisher fails
     * silently otherwise, and the endgame stalls are exactly its silent failures.
     */
    fun finishAttempt(stance: Stance, elapsed: Int, speed: Double, chainReached: Boolean, sealed: Boolean) {}

    /**
     * The frontier genuinely drained and the search restarted from the tape's
     * continuation. [moving] distinguishes the tip restart (clean slate, body keeps
     * walking) from the brake restart (the body will halt). [drops] and [spent] say what
     * the drained frontier died of: drops are branches executed past their fork and
     * discarded at poll, spent are anchors that ground through their whole vocabulary.
     */
    fun restarted(moving: Boolean, seedElapsed: Int, executing: Int, expansions: Int, drops: Int, spent: Int) {}

    /**
     * A publication was refused while the body was inside the runway window -- the
     * frames before a potential stall. [reason] names the specific gate; the ledger's
     * standing instruction is to attribute refusals to gates before touching any of
     * them, because three fixes built on an unmeasured model of this all regressed.
     */
    fun publishRefused(reason: String, anchorElapsed: Int, tipElapsed: Int, executing: Int) {}

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
