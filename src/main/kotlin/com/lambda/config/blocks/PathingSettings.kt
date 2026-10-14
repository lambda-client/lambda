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

    @Group(SEARCH_GROUP)
    override val frontierTicksPerBlock by c.setting(
        "Frontier Directness", com.lambda.pathing.actions.SimpleMoveOptions.DEFAULT_FRONTIER_TICKS_PER_BLOCK, 3.0..30.0, 0.5,
        "Ticks a far goal charges per block of unloaded terrain. Low values follow cheap known ground (coasts, ridges) " +
            "sideways; high values head straight for the edge of the loaded world toward the goal, swimming if needed.",
        unit = " ticks",
    )

    @Group(MOVES_GROUP)
    override val saveFood by c.setting(
        "Save Food", false,
        "Spend as little hunger as possible: never sprint, and only jump where a jump saves more than a second over walking.",
    )

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
    override val allowSwimming by c.setting(
        "Swimming", true,
        "Swim across water at the surface, walking or dropping in from the shore and climbing " +
            "out onto banks one block high.",
    )

    @Group(MOVES_GROUP)
    override val allowDiving by c.setting(
        "Diving", true,
        "Swim under water through flooded passages, caves and shafts, never further than a breath " +
            "from air. Rising bubble columns are ridden as lifts; sinking ones are avoided.",
    ) { allowSwimming }

    @Group(MOVES_GROUP)
    override val allowJumpCandidates by c.setting(
        "Gap Jumps", true,
        "Let the coarse layer propose gap jumps. Each still needs a simulated launch before it is walked.",
    )

    @Group(MOVES_GROUP)
    override val maxJumpSpan by c.setting(
        "Max Jump Span", 5, 2..5, 1,
        "Per-axis cap on candidate jumps. Reach itself is bounded by what a standing " +
            "start provably clears -- descending jumps reach further, up to a four-cell " +
            "air gap with half a block of real drop; each candidate still needs a " +
            "simulated launch.",
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
            "the side, which has no cardinal or diagonal template -- and slime bounces at any " +
            "heading. Roughly doubles the graph's fan-out on open ground.",
    ) { allowJumpCandidates }

    @Group(MOVES_GROUP)
    override val allowDeepDropJumps by c.setting(
        "Deep Drop Jumps", true,
        "Offer descending jumps beyond the flat standing reach: a four-cell air gap " +
            "with half a block of real drop, wide diagonals with a full block and a straight " +
            "five-cell gap with a block and three quarters, taken only with momentum and only where " +
            "nothing easier leads on (solver-measured). Route lengths are unchanged on ordinary " +
            "terrain; turn it off only to keep the planner to flat-reach jumps.",
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
        "Max Bounce Drop", 8, 3..24, 1,
        "Deepest fall onto slime a bounce may be planned around. Deeper falls reach further " +
            "and need less run-up, at the cost of a longer arc to simulate.",
        unit = " blocks",
    ) { allowSlimeBounces }

    @Group(MOVES_GROUP)
    override val allowDeepFalls by c.setting(
        "Deep Falls", true,
        "Fall from any height into water, onto hay bales and beds, or onto any floor Max Safe Fall " +
            "accepts, steering toward the landing on the way down.",
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
    override val touchArrival by c.setting(
        "Arrive On Touch", true,
        "Entering the goal block is arriving: the walk brakes wherever that landing rests. " +
            "Off, it must come to rest inside Goal Radius, and a fast landing has to circle back.",
    )

    @Group(SEARCH_GROUP)
    override val waypointArrival by c.setting(
        "Waypoint Arrival", com.lambda.pathing.core.ArrivalMode.WALK_THROUGH,
        "Walk Through: a route's intermediate waypoints are checkpoints passed at speed, one " +
            "continuous tape. Stand Still: the body rests at each waypoint before the next leg.",
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
    override val maxFallDamage by c.setting(
        "Max Fall Damage", 0.0, 0.0..10.0, 0.5,
        "Fall damage the planner may take on one landing. At 0 it never gets hurt; falls the player " +
            "takes unhurt are always used: into water of any depth, onto hay bales and beds within " +
            "their cushion, and within its safe fall distance, fall damage multiplier, flight or " +
            "Slow Falling. Half a heart opens drops like nine blocks onto hay. A damaging landing " +
            "makes the server correct the velocity, so the walk replans after it.",
        unit = " hearts",
    )

    @Group(MOVES_GROUP)
    override val ignoreHazards by c.setting(
        "Ignore Hazards", false,
        "Plan through fire, lit campfires and cacti as if they were harmless: walk through flames " +
            "and stand on or brush past cacti. Every hit hurts and makes the server correct the " +
            "velocity, so the walk replans after it. Off, those cells are kept out of entirely.",
    )

    @Group(MOVES_GROUP)
    override val avoidLavaEdges by c.setting(
        "Avoid Lava Edges", false,
        "Never land from a jump or a fall with any part of the feet over lava, nor a low hop with the centre over it. " +
            "Vanilla allows it, but parkour servers reset players who land on a lava edge; walking past lava stays allowed.",
    )

    @Group(MOVES_GROUP)
    override val avoidMobs by c.setting(
        "Avoid Mobs", true,
        "Plan around nearby mobs, animals and other players instead of through them: the cells they " +
            "stand in stay closed for three seconds after they leave, so a walk is not pushed off its " +
            "tape. Off, entities are ignored and every push makes the walk replan.",
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
    override val useBaritoneCache by c.setting(
        "Baritone Cache", true,
        "Plan beyond loaded chunks through the terrain Baritone's cache remembers. Read only; " +
            "every loaded chunk replaces what the cache remembered of it.",
    )

    @Group(SEARCH_GROUP)
    override val dumpFailedPlans by c.setting(
        "Dump Failed Plans", false,
        "On a refusal, replan, hold, stop or cancel, write the endpoints, entry state and the executed tape to " +
            "lambda/pathing-dumps and the captured terrain to lambda/cache/terrain, so the failure can be replayed and fixed offline.",
    )

    @Group(SEARCH_GROUP)
    override val dumpAllPlans by c.setting(
        "Dump All Plans", false,
        "Write the endpoints and entry state of every planned leg to lambda/pathing-dumps " +
            "and the captured terrain to lambda/cache/terrain, successes included, so a real " +
            "course can be replayed and improved offline.",
    )

    private companion object {
        const val MOVES_GROUP = "Moves"
        const val SEARCH_GROUP = "Search"
    }
}
