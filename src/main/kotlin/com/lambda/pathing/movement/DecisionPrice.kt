package com.lambda.pathing.movement

/**
 * What a movement costs the search to attempt, over and above the coarse edge it serves.
 *
 * The coarse graph prices *where* the body goes. This prices *how*: two decisions that
 * cross the same edge can differ by fifty frames of run-up, or by whether the landing has
 * a hand's width of slack or none at all. Without that difference in the frontier order,
 * an anchor is expanded by list position before the search descends.
 *
 * The two components are not interchangeable:
 *
 * - [ticks] is real, spent time. A run-up that retreats four blocks costs those frames
 *   whether or not it works, and a detour through a suboptimal coarse step costs exactly
 *   the ticks the graph says it does. This lengthens the trajectory.
 * - [difficulty] is the chance of spending the time for nothing. It never lengthens a
 *   trajectory; it decides how far down the queue the attempt sits, and -- through
 *   [com.lambda.pathing.trajectory.Temperature] -- whether it is offered at all yet.
 */
data class DecisionPrice(
    /** Frames of setup beyond the coarse edge's own estimate. Never negative. */
    val ticks: Double = 0.0,

    /** 0 for a movement that always works, 1 at the edge of what the physics allows. */
    val difficulty: Double = 0.0,
) {
    init {
        require(ticks >= 0.0 && ticks.isFinite()) { "decision ticks must be finite and non-negative: $ticks" }
        require(difficulty in 0.0..1.0) { "difficulty is a 0..1 fraction: $difficulty" }
    }

    operator fun plus(other: DecisionPrice) = DecisionPrice(
        ticks = ticks + other.ticks,
        difficulty = maxOf(difficulty, other.difficulty),
    )

    companion object {
        val FREE = DecisionPrice()
    }
}

/** A decision together with what attempting it costs. */
class PricedDecision(val decision: TrajectoryDecision, val price: DecisionPrice)
