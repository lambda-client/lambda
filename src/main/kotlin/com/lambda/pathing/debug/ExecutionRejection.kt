/*
 * Copyright 2026 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

package com.lambda.pathing.debug

import com.lambda.Lambda.mc
import com.lambda.pathing.PathingManager
import com.lambda.pathing.execution.ExecutionDeviation
import com.lambda.util.player.prediction.MovementSimulationState
import net.minecraft.util.math.Box
import net.minecraft.util.math.Vec3d

internal fun executionRejectionReport(
    path: PathingManager.PublishedPath?,
    frame: Int,
    deviation: ExecutionDeviation,
    observed: MovementSimulationState,
    afterInput: Boolean,
): String {
    val splice = path?.spliceFrames?.let { boundaries ->
        when {
            frame in boundaries -> " at predicted splice"
            frame + 1 in boundaries -> " immediately before predicted splice"
            else -> ""
        }
    }.orEmpty()
    val input = path?.plan?.tape?.asList()?.getOrNull(frame)
    val expected = path?.plan?.let { plan ->
        if (afterInput) plan.frames.getOrNull(frame)?.state
        else if (frame == 0) plan.initialState else plan.frames.getOrNull(frame - 1)?.state
    }
    val phase = if (afterInput) "after input" else "before input"
    val inputDetail = input?.let {
        "; input f=%.1f s=%.1f jump=%s sprintKey=%s sneakKey=%s yaw=%s".format(
            it.forward, it.strafe, it.jump, it.sprint, it.sneak,
            it.rotation?.yaw?.let { yaw -> "%.2f".format(yaw) } ?: "hold",
        )
    }.orEmpty()
    val stateDetail = expected?.let {
        ("; expected/live yaw %.3f/%.3f, sprint %s/%s, sneaking %s/%s, ground %s/%s, " +
            "hCollision %s/%s, soft %s/%s, vCollision %s/%s, jumpCooldown %d/%d, " +
            "position %s/%s, velocity %s/%s").format(
            it.rotation.yaw, observed.rotation.yaw,
            it.isSprinting, observed.isSprinting,
            // The sneak flag drives two vanilla behaviours that lag differently -- the
            // ledge clip immediately, the speed multiplier a tick later -- so a divergence
            // here is unreadable without seeing what each side thought it was doing.
            it.isSneaking, observed.isSneaking,
            it.onGround, observed.onGround,
            it.horizontalCollision, observed.horizontalCollision,
            it.collidedSoftly, observed.collidedSoftly,
            it.verticalCollision, observed.verticalCollision,
            it.jumpingCooldown, observed.jumpingCooldown,
            it.position.short(), observed.position.short(),
            it.velocity.short(), observed.velocity.short(),
        )
    }.orEmpty()
    val before = path?.plan?.let { plan ->
        if (frame == 0) plan.initialState else plan.frames.getOrNull(frame - 1)?.state
    }
    val ledgeDetail =
        if (expected?.horizontalCollision == true && !observed.horizontalCollision && before != null) {
            liveLedgeProbe(before.boundingBox, observed.velocity)
        } else ""
    val previousInputDetail = path?.plan?.tape?.asList()?.getOrNull(frame - 1)?.let {
        "; previous input f=%.1f jump=%s sprintKey=%s sneakKey=%s".format(
            it.forward, it.jump, it.sprint, it.sneak,
        )
    }.orEmpty()
    val liveInputDetail = mc.player?.input?.let {
        "; live input f=%.1f jump=%s sprintKey=%s sneakKey=%s".format(
            it.movementVector.y, it.playerInput.jump(), it.playerInput.sprint(),
            it.playerInput.sneak(),
        )
    }.orEmpty()
    return "frame $frame $phase$splice: $deviation$inputDetail$previousInputDetail" +
        "$liveInputDetail$stateDetail$ledgeDetail"
}

/**
 * What vanilla's own sneak ledge clip says about the live world, at the frame that failed.
 *
 * Printed only when the tape collided horizontally and the live client did not, which is
 * the signature of a ledge clip firing on one side only. The tape's side is the simulator
 * reading the planning snapshot; this is the identical predicate read from the live world,
 * so the two together separate the only things that can produce that signature.
 *
 * A [worldEmpty] of true means the live world does have the ledge the tape clipped on, and
 * the live client declined to clip anyway -- so the sneak key never reached `clipAtLedge`,
 * and the bug is in the input path. False means the world has ground where the snapshot had
 * air, and the bug is in the terrain the plan was built on. [blocksEmpty] splits off the
 * third case: vanilla's probe also fails on entities and the world border, neither of which
 * the snapshot models, so a disagreement only between these two is not terrain at all.
 *
 * @see net.minecraft.entity.player.PlayerEntity.adjustMovementForSneaking
 */
private fun liveLedgeProbe(box: Box, movement: Vec3d): String {
    val player = mc.player ?: return ""
    val world = mc.world ?: return ""
    val step = player.stepHeight.toDouble()
    val probe = Box(
        box.minX + LEDGE_EPSILON + movement.x, box.minY - step - LEDGE_EPSILON, box.minZ + LEDGE_EPSILON + movement.z,
        box.maxX - LEDGE_EPSILON + movement.x, box.minY, box.maxZ - LEDGE_EPSILON + movement.z,
    )
    return "; live ledge probe %s dx=%.4f dz=%.4f step=%.2f worldEmpty=%s blocksEmpty=%s clipAtLedge=%s".format(
        probe.short(), movement.x, movement.z, step,
        world.isSpaceEmpty(player, probe),
        world.isBlockSpaceEmpty(player, probe),
        player.isSneaking,
    )
}

private const val LEDGE_EPSILON = 1.0E-7

private fun Box.short() =
    "[%.3f,%.3f,%.3f -> %.3f,%.3f,%.3f]".format(minX, minY, minZ, maxX, maxY, maxZ)

private fun Vec3d.short() = "(%.4f,%.4f,%.4f)".format(x, y, z)
