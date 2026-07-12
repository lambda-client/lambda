/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.bench

import com.lambda.util.world.fastVectorOf
import net.minecraft.block.Block
import net.minecraft.block.Blocks
import net.minecraft.server.MinecraftServer
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.io.File
import kotlin.math.atan2
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Deterministic generated parkour corpus.
 *
 * The isolated matrix covers every canonical integer offset in discovery's
 * 2..5-block fan (rotations/reflections are physics-equivalent), crossed with
 * every supported landing delta. The transition matrix then deliberately
 * restores directional coverage: straight carry, 45/90/135-degree turns,
 * alternating turns, and rising/descending chains on short pads.
 *
 * Generated cases are gated: every target and landing must match the exact
 * platform contract, so any future retry, shortcut, walk-off, or rejection
 * fails the GameTest rather than silently becoming baseline telemetry.
 */
object ParkourCourseGenerator {
    private const val FEET_Y = 70
    private const val TIMEOUT_TICKS = 260
    private const val PREFIX = "parkour-"
    private val RESET_COMMANDS = listOf(
        "/fill -16 62 -14 24 78 14 minecraft:air",
        "/fill -16 63 -14 24 63 14 minecraft:stone",
    )

    private data class Offset(val x: Int, val z: Int) {
        val distance get() = hypot(x.toDouble(), z.toDouble())
        val id get() = "${x}x${z}"
    }

    private data class Pad(
        /** Goal/target node; bounds may extend away from it for landing carry. */
        val x: Int,
        val feetY: Int,
        val z: Int,
        val minX: Int = x,
        val maxX: Int = x,
        val minZ: Int = z,
        val maxZ: Int = z,
    ) {
        val landing get() = ParkourLanding(minX, maxX, feetY, minZ, maxZ)
    }

    private data class ChainSpec(
        val id: String,
        val vectors: List<Triple<Int, Int, Int>>,
    )

    /** Unique shapes after rotation/reflection reduction. */
    private val canonicalOffsets: List<Offset> = buildList {
        for (x in 2..5) for (z in 0..x) {
            val offset = Offset(x, z)
            if (offset.distance in 2.0..5.0) add(offset)
        }
    }

    private val isolated: List<TraversalScenario> = buildList {
        for (offset in canonicalOffsets) {
            // landing delta: discovery TAKEOFF_RISES mapped into world Y.
            for (landingDelta in listOf(-3, -2, -1, 0, 1)) {
                // Ascending discovery candidates intentionally cap at 3.2.
                if (landingDelta > 0 && offset.distance > 3.2) continue
                add(isolatedCase(offset, landingDelta))
            }
        }
    }

    private val chains: List<TraversalScenario> = listOf(
        ChainSpec("carry-straight", listOf(t(5, 0), t(5, 0), t(5, 0))),
        ChainSpec("turn-left-90", listOf(t(5, 0), t(0, 5))),
        ChainSpec("turn-right-90", listOf(t(5, 0), t(0, -5))),
        ChainSpec("turn-left-45", listOf(t(5, 0), t(4, 4), t(0, 5))),
        // A short-pad 135° reversal necessarily puts its landing within the
        // discovery radius of the original takeoff, allowing a legitimate
        // direct shortcut. It needs a shielded fixture and is therefore not
        // falsely claimed by this first transition corpus.
        ChainSpec("zigzag-90", listOf(t(5, 0), t(0, 5), t(5, 0))),
        // Rising hops use four-block pad-centre spacing: with radius-one
        // pads the actual lip-to-lip displacement is within discovery's
        // calibrated 3.2-block ascending reach. Five was not parkour-hard;
        // it was physically outside the maneuver library's reachable set.
        // Offset the second landing far enough that no <=5-block diagonal
        // can skip the corner, while radius-one pad edges keep each intended
        // transition inside its vertical reach band.
        ChainSpec("rise-turn-fall", listOf(t(4, 0, 1), t(0, 6, -1))),
        ChainSpec("fall-turn-rise", listOf(t(6, 0, -1), t(0, -5, 1))),
        ChainSpec("alternating-height", listOf(t(4, 0, 1), t(5, 0, -1), t(4, 0, 1))),
    ).map(::chainCase)

    val all: List<TraversalScenario> = isolated + chains

    private fun t(x: Int, z: Int, dy: Int = 0) = Triple(x, z, dy)

    private fun isolatedCase(offset: Offset, landingDelta: Int): TraversalScenario {
        // Keep the front target block at the requested displacement so the
        // gap never disappears, but extend quartz laterally and *away* from
        // the takeoff. This tests the jump rather than single-block balance,
        // while giving the certified carry/brake policy a real landing pad.
        val landing = Pad(
            x = offset.x,
            feetY = FEET_Y + landingDelta,
            z = offset.z,
            minX = offset.x,
            maxX = offset.x + 2,
            minZ = offset.z - 1,
            maxZ = offset.z + 1,
        )
        val deltaId = when {
            landingDelta > 0 -> "up$landingDelta"
            landingDelta < 0 -> "down${-landingDelta}"
            else -> "flat"
        }
        val caseId = "isolated-${offset.id}-$deltaId"
        // Three-wide rasterization keeps arbitrary-angle approaches a solid
        // runnable corridor. A one-wide digital line contains holes relative
        // to the continuous refined heading and accidentally creates an
        // extra "jump along the runway" before the intended gap. Discovery
        // may still choose a side/earlier takeoff for momentum, so the case
        // id describes landing displacement from the runway end; per-jump
        // telemetry records the exact selected edge.
        val approach = approachBlocks(offset, halfWidth = 1)
        return generatedScenario(
            caseId = caseId,
            purpose = "Generated isolated layout: landing offset from runway end " +
                "(${offset.x},${offset.z}), " +
                "distance ${"%.2f".format(offset.distance)}, landing delta $landingDelta.",
            pads = listOf(landing),
            extraSupports = approach,
            // The exact five-block centre displacement is an ice-tier
            // momentum primitive: a three-block patch only reached stored
            // v=0.22 before its narrow envelope, while discovery correctly
            // requires ~0.24. Use the complete deterministic run-up.
            iceSupports = if (offset.x == 5 && offset.z == 0 && landingDelta == 0) approach else emptySet(),
            start = approachStart(offset),
            startYaw = yawToward(offset.x, offset.z),
        )
    }

    private fun chainCase(spec: ChainSpec): TraversalScenario {
        var x = 0
        var z = 0
        var y = FEET_Y
        val pads = ArrayList<Pad>(spec.vectors.size)
        for ((dx, dz, dy) in spec.vectors) {
            x += dx
            z += dz
            y += dy
            pads += Pad(x, y, z, x - 1, x + 1, z - 1, z + 1)
        }
        val first = spec.vectors.first()
        val approachOffset = Offset(kotlin.math.abs(first.first), kotlin.math.abs(first.second))
        return generatedScenario(
            caseId = "chain-${spec.id}",
            purpose = "Generated short-pad transition chain '${spec.id}': ${spec.vectors}.",
            pads = pads,
            extraSupports = approachBlocks(approachOffset, halfWidth = 1),
            start = approachStart(approachOffset),
            startYaw = yawToward(first.first, first.second),
        )
    }

