package com.lambda.pathing.trajectory

import com.lambda.pathing.core.MovementId
import com.lambda.pathing.core.Stance
import com.lambda.pathing.movement.TrajectoryDecision
import net.minecraft.util.math.Vec3d

class CandidatePath(val points: List<Vec3d>, val best: Boolean)

interface SearchProbe {
    val candidatesEnabled: Boolean get() = false

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
