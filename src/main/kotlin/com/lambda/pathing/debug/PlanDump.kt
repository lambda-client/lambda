/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.debug

import com.lambda.interaction.managers.rotating.Rotation
import com.lambda.pathing.coarse.SimpleMoveOptions
import com.lambda.pathing.coarse.Stance
import com.lambda.pathing.movement.MotionConstraints
import com.lambda.pathing.world.CoarseVoxel
import com.lambda.pathing.world.Medium
import com.lambda.util.player.prediction.MovementSimulationState
import com.lambda.util.player.prediction.PlayerPhysicsProfile
import com.lambda.util.player.prediction.SimulationSnapshotBounds
import com.lambda.util.player.prediction.SnapshotBlockPhysics
import com.lambda.util.player.prediction.SnapshotSimulationEnvironment
import com.lambda.util.player.prediction.UnsupportedPhysics
import com.lambda.util.player.prediction.UnsupportedPhysicsKind
import java.nio.file.Files
import java.nio.file.Path
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d
import net.minecraft.util.shape.VoxelShapes

object PlanDump {
    const val VERSION = 3

    data class Loaded(
        val bounds: SimulationSnapshotBounds,
        val blocks: Map<BlockPos, SnapshotBlockPhysics>,
        val start: Stance,
        val goal: Stance,
        val initialState: MovementSimulationState,
        val profile: PlayerPhysicsProfile,
        val moveOptions: SimpleMoveOptions,
        val searchConfig: MotionConstraints,
        val note: String,
    ) {
        fun environment(): SnapshotSimulationEnvironment =
            SnapshotSimulationEnvironment.synthetic(bounds, blocks)
    }

    fun write(
        directory: Path,
        environment: SnapshotSimulationEnvironment,
        start: Stance,
        goal: Stance,
        initialState: MovementSimulationState,
        profile: PlayerPhysicsProfile,
        moveOptions: SimpleMoveOptions,
        searchConfig: MotionConstraints,
        note: String,
    ): Path {
        Files.createDirectories(directory)
        val path = directory.resolve("plan-${System.currentTimeMillis()}.dump")

        val palette = LinkedHashMap<SnapshotBlockPhysics, Int>()
        val cells = ArrayList<Pair<BlockPos, Int>>()
        environment.forEachSnapshotBlock { packed, physics ->
            if (physics == SnapshotBlockPhysics.AIR) return@forEachSnapshotBlock
            val index = palette.getOrPut(physics) { palette.size }
            cells += BlockPos.fromLong(packed) to index
        }

        val text = buildString {
            appendLine("version $VERSION")
            appendLine("note ${note.replace('\n', ' ')}")
            with(environment.bounds) {
                appendLine("bounds $minX $minY $minZ $maxX $maxY $maxZ")
            }
            appendLine("start ${start.x} ${start.y} ${start.z}")
            appendLine("goal ${goal.x} ${goal.y} ${goal.z}")
            appendLine(profile.dump())
            appendLine(moveOptions.dump())
            appendLine(searchConfig.dump())
            appendLine(initialState.dump())
            appendLine("palette ${palette.size}")
            palette.keys.forEach { appendLine(it.dump()) }
            appendLine("cells ${cells.size}")
            cells.forEach { (pos, index) -> appendLine("c ${pos.x} ${pos.y} ${pos.z} $index") }
        }
        Files.writeString(path, text)
        return path
    }

