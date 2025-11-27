/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.interaction.request.rotating.visibilty

import com.lambda.context.Automated
import com.lambda.context.SafeContext
import com.lambda.interaction.construction.verify.SurfaceScan
import com.lambda.interaction.request.rotating.Rotation
import com.lambda.interaction.request.rotating.Rotation.Companion.dist
import com.lambda.interaction.request.rotating.RotationManager
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.ALL_SIDES
import com.lambda.interaction.request.rotating.visibilty.VisibilityChecker.findRotation
import com.lambda.threading.runSafeAutomated
import com.lambda.util.extension.rotation
import com.lambda.util.world.raycast.InteractionMask
import net.minecraft.entity.LivingEntity
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.hit.EntityHitResult
import net.minecraft.util.hit.HitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

@DslMarker
annotation class RotationDsl

/**
 * Creates a [RotationTarget] based on a specific angle.
 *
 * @param angle The target rotation.
 * @param maxAngleDistance The maximum allowed distance between the current rotation and the target angle. Defaults to 10.0.
 * @return A [RotationTarget] instance.
 */
@RotationDsl
fun lookAt(angle: Rotation, maxAngleDistance: Double = 0.001) =
    RotationTarget(null, {
        RotationManager.activeRotation dist angle < maxAngleDistance
    }) { angle }

@RotationDsl
fun lookInDirection(direction: PlaceDirection) =
    RotationTarget(null, {
        PlaceDirection.fromRotation(RotationManager.activeRotation) == direction
    }) {
        if (!direction.isInArea(player.rotation)) direction.snapToArea(RotationManager.activeRotation)
        else player.rotation
    }

/**
 * Creates a [RotationTarget] based on a requested hit, but doesn't build the rotation.
 *
 * Use this, if you need to build the rotation by yourself.
 *
 * @param rotation The target rotation.
 * @param hit [RequestedHit] to look at.
 * @param rotation Custom rotation builder.
 * @return A [RotationTarget] instance.
 */
@RotationDsl
fun lookAtHit(hit: RequestedHit, rotation: SafeContext.() -> Rotation?) =
    RotationTarget(hit, buildRotation = rotation)

/**
 * Creates a [RotationTarget] based on a [HitResult].
 *
 * @param hit [HitResult] to look at.
 * @return A [RotationTarget] instance.
 */
@RotationDsl
fun Automated.lookAtHit(hit: HitResult): RotationTarget? {
    return when (hit) {
        is BlockHitResult -> lookAtBlock(hit.blockPos, setOf(hit.side), SurfaceScan.DEFAULT)
        is EntityHitResult -> lookAtEntity(hit.entity as? LivingEntity ?: return null)
        else -> null
    }
}

/**
 * Creates a [RotationTarget] based on an entity.
 *
 * @param entity The target entity.
 * @param config The interaction configuration. Defaults to [TaskFlowModule.interact].
 * @return A [RotationTarget] instance.
 */
@RotationDsl
fun Automated.lookAtEntity(entity: LivingEntity): RotationTarget {
    val requestedHit = entityHit(entity, buildConfig.attackReach)

    return RotationTarget(requestedHit) {
        runSafeAutomated {
            findRotation(
                requestedHit.getBoundingBoxes(),
                buildConfig.attackReach,
                player.eyePos,
                ALL_SIDES,
                SurfaceScan.DEFAULT,
                false,
                InteractionMask.Entity
            ) { requestedHit.verifyHit(hit) }?.rotation
        }
    }
}

/**
 * Creates a [RotationTarget] based on a block.
 *
 * @param pos The position of the block.
 * @param sides The set of sides to consider for the hit. Defaults to [ALL_SIDES].
 * @param config The interaction configuration. Defaults to [TaskFlowModule.interact].
 * @return A [RotationTarget] instance.
 */
@RotationDsl
fun Automated.lookAtBlock(
    pos: BlockPos,
    sides: Set<Direction> = ALL_SIDES,
    surfaceScan: SurfaceScan = SurfaceScan.DEFAULT
): RotationTarget {
    val requestedHit = blockHit(pos, sides, buildConfig.interactReach)

    return RotationTarget(requestedHit) {
        runSafeAutomated {
            findRotation(
                requestedHit.getBoundingBoxes(),
                buildConfig.interactReach,
                player.eyePos,
                sides,
                surfaceScan,
                false,
                InteractionMask.Block
            ) { requestedHit.verifyHit(hit) }?.rotation
        }
    }
}