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
        "; input f=%.1f s=%.1f jump=%s sprintKey=%s yaw=%s".format(
            it.forward, it.strafe, it.jump, it.sprint,
            it.rotation?.yaw?.let { yaw -> "%.2f".format(yaw) } ?: "hold",
        )
    }.orEmpty()
    val stateDetail = expected?.let {
        ("; expected/live yaw %.3f/%.3f, sprint %s/%s, ground %s/%s, " +
            "hCollision %s/%s, soft %s/%s, vCollision %s/%s, jumpCooldown %d/%d, " +
            "position %s/%s, velocity %s/%s").format(
            it.rotation.yaw, observed.rotation.yaw,
            it.isSprinting, observed.isSprinting,
            it.onGround, observed.onGround,
            it.horizontalCollision, observed.horizontalCollision,
            it.collidedSoftly, observed.collidedSoftly,
            it.verticalCollision, observed.verticalCollision,
            it.jumpingCooldown, observed.jumpingCooldown,
            it.position.short(), observed.position.short(),
            it.velocity.short(), observed.velocity.short(),
        )
    }.orEmpty()
    val previousInputDetail = path?.plan?.tape?.asList()?.getOrNull(frame - 1)?.let {
        "; previous input f=%.1f jump=%s sprintKey=%s".format(it.forward, it.jump, it.sprint)
    }.orEmpty()
    val liveInputDetail = mc.player?.input?.let {
        "; live input f=%.1f jump=%s sprintKey=%s".format(
            it.movementVector.y, it.playerInput.jump(), it.playerInput.sprint(),
        )
    }.orEmpty()
    return "frame $frame $phase$splice: $deviation$inputDetail$previousInputDetail" +
        "$liveInputDetail$stateDetail"
}

private fun Vec3d.short() = "(%.4f,%.4f,%.4f)".format(x, y, z)