    fun read(path: Path): Loaded {
        val lines = Files.readAllLines(path).filter { it.isNotBlank() }
        var bounds: SimulationSnapshotBounds? = null
        var start: Stance? = null
        var goal: Stance? = null
        var profile: PlayerPhysicsProfile? = null
        var moveOptions = SimpleMoveOptions()
        var searchConfig = MotionConstraints()
        var state: MovementSimulationState? = null
        var note = ""
        val palette = ArrayList<SnapshotBlockPhysics>()
        val blocks = HashMap<BlockPos, SnapshotBlockPhysics>()

        for (line in lines) {
            val parts = line.split(' ')
            when (parts[0]) {
                "version" -> require(parts[1].toInt() == VERSION) {
                    "Unsupported plan dump version ${parts[1]}"
                }
                "note" -> note = line.removePrefix("note ")
                "bounds" -> bounds = SimulationSnapshotBounds(
                    parts[1].toInt(), parts[2].toInt(), parts[3].toInt(),
                    parts[4].toInt(), parts[5].toInt(), parts[6].toInt(),
                )

                "start" -> start = Stance(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                "goal" -> goal = Stance(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())
                "profile" -> profile = readProfile(parts)
                "options" -> moveOptions = readOptions(parts)
                "search" -> searchConfig = readSearchConfig(parts)
                "state" -> state = readState(parts, checkNotNull(profile) { "profile must precede state" })
                "p" -> palette += readPhysics(parts)
                "c" -> blocks[BlockPos(parts[1].toInt(), parts[2].toInt(), parts[3].toInt())] =
                    palette[parts[4].toInt()]
            }
        }

        return Loaded(
            bounds = checkNotNull(bounds) { "dump has no bounds" },
            blocks = blocks,
            start = checkNotNull(start) { "dump has no start" },
            goal = checkNotNull(goal) { "dump has no goal" },
            initialState = checkNotNull(state) { "dump has no entry state" },
            profile = checkNotNull(profile) { "dump has no physics profile" },
            moveOptions = moveOptions,
            searchConfig = searchConfig,
            note = note,
        )
    }

    private fun PlayerPhysicsProfile.dump(): String = listOf(
        "profile", movementSpeed, sneakSpeedModifier, gravity, jumpStrength, stepHeight,
        jumpBoostVelocityModifier, slowFalling, width, height, eyeHeight,
    ).joinToString(" ")

    private fun readProfile(parts: List<String>) = PlayerPhysicsProfile(
        movementSpeed = parts[1].toDouble(),
        sneakSpeedModifier = parts[2].toDouble(),
        gravity = parts[3].toDouble(),
        jumpStrength = parts[4].toDouble(),
        stepHeight = parts[5].toDouble(),
        jumpBoostVelocityModifier = parts[6].toDouble(),
        slowFalling = parts[7].toBoolean(),
        width = parts[8].toDouble(),
        height = parts[9].toDouble(),
        eyeHeight = parts[10].toDouble(),
    )

    private fun SimpleMoveOptions.dump(): String = listOf(
        "options", allowDiagonal, allowStepUp, maxWalkOffDepth, allowJumpCandidates,
        maxJumpSpan, maxJumpDrop, maxDiagonalJumpSpan,
    ).joinToString(" ")

    private fun readOptions(parts: List<String>) = SimpleMoveOptions(
        allowDiagonal = parts[1].toBoolean(),
        allowStepUp = parts[2].toBoolean(),
        maxWalkOffDepth = parts[3].toInt(),
        allowJumpCandidates = parts[4].toBoolean(),
        maxJumpSpan = parts[5].toInt(),
        maxJumpDrop = parts[6].toInt(),
        maxDiagonalJumpSpan = parts[7].toInt(),
    )

    private fun MotionConstraints.dump(): String = listOf(
        "search", maxFrames, maxYawDegreesPerFrame, goalRadius,
        sprintModes.joinToString(",").ifEmpty { "-" },
    ).joinToString(" ")

    private fun readSearchConfig(parts: List<String>) = MotionConstraints(
        maxFrames = parts[1].toInt(),
        maxYawDegreesPerFrame = parts[2].toDouble(),
        goalRadius = parts[3].toDouble(),
        sprintModes = if (parts[4] == "-") emptyList() else parts[4].split(',').map { it.toBoolean() },
    )

    private fun MovementSimulationState.dump(): String = listOf(
        "state", position.x, position.y, position.z, velocity.x, velocity.y, velocity.z,
        rotation.yaw, rotation.pitch, onGround, isJumping, isSprinting, isSneaking,
        jumpingCooldown, horizontalCollision, collidedSoftly, verticalCollision,
        velocityAffectingPos.x, velocityAffectingPos.y, velocityAffectingPos.z,
        supportingBlockPos?.let { "${it.x} ${it.y} ${it.z}" } ?: "none",
    ).joinToString(" ")

    private fun readState(parts: List<String>, profile: PlayerPhysicsProfile): MovementSimulationState {
        val position = Vec3d(parts[1].toDouble(), parts[2].toDouble(), parts[3].toDouble())
        return MovementSimulationState.synthetic(
            profile = profile,
            position = position,
            rotation = Rotation(parts[7].toDouble(), parts[8].toDouble()),
            velocity = Vec3d(parts[4].toDouble(), parts[5].toDouble(), parts[6].toDouble()),
            onGround = parts[9].toBoolean(),
        ).copy(
            isJumping = parts[10].toBoolean(),
            isSprinting = parts[11].toBoolean(),
            isSneaking = parts[12].toBoolean(),
            jumpingCooldown = parts[13].toInt(),
            horizontalCollision = parts[14].toBoolean(),
            collidedSoftly = parts[15].toBoolean(),
            verticalCollision = parts[16].toBoolean(),
            velocityAffectingPos = BlockPos(parts[17].toInt(), parts[18].toInt(), parts[19].toInt()),
            supportingBlockPos = if (parts[20] == "none") null else {
                BlockPos(parts[20].toInt(), parts[21].toInt(), parts[22].toInt())
            },
        )
    }

    private fun SnapshotBlockPhysics.dump(): String {
        val boxes = collisionShape.boundingBoxes
        return buildString {
            append("p ").append(slipperiness).append(' ')
                .append(velocityMultiplier).append(' ')
                .append(jumpVelocityMultiplier).append(' ')
                .append(fenceLike).append(' ')
                .append(coarseVoxel.fullyPassable).append(' ')
                .append(coarseVoxel.centerPassable).append(' ')
                .append(coarseVoxel.standableFullTop).append(' ')
                .append(coarseVoxel.intrudesAbove).append(' ')
                .append(coarseVoxel.medium.name).append(' ')
                .append(unsupportedPhysics?.kind?.name ?: "-").append(' ')
                .append(unsupportedPhysics?.blockId?.replace(' ', '_') ?: "-").append(' ')
                .append(boxes.size)
            boxes.forEach { box ->
                append(' ').append(box.minX).append(' ').append(box.minY).append(' ').append(box.minZ)
                    .append(' ').append(box.maxX).append(' ').append(box.maxY).append(' ').append(box.maxZ)
            }
        }
    }

    private fun readPhysics(parts: List<String>): SnapshotBlockPhysics {
        val boxCount = parts[12].toInt()
        var shape = VoxelShapes.empty()
        for (box in 0 until boxCount) {
            val base = 13 + box * 6
            shape = VoxelShapes.union(
                shape,
                VoxelShapes.cuboid(
                    Box(
                        parts[base].toDouble(), parts[base + 1].toDouble(), parts[base + 2].toDouble(),
                        parts[base + 3].toDouble(), parts[base + 4].toDouble(), parts[base + 5].toDouble(),
                    )
                ),
            )
        }
        val medium = Medium.valueOf(parts[9])
        val unsupportedKind = parts[10].takeIf { it != "-" }?.let { UnsupportedPhysicsKind.valueOf(it) }
        return SnapshotBlockPhysics(
            collisionShape = shape,
            slipperiness = parts[1].toDouble(),
            velocityMultiplier = parts[2].toDouble(),
            jumpVelocityMultiplier = parts[3].toDouble(),
            unsupportedPhysics = unsupportedKind?.let {
                UnsupportedPhysics(it, parts[11].takeIf { id -> id != "-" })
            },
            coarseVoxel = CoarseVoxel(
                fullyPassable = parts[5].toBoolean(),
                centerPassable = parts[6].toBoolean(),
                standableFullTop = parts[7].toBoolean(),
                intrudesAbove = parts[8].toBoolean(),
                medium = medium,
            ),
            fenceLike = parts[4].toBoolean(),
        )
    }
}
