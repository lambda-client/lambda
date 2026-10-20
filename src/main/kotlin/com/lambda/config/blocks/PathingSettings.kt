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
import com.lambda.pathing.actions.MotionConstraints
import com.lambda.pathing.actions.SimpleMoveOptions
import com.lambda.pathing.core.ArrivalMode
import com.lambda.pathing.physics.Vanilla
import com.lambda.pathing.search.SearchConfig

class PathingSettings(override val c: Config) : PathingConfig, ConfigBlock {
    @Group(MOVES_GROUP)
    override val allowSprint by c.setting("Allow Sprint", true, "Sprint wherever it is faster. Off, the walk never sprints.")

    @Group(MOVES_GROUP)
    override val saveFood by c.setting(
        "Save Food", false,
        "Spend as little hunger as possible: never sprint, and only jump where a jump saves more than a second over walking.",
    )

    @Group(MOVES_GROUP)
    override val allowClimbing by c.setting("Ladders", true, "Climb ladders and vines, and catch them from jumps.")

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
    override val unlimitedAir by c.setting(
        "Unlimited Air", false,
        "Never plan for air: no breathing stops at air pockets or the surface, dives as long as the route needs. " +
            "For servers or modes where the player cannot drown.",
    ) { allowSwimming && allowDiving }

    @Group(MOVES_GROUP)
    override val diveSeconds by c.setting(
        "Dive Time", (Vanilla.MAX_AIR - SimpleMoveOptions.DEFAULT_AIR_RESERVE).toDouble() / Vanilla.TICKS_PER_SECOND, 1.0..15.0, 0.5,
        "Longest stretch under water per breath: the walk breathes at an air pocket or the surface before it has " +
            "spent this much of its 15 s of air.",
        unit = " s",
    ) { allowSwimming && allowDiving && !unlimitedAir }

    @Group(MOVES_GROUP)
    override val maxJumpSpan by c.setting(
        "Max Jump Span", SimpleMoveOptions.TUNED_JUMP_SPAN, 2..SimpleMoveOptions.FULL_JUMP_SPAN, 1,
        "Longest jump considered, in blocks along each axis. Up to 5 the walk takes the jumps it lands reliably from a stand " +
            "or a short run-up; above 5 it also considers every longer jump a full sprint can make, at the cost of planning time.",
        unit = " blocks",
    )

    @Group(MOVES_GROUP)
    override val allowNeoJumps by c.setting(
        "Neo Jumps", true,
        "Jump around the side of a wall that blocks a straight jump: run out past the wall's edge, " +
            "jump beside it and steer back around it in the air. Needs sprinting and a turn speed of at least 90 degrees a tick.",
    )

    @Group(MOVES_GROUP)
    override val allowSlimeBounces by c.setting(
        "Slime Bounces", true,
        "Cross gaps by falling onto slime and riding the rebound. Reaches ground no jump does; " +
            "it only costs planning time where slime is near the route.",
    )

    @Group(MOVES_GROUP)
    override val maxBounceDrop by c.setting(
        "Max Bounce Drop", 8, 3..24, 1,
        "Deepest fall onto slime a bounce may be planned around. Deeper falls reach further " +
            "and need less run-up, at the cost of a longer arc to simulate.",
        unit = " blocks",
    ) { allowSlimeBounces }

    @Group(HAZARDS_GROUP)
    override val avoidFire by c.setting(
        "Avoid Fire", true,
        "Keep out of fire, soul fire and lit campfires. Off, the walk passes through them and burns, " +
            "and the server's knockback makes it replan.",
    )

    @Group(HAZARDS_GROUP)
    override val avoidCacti by c.setting(
        "Avoid Cacti", true,
        "Keep clear of cacti. Off, the walk stands on and brushes past them, and every prick makes it replan.",
    )

    @Group(HAZARDS_GROUP)
    override val sneakOnMagma by c.setting(
        "Sneak On Magma", true,
        "Hold sneak while standing on magma blocks, from the tick before landing on one, so they never burn. " +
            "Off, magma is walked like any floor.",
    )

    @Group(HAZARDS_GROUP)
    override val avoidLavaEdges by c.setting(
        "Avoid Lava Edges", false,
        "Never land from a jump or a fall with any part of the feet over lava, nor a low hop with the centre over it. " +
            "Vanilla allows it, but parkour servers reset players who land on a lava edge; walking past lava stays allowed.",
    )

    @Group(HAZARDS_GROUP)
    override val avoidMobs by c.setting(
        "Avoid Mobs", true,
        "Plan the trajectory around nearby mobs, animals and other players, and through them when they block the " +
            "only way. Pushes are absorbed by replaying the tape from the pushed body either way.",
    )

    @Group(HAZARDS_GROUP)
    override val ignoreFallDamage by c.setting(
        "Ignore Fall Damage", false,
        "Take any fall however much it costs, even one the player would not survive. For servers or modes without fall damage.",
    )

    @Group(HAZARDS_GROUP)
    override val fallDamageLimit by c.setting(
        "Fall Damage Limit", 0.0, 0.0..10.0, 0.5,
        "Health one landing may cost. At 0 the walk only takes falls the player survives unhurt: into water, onto hay " +
            "bales, beds and slime, and within its safe fall distance. Half a heart opens drops like nine blocks onto hay; " +
            "a damaging landing makes the server correct the velocity, so the walk replans after it.",
        unit = " hearts",
    ) { !ignoreFallDamage }

