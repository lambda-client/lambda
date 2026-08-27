/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.config.blocks

import com.lambda.config.Config
import com.lambda.config.ConfigBlock
import com.lambda.config.Group

class PathingSettings(override val c: Config) : PathingConfig, ConfigBlock {
    @Group(MOVES_GROUP)
    override val allowDiagonal by c.setting("Diagonal Moves", true, "Allow 45-degree coarse edges.")

    @Group(MOVES_GROUP)
    override val allowStepUp by c.setting("Step Up", true, "Allow one-block rises. They are jumped, not walked.")

    @Group(MOVES_GROUP)
    override val allowSprint by c.setting("Allow Sprint", true, "Let the seed search try sprinting gaits.")

    @Group(MOVES_GROUP)
    override val maxWalkOffDepth by c.setting(
        "Max Walk Off Depth", 3, 0..8, 1,
        "Deepest drop the coarse layer may propose. The trajectory layer still refuses landings that would hurt.",
        unit = " blocks",
    )

    @Group(MOVES_GROUP)
    override val maxDropSpan by c.setting(
        "Max Drop Span", 2, 1..4, 1,
        "How far a controlled drop may carry the body sideways while descending. " +
            "A span of 1 is straight down onto the neighbouring block; 2 crosses a gap on the way down.",
        unit = " blocks",
    )

    @Group(MOVES_GROUP)
    override val allowClimbing by c.setting(
        "Ladders", false,
        "Route up and down ladders and vines. Off by default because climb edges are cheap " +
            "per block, which weakens the search's distance estimate everywhere -- including " +
            "routes with no ladder anywhere near them.",
    )

    @Group(MOVES_GROUP)
    override val allowJumpCandidates by c.setting(
        "Gap Jumps", true,
        "Let the coarse layer propose gap jumps. Each still needs a simulated launch before it is walked.",
    )

    @Group(MOVES_GROUP)
    override val maxJumpSpan by c.setting(
        "Max Jump Span", 4, 2..5, 1,
        "Per-axis cap on candidate jumps. Reach itself is bounded by what a standing " +
            "start provably clears; each candidate still needs a simulated launch.",
        unit = " blocks",
    ) { allowJumpCandidates }

    @Group(MOVES_GROUP)
    override val maxJumpDrop by c.setting(
        "Max Jump Drop", 2, 0..3, 1,
        "Deepest lower landing a gap jump may target. Lets the coarse layer take a straight " +
            "descending parkour line instead of weaving around drops. Unsafe arcs are still " +
            "refused by simulation.",
        unit = " blocks",
    ) { allowJumpCandidates }

    @Group(MOVES_GROUP)
    override val allowOffAxisJumps by c.setting(
        "Off-Axis Jumps", true,
        "Offer jumps that land off the eight compass directions -- three across and one to " +
            "the side, which has no cardinal or diagonal template. Roughly doubles the " +
            "graph's fan-out on open ground.",
    ) { allowJumpCandidates }

    @Group(MOVES_GROUP)
    override val allowSlimeBounces by c.setting(
        "Slime Bounces", false,
        "Cross gaps by falling onto slime and riding the rebound. Reaches ground no jump " +
            "does, because the fall supplies the impulse. Off by default: the arcs are around " +
            "thirty ticks long, so every one the search tries is expensive, and terrain that " +
            "rewards them is rare.",
    )

    @Group(MOVES_GROUP)
    override val maxBounceDrop by c.setting(
        "Max Bounce Drop", 8, 3..12, 1,
        "Deepest fall onto slime a bounce may be planned around. Deeper falls reach further " +
            "and need less run-up, at the cost of a longer arc to simulate.",
        unit = " blocks",
    ) { allowSlimeBounces }

    @Group(SEARCH_GROUP)
    override val planningHorizonChunks by c.setting(
        "Planning Horizon", 4, 0..16, 1,
        "How far around the body the coarse planner searches before it starts walking. " +
            "Terrain past the ring is planned while moving, the way unstreamed chunks " +
            "already are. 0 plans the whole loaded world up front, which at full render " +
            "distance can take tens of seconds before the first step.",
        unit = " chunks",
    )

    @Group(SEARCH_GROUP)
    override val maxFrames by c.setting(
        "Max Frames", 160, 40..600, 10,
        "Longest tape the seed search may certify.", unit = " ticks",
    )

