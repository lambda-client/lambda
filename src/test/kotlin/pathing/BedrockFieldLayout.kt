package com.lambda.pathing.debug

import com.lambda.pathing.core.VoxelPos
import kotlin.random.Random

object BedrockFieldLayout {
    const val SEED = 0x5EED_BED
    const val LENGTH = 120
    const val HALF_WIDTH = 8

    const val SURFACE_Y = 63

    data class SurfacePoint(val x: Int, val y: Int, val z: Int)

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
