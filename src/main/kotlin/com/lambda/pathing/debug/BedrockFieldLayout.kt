/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.debug

import com.lambda.pathing.world.VoxelPos
import kotlin.random.Random

/**
 * Fixed-seed reconstruction of vanilla's 80/60/40/20% exposed-bedrock layer profile --
 * irregular pillars, pockets, overhangs and narrow landings. This is the terrain class
 * the planner redesign is measured against; no hand-built fixture reproduces its
 * jump/route quality pressure. One deterministic generator feeds both the live
 * gametest (real bedrock blocks) and the offline JVM refinement bench (snapshot
 * cubes), so the two always measure the same field.
 *
 * Flat launch and arrival islands at both ends keep the measurement about the chaotic
 * middle rather than endpoint placement luck. Base layer y=62; islands stand at y=63.
 */
object BedrockFieldLayout {
    const val SEED = 0x5EED_BED
    const val LENGTH = 120
    const val HALF_WIDTH = 8

    /** Y of the flat island surface the walk starts and ends on. */
    const val SURFACE_Y = 63

    data class SurfacePoint(val x: Int, val y: Int, val z: Int)

    /** Every solid cell of the field, endpoint islands already carved. */
    fun solidCells(
        length: Int = LENGTH,
        halfWidth: Int = HALF_WIDTH,
        seed: Int = SEED,
    ): Set<VoxelPos> {
        val random = Random(seed)
        val cells = HashSet<VoxelPos>()
        for (x in 0 until length) {
            for (z in -halfWidth..halfWidth) {
                cells += VoxelPos(x, 62, z)
                for (layer in 1..4) {
                    if (random.nextDouble() < (5 - layer) / 5.0) {
                        cells += VoxelPos(x, 62 + layer, z)
                    }
                }
            }
        }
        for (range in listOf(0..3, length - 4 until length)) {
            for (x in range) for (z in -2..2) for (y in SURFACE_Y..67) {
                cells -= VoxelPos(x, y, z)
            }
        }
        return cells
    }

    /**
     * Every stance with a solid support and two blocks of body clearance.
     *
     * Corpus endpoints are selected from this set instead of assuming a fixed Y; that
     * matters on real exposed bedrock where random starts and goals sit on different
     * layers.
     */
    fun standableSurface(
        cells: Set<VoxelPos>,
        length: Int = LENGTH,
        halfWidth: Int = HALF_WIDTH,
    ): List<SurfacePoint> = buildList {
        for (x in 0 until length) {
            for (z in -halfWidth..halfWidth) {
                for (feetY in SURFACE_Y..67) {
                    if (VoxelPos(x, feetY - 1, z) in cells &&
                        VoxelPos(x, feetY, z) !in cells &&
                        VoxelPos(x, feetY + 1, z) !in cells
                    ) {
                        add(SurfacePoint(x, feetY, z))
                    }
                }
            }
        }
    }

    /** Fixed-seed, widely separated endpoint candidates for repeatable A→B corpora. */
    fun randomEndpointPairs(
        count: Int,
        length: Int = LENGTH,
        halfWidth: Int = HALF_WIDTH,
        terrainSeed: Int = SEED,
        pairSeed: Int = SEED xor 0x51A7,
        minHorizontalDistance: Double = length / 3.0,
    ): List<Pair<SurfacePoint, SurfacePoint>> {
        require(count > 0)
        val surface = standableSurface(
            solidCells(length = length, halfWidth = halfWidth, seed = terrainSeed),
            length,
            halfWidth,
        )
        val random = Random(pairSeed)
        val pairs = LinkedHashSet<Pair<SurfacePoint, SurfacePoint>>()
        var draws = 0
        while (pairs.size < count && draws++ < count * 10_000) {
            val from = surface[random.nextInt(surface.size)]
            val to = surface[random.nextInt(surface.size)]
            val dx = (to.x - from.x).toDouble()
            val dz = (to.z - from.z).toDouble()
            if (kotlin.math.hypot(dx, dz) >= minHorizontalDistance) {
                pairs += from to to
            }
        }
        check(pairs.size == count) {
            "could not select $count bedrock endpoint pairs from ${surface.size} stances"
        }
        return pairs.toList()
    }
}