    @Group(SEARCH_GROUP)
    override val snapshotCaptureBudgetMillis by c.setting(
        "Snapshot Tick Budget", 3.0, 0.25..10.0, 0.25,
        "Maximum client-thread time used to copy world physics per tick. Larger values " +
            "finish planning sooner but can make rendering less smooth.",
        unit = " ms",
    )

    @Group(SEARCH_GROUP)
    override val goalRadius by c.setting(
        "Goal Radius", 0.20, 0.05..1.0, 0.01,
        "How close to the goal centre the walk must stop.", unit = " blocks",
    )

    @Group(SEARCH_GROUP)
    override val horizonRunwayFrames by c.setting(
        "Horizon Runway", 20, 5..300, 5,
        "Committed motion kept ahead of the body. Smaller leaves decisions later, so the " +
            "search has longer to improve them; too small and the body catches its brake.",
        unit = " frames",
    )

    @Group(SEARCH_GROUP)
    override val horizonCommitFrames by c.setting(
        "Horizon Commit", 20, 5..120, 5,
        "How much motion each step commits. Smaller keeps more of the walk open to be " +
            "replanned; too small and the search cannot keep ahead of the body.",
        unit = " frames",
    )

    @Group(MOVES_GROUP)
    override val maxSafeFallDistance by c.setting(
        "Max Safe Fall", 3.0, 1.0..30.0, 0.5,
        "Deepest fall certified as survivable on ordinary ground. Above ~3 blocks the " +
            "landing costs health; raise it only when that trade is intended.",
        unit = " blocks",
    )

    @Group(SEARCH_GROUP)
    override val coarseExpansionBudget by c.setting(
        "Coarse Budget", 1_000_000, 10_000..5_000_000, 10_000,
        "Hard cap on coarse graph expansions per repair before the route is refused.",
        unit = " nodes",
    )

    @Group(SEARCH_GROUP)
    override val trajectoryExpansionBudget by c.setting(
        "Trajectory Budget", 2_000_000, 10_000..5_000_000, 10_000,
        "Hard cap on trajectory-search expansions per leg before the search gives up.",
        unit = " nodes",
    )

    @Group(SEARCH_GROUP)
    override val frontierProbeRange by c.setting(
        "Frontier Probe Range", 512, 64..2048, 32,
        "Furthest the frontier probe marches toward an unstreamed goal. Must reach past " +
            "the render distance for goals in unloaded terrain.",
        unit = " blocks",
    )

    @Group(SEARCH_GROUP)
    override val frontierSweepBudget by c.setting(
        "Frontier Sweep Budget", 40_000, 1_000..200_000, 1_000,
        "Node budget of the last-resort reachability sweep used when every probe ray " +
            "lands somewhere the body cannot reach.",
        unit = " nodes",
    )

    @Group(SEARCH_GROUP)
    override val bootstrapDelayMillis by c.setting(
        "Bootstrap Delay", 200, 0..2000, 25,
        "Minimum wall time before the first partial tape is published. Longer waits " +
            "publish better-optimized openings; shorter starts walking sooner.",
        unit = " ms",
    )

    @Group(SEARCH_GROUP)
    override val plannerThreads by c.setting(
        "Planner Threads", 4, 1..12, 1,
        "Rollout workers for the trajectory search. The rollout kernel scales near-" +
            "linearly to the physical core count; extra throughput buys deeper " +
            "refinement of the tape ahead of the body. 1 runs the exact serial search.",
        unit = " threads",
    )

    @Group(SEARCH_GROUP)
    override val captureRetries by c.setting(
        "Capture Retries", 4, 0..16, 1,
        "Retries while the exact world capture catches up to a body standing at the " +
            "streamed frontier before the walk is failed.",
    )

    @Group(SEARCH_GROUP)
    override val captureRetryMillis by c.setting(
        "Capture Retry Wait", 400, 50..2000, 50,
        "Wait between those capture retries.", unit = " ms",
    ) { captureRetries > 0 }

    @Group(SEARCH_GROUP)
    override val dumpFailedPlans by c.setting(
        "Dump Failed Plans", false,
        "On a refusal, write the captured world, endpoints and entry state to " +
            "neolambda/pathing-dumps so the failure can be replayed and fixed offline.",
    )

    private companion object {
        const val MOVES_GROUP = "Moves"
        const val SEARCH_GROUP = "Search"
    }
}
