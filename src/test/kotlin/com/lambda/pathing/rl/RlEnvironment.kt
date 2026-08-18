/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.rl

/** Shared wire-level contract for exact-physics movement-learning environments. */
interface RlEnvironment {
    fun reset(seed: Long, maxDifficulty: Int = Int.MAX_VALUE): RlTransition

    fun step(action: IntArray): RlTransition
}

data class RlTransition(
    val observation: FloatArray,
    val reward: Float,
    val terminated: Boolean,
    val truncated: Boolean,
    val outcome: RlOutcome,
    val ticks: Int,
    /** Environment-specific discrete bucket: gap width or block-field difficulty. */
    val scenario: Int,
    val distanceToGoal: Float,
    /** Terrain surface coverage in [0, 1]. Gap runner reports 1. */
    val density: Float,
)

enum class RlOutcome(val wireId: Int) {
    RUNNING(0),
    SUCCESS(1),
    FALL(2),
    OUT_OF_BOUNDS(3),
    TIME_LIMIT(4),
    SIMULATION_REJECTED(5),

    /**
     * The body stopped making route progress long enough that the remaining
     * episode cannot teach anything. Reported separately from [TIME_LIMIT] so
     * that refusing to attempt a hazard is distinguishable from running out of
     * budget while still moving.
     */
    STALLED(6),
}

data class RlEnvironmentSpec(
    val wireName: String,
    val observationSize: Int,
    val actionDims: IntArray,
    val create: () -> RlEnvironment,
)

object RlEnvironments {
    val GAP_RUNNER = RlEnvironmentSpec(
        wireName = "gap-runner",
        observationSize = GapRunnerEnvironment.OBSERVATION_SIZE,
        actionDims = GapRunnerEnvironment.ACTION_DIMS,
        create = ::GapRunnerEnvironment,
    )
    val BLOCK_FIELD = RlEnvironmentSpec(
        wireName = "block-field",
        observationSize = BlockFieldEnvironment.OBSERVATION_SIZE,
        actionDims = BlockFieldEnvironment.ACTION_DIMS,
        create = ::BlockFieldEnvironment,
    )

    /** Transfer test: whole real corpus routes behind the block-field observation. */
    val BEDROCK_FIELD = RlEnvironmentSpec(
        wireName = "bedrock-field",
        observationSize = BedrockFieldEnvironment.OBSERVATION_SIZE,
        actionDims = BedrockFieldEnvironment.ACTION_DIMS,
        create = { BedrockFieldEnvironment(segmentMode = false) },
    )

    /** The planner's real query: one certified grounded-anchor-to-anchor hop. */
    val BEDROCK_SEGMENT = RlEnvironmentSpec(
        wireName = "bedrock-segment",
        observationSize = BedrockFieldEnvironment.OBSERVATION_SIZE,
        actionDims = BedrockFieldEnvironment.ACTION_DIMS,
        create = { BedrockFieldEnvironment(segmentMode = true) },
    )

    /** Value-guided training over D*-planned irregular terrain (real tail-cost reward). */
    val DSTAR_TERRAIN = RlEnvironmentSpec(
        wireName = "dstar-terrain",
        observationSize = DStarTerrainEnvironment.OBSERVATION_SIZE,
        actionDims = DStarTerrainEnvironment.ACTION_DIMS,
        create = ::DStarTerrainEnvironment,
    )

    private val ALL = listOf(GAP_RUNNER, BLOCK_FIELD, BEDROCK_FIELD, BEDROCK_SEGMENT, DSTAR_TERRAIN)

    fun find(name: String): RlEnvironmentSpec =
        ALL.firstOrNull { it.wireName == name }
            ?: error(
                "Unknown RL environment '$name'; expected " +
                    ALL.joinToString { it.wireName },
            )
}