    private fun generatedScenario(
        caseId: String,
        purpose: String,
        pads: List<Pad>,
        extraSupports: Set<BlockPos>,
        iceSupports: Set<BlockPos> = emptySet(),
        start: Vec3d,
        startYaw: Float,
    ): TraversalScenario {
        val fixture: (MinecraftServer) -> Unit = { server ->
            val world = server.overworld
            // The command reset only covers the legacy scenario volume. A
            // direct clear makes generated cases independent of run order.
            for (x in -12..22) for (z in -12..12) for (y in 64..76) {
                world.setBlockState(BlockPos(x, y, z), Blocks.AIR.defaultState, Block.NOTIFY_LISTENERS)
            }
            for (support in extraSupports) {
                val block = if (support in iceSupports) Blocks.BLUE_ICE else Blocks.SMOOTH_QUARTZ
                world.setBlockState(support, block.defaultState, Block.NOTIFY_LISTENERS)
            }
            for (pad in pads) for (px in pad.minX..pad.maxX) {
                for (pz in pad.minZ..pad.maxZ) {
                    world.setBlockState(
                        BlockPos(px, pad.feetY - 1, pz),
                        Blocks.QUARTZ_BLOCK.defaultState,
                        Block.NOTIFY_LISTENERS,
                    )
                }
            }
        }
        val goal = pads.last()
        return TraversalScenario(
            name = PREFIX + caseId,
            purpose = purpose,
            fixture = RESET_COMMANDS,
            serverFixture = fixture,
            start = start,
            startYaw = startYaw,
            goal = fastVectorOf(goal.x, goal.feetY, goal.z),
            timeoutTicks = TIMEOUT_TICKS,
            allowJump = true,
            gated = true,
            minAllowedY = FEET_Y - 3.25,
            parkourContract = ParkourContract(caseId, pads.map(Pad::landing)),
        )
    }

    /**
     * Solid six-block quartz approach containing the desired heading line.
     * Filling the raster's bounding rectangle is intentional: independently
     * rounded perpendicular samples left diagonal pinholes that discovery
     * correctly treated as extra gaps, adding an unintended jump before the
     * measured one. The generated case is about the landing transition, not
     * balance-beaming along a voxelized runway.
     */
    private fun approachBlocks(offset: Offset, halfWidth: Int): Set<BlockPos> = buildSet {
        val length = offset.distance
        val ux = offset.x / length
        val uz = offset.z / length
        var minX = 0
        var maxX = 0
        var minZ = 0
        var maxZ = 0
        for (step in 0..6) {
            val distance = -step.toDouble()
            val cx = (ux * distance).roundToInt()
            val cz = (uz * distance).roundToInt()
            minX = minOf(minX, cx)
            maxX = maxOf(maxX, cx)
            minZ = minOf(minZ, cz)
            maxZ = maxOf(maxZ, cz)
        }
        // Canonical isolated vectors always progress +X. Do not widen the
        // runway *past* its x=0 lip: x=1 adjacent to a landing beginning at
        // x=2 fills the intended one-block gap and lets short cases walk.
        for (x in minX - halfWidth..maxX) {
            for (z in minZ - halfWidth..maxZ + halfWidth) {
                add(BlockPos(x, FEET_Y - 1, z))
            }
        }
    }

    private fun approachStart(offset: Offset): Vec3d {
        val length = offset.distance
        return Vec3d(-offset.x / length * 5.0 + 0.5, FEET_Y.toDouble(), -offset.z / length * 5.0 + 0.5)
    }

    private fun yawToward(dx: Int, dz: Int): Float =
        Math.toDegrees(atan2(-dx.toDouble(), dz.toDouble())).toFloat()

    fun writeManifest(directory: File) {
        File(directory, "parkour-manifest.json").writeText(
            all.joinToString(prefix = "[\n", postfix = "\n]\n", separator = ",\n") { scenario ->
                val contract = requireNotNull(scenario.parkourContract)
                buildString {
                    append("  {\"name\":\"").append(scenario.name).append("\"")
                    append(",\"caseId\":\"").append(contract.caseId).append("\"")
                    append(",\"purpose\":\"").append(scenario.purpose.replace("\"", "\\\"")).append("\"")
                    append(",\"landings\":[")
                    append(contract.landings.joinToString(",") {
                        "{\"x\":[${it.minX},${it.maxX}],\"y\":${it.feetY},\"z\":[${it.minZ},${it.maxZ}]}"
                    })
                    append("]}")
                }
            },
        )
    }

}