    @Group(ARRIVAL_GROUP)
    override val goalRadius by c.setting(
        "Goal Radius", 0.20, 0.05..1.0, 0.01,
        "How close to the goal centre the walk must stop.", unit = " blocks",
    )

    @Group(ARRIVAL_GROUP)
    override val touchArrival by c.setting(
        "Arrive On Touch", true,
        "Entering the goal block is arriving: the walk brakes wherever that landing rests. " +
            "Off, it must come to rest inside Goal Radius, and a fast landing has to circle back.",
    )

    @Group(ARRIVAL_GROUP)
    override val touchRestRadius by c.setting(
        "Touch Rest Radius", MotionConstraints.DEFAULT_TOUCH_REST_RADIUS, 0.5..3.0, 0.1,
        "How far from the goal centre the body may come to rest after touching the goal block.",
        unit = " blocks",
    ) { touchArrival }

    @Group(ARRIVAL_GROUP)
    override val waypointArrival by c.setting(
        "Waypoint Arrival", ArrivalMode.WALK_THROUGH,
        "Walk Through: a route's intermediate waypoints are checkpoints passed at speed, one " +
            "continuous tape. Stand Still: the body rests at each waypoint before the next leg.",
    )

    @Group(SEARCH_GROUP)
    override val frontierTicksPerBlock by c.setting(
        "Frontier Directness", SimpleMoveOptions.DEFAULT_FRONTIER_TICKS_PER_BLOCK, 4.0..30.0, 0.5,
        "Ticks a far goal charges per block of unloaded terrain. Low values follow cheap known ground (coasts, ridges) " +
            "sideways; high values head straight for the edge of the loaded world toward the goal, swimming if needed.",
        unit = " ticks",
    )

    @Group(SEARCH_GROUP)
    override val horizonRunwayFrames by c.setting(
        "Horizon Runway", SearchConfig.RUNWAY_FRAMES, 5..300, 5,
        "Committed motion kept ahead of the body. Smaller leaves decisions later, so the " +
            "search has longer to improve them; too small and the body catches its brake.",
        unit = " frames",
    )

    @Group(SEARCH_GROUP)
    override val horizonCommitFrames by c.setting(
        "Horizon Commit", SearchConfig.COMMIT_FRAMES, 5..120, 5,
        "How much motion each step commits. Smaller keeps more of the walk open to be " +
            "replanned; too small and the search cannot keep ahead of the body.",
        unit = " frames",
    )

    @Group(SEARCH_GROUP)
    override val coarseExpansionBudget by c.setting(
        "Coarse Budget", 1_000_000, 10_000..5_000_000, 10_000,
        "Hard cap on coarse graph expansions per repair before the route is refused.",
        unit = " nodes",
    )

    @Group(SEARCH_GROUP)
    override val liveImprover by c.setting(
        "Live Improver", true,
        "Once a walk has a complete tape to the goal, keep improving the part not walked yet while walking, " +
            "the way Path Plan refines before it walks. Swaps in a faster tape whenever one certifies.",
    )

    @Group(SEARCH_GROUP)
    override val windowExpansionBudget by c.setting(
        "Window Budget", SearchConfig.WINDOW_EXPANSIONS, 10_000..1_000_000, 10_000,
        "Trajectory-search expansions per planning window before the search commits its best tape. " +
            "Higher finds better tapes on hard terrain but reacts slower.",
        unit = " nodes",
    )

    @Group(SEARCH_GROUP)
    override val bootstrapDelayMillis by c.setting(
        "Bootstrap Delay", SearchConfig.BOOTSTRAP_DELAY_MILLIS.toInt(), 0..2000, 25,
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

    @Group(WORLD_GROUP)
    override val searchDistance by c.setting(
        "Search Distance", 8, 2..32, 1,
        "How far around you loaded chunks count as known terrain for planning, capped at the render distance. " +
            "Chunks further out stay unknown until you come closer. Lower plans faster; higher sees further ahead.",
        unit = " chunks",
    )

    @Group(WORLD_GROUP)
    override val useBaritoneCache by c.setting(
        "Baritone Cache", true,
        "Plan beyond loaded chunks, and find blocks with path find, through the terrain Baritone's cache remembers. " +
            "Read only; every loaded chunk replaces what the cache remembered of it.",
    )

    @Group(DEBUG_GROUP)
    override val dumpFailedPlans by c.setting(
        "Dump Failed Plans", false,
        "On every refusal, replan, hold, stop, cancel and arrival (at most one every 2 s), write the endpoints, entry " +
            "state and the executed tape to lambda/pathing-dumps and the captured terrain to lambda/cache/terrain, so " +
            "the walk can be replayed and fixed offline.",
    )

    @Group(DEBUG_GROUP)
    override val dumpAllPlans by c.setting(
        "Dump All Plans", false,
        "Write the endpoints and entry state of every planned leg to lambda/pathing-dumps " +
            "and the captured terrain to lambda/cache/terrain, successes included, so a real " +
            "course can be replayed and improved offline.",
    )

    private companion object {
        const val MOVES_GROUP = "Moves"
        const val HAZARDS_GROUP = "Hazards"
        const val ARRIVAL_GROUP = "Arrival"
        const val SEARCH_GROUP = "Search"
        const val WORLD_GROUP = "World"
        const val DEBUG_GROUP = "Debug"
    }
}
