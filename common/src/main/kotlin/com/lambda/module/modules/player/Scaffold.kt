/*
 * Copyright 2024 Lambda
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

package com.lambda.module.modules.player

import com.lambda.config.groups.InteractionSettings
import com.lambda.config.groups.RotationSettings
import com.lambda.context.SafeContext
import com.lambda.event.events.*
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.graphics.renderer.esp.DirectionMask
import com.lambda.graphics.renderer.esp.DirectionMask.buildSideMesh
import com.lambda.graphics.renderer.esp.builders.build
import com.lambda.interaction.RotationManager.currentRotation
import com.lambda.interaction.RotationManager.requestRotation
import com.lambda.interaction.blockplace.PlaceFinder.Companion.buildPlaceInfo
import com.lambda.interaction.blockplace.PlaceInfo
import com.lambda.interaction.blockplace.PlaceInteraction.placeBlock
import com.lambda.interaction.rotation.Rotation
import com.lambda.interaction.rotation.Rotation.Companion.angleDifference
import com.lambda.interaction.rotation.Rotation.Companion.dist
import com.lambda.interaction.rotation.Rotation.Companion.rotationTo
import com.lambda.interaction.rotation.Rotation.Companion.wrap
import com.lambda.interaction.rotation.RotationContext
import com.lambda.interaction.visibilty.VisibilityChecker.scanVisibleSurfaces
import com.lambda.module.Module
import com.lambda.module.modules.client.GuiSettings
import com.lambda.module.tag.ModuleTag
import com.lambda.util.math.MathUtils.floorToInt
import com.lambda.util.math.VecUtils.dist
import com.lambda.util.math.VecUtils.distSq
import com.lambda.util.math.multAlpha
import com.lambda.util.math.step
import com.lambda.util.math.transform
import com.lambda.util.player.MovementUtils.calcMoveYaw
import com.lambda.util.player.MovementUtils.isInputting
import com.lambda.util.player.MovementUtils.newMovementInput
import com.lambda.util.player.MovementUtils.roundedForward
import com.lambda.util.player.MovementUtils.roundedStrafing
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.toFastVec
import net.minecraft.util.Hand
import net.minecraft.util.hit.BlockHitResult
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Box
import net.minecraft.util.math.Direction
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.floor
import kotlin.math.pow

object Scaffold : Module(
    name = "Scaffold",
    description = "Places blocks under the player",
    defaultTags = setOf(ModuleTag.PLAYER)
) {
    private val page by setting("Page", Page.GENERAL)

    private val keepY by setting("Keep Y", true) { page == Page.GENERAL }
    private val minPlaceDist by setting("Min Place Dist", 0.0, 0.0..0.2, 0.01) { page == Page.GENERAL }
    private val minRotateDist by setting("Min Rotate Dist", 0.10, 0.0..0.2, 0.01) { page == Page.GENERAL }

    private val rotationConfig = RotationSettings(this) { page == Page.ROTATION }
    private val safeWalk by setting("Sneak Before Rotation", true) { page == Page.ROTATION }
    private val direction by setting("Direction", LookingDirection.FREE) { page == Page.ROTATION }
    private val optimalPitch by setting("Optimal Pitch", 81.0, 70.0..85.0, 0.05) { page == Page.ROTATION }

    private val interactionConfig = InteractionSettings(this) { page == Page.INTERACTION }

    // Placement
    private var placeInfo: PlaceInfo? = null
    private var keepLevel: Int? = null
    private var lastRotation: Rotation? = null
    private var edjeDistance = 0.0

    // Sneaking
    private var placeInfoAge = 0
    private var sneakTicks = 0

    // Rendering
    private val renderInfo = HashSet<Pair<PlaceInfo, Long>>()
    private val currentTime get() = System.currentTimeMillis()

    // Other
    private val yawList = listOf(0.0, 90.0, 180.0, 270.0)
    private val diagonalYawList = yawList.map { it + 45 }
    private val builderSideMask = EnumSet.allOf(Direction::class.java).apply {
        remove(Direction.UP)
    }

    // Yaw values within this range will not make your movement unstable
    private const val YAW_THRESHOLD = 15.0

    private enum class Page {
        GENERAL,
        ROTATION,
        INTERACTION
    }

    private enum class LookingDirection {
        FREE,
        ClAMPED,
        STRAIGHT,
        DIAGONAL
    }

    init {
        requestRotation(
            onUpdate = {
                lastRotation = null
                val info = updatePlaceInfo() ?: return@requestRotation null
                val rotation = rotate(info) ?: return@requestRotation null

                RotationContext(rotation, rotationConfig)
            }
        )

        listen<MovementEvent.Sneak> {
            if (sneakTicks > 0) it.sneak = true
        }

        listen<TickEvent.Pre> {
            placeInfo?.let { info ->
                tickPlacement(info)
            }

            updateSneaking()
        }

        listen<RenderEvent.StaticESP> { event ->
            buildRenderer(event)
        }

        onEnable {
            placeInfo = null
            renderInfo.clear()

            keepLevel = null
            sneakTicks = 0
        }
    }

    private fun SafeContext.updatePlaceInfo(): PlaceInfo? {
        // Feet placing blockpos
        var y = (floor(player.pos.y) - 0.00001).floorToInt()

        // KeepY update
        if (keepY && isInputting) {
            keepLevel?.let {
                y = it
            }
        }

        // Getting the latest block of the placement sequence
        placeInfo = buildPlaceInfo(
            basePos = BlockPos(player.pos.x.floorToInt(), y, player.pos.z.floorToInt()),
            range = interactionConfig.reach + 2,
            sides = builderSideMask
        )

        placeInfo?.let { info ->
            placeInfoAge = 0

            // Ignore supporting blocks
            if (info.placeSteps != 0) return@let

            // Updating keep level
            keepLevel = info.placedPos.y

            edjeDistance = distanceToEdge(info.clickPos)
            if (info.clickSide.axis == Direction.Axis.Y) edjeDistance = -1.0
        }

        return placeInfo
    }

    private fun SafeContext.rotate(info: PlaceInfo): Rotation? {
        val eye = player.eyePos

        val reach = interactionConfig.reach
        val reachSq = reach.pow(2)

        val input = newMovementInput()
        val moveYaw = calcMoveYaw(player.yaw, input.roundedForward, input.roundedStrafing)

        // Checking whether the player is moving diagonally
        val isDiagonal = diagonalYawList.any {
            angleDifference(moveYaw, it) < YAW_THRESHOLD
        }

        // Assumed yaw values
        val assumedYaw = assumeYawByDirection(moveYaw)

        // No need to rotate, already looking correctly
        val lookingCorrectly = castRotation(currentRotation, info) != null
        val isYawStable = angleDifference(currentRotation.yaw, assumedYaw) < YAW_THRESHOLD
        if (lookingCorrectly && isYawStable) return currentRotation

        // Dividing the surface by segments and iterating through them
        val pointScan = mutableSetOf<Rotation>().apply {
            scanVisibleSurfaces(
                eyes = eye,
                box = Box(info.clickPos),
                resolution = interactionConfig.resolution
            ) { _, vec ->
                if (eye distSq vec > reachSq) return@scanVisibleSurfaces

                val rotation = eye.rotationTo(vec)
                castRotation(rotation, info) ?: return@scanVisibleSurfaces

                add(rotation)
            }
        }

        // Iterating through assumed angle ranges
        val angleScan = mutableSetOf<Rotation>().apply {
            val pitchRange = 55.0..85.0
            val pitchList = pitchRange.step(0.1)

            pitchList.forEach { pitch ->
                val rotation = Rotation(assumedYaw, pitch)
                castRotation(rotation, info) ?: return@forEach

                add(rotation)
            }
        }

        var optimalPitch = optimalPitch
        if (isDiagonal) optimalPitch++

        val assumedRotation = Rotation(assumedYaw, optimalPitch)

        // Check if the assumed rotation is ok
        if (castRotation(assumedRotation, info) != null) {
            return assumedRotation
        }

        val optimalRotation = when {
            // Placing supporting block
            info.placeSteps > 0 && !isDiagonal -> currentRotation

            // Placing base block
            else -> assumedRotation
        }

        // Otherwise selecting the most similar rotation
        val rotation = (angleScan + pointScan).minByOrNull { rotation ->
            optimalRotation dist rotation
        }.also {
            lastRotation = it
        }

        if (isDiagonal) {
            edjeDistance = -1.0
        }

        // Check the distance to the edge (stabilizes rotation)
        if (edjeDistance > 0 && edjeDistance < minRotateDist) return null

        return rotation
    }

    private fun SafeContext.tickPlacement(info: PlaceInfo) {
        // Check the distance to the edge
        if (edjeDistance > 0 && edjeDistance < minPlaceDist) return

        // Raycast the rotation
        var blockResult: BlockHitResult? = castRotation(currentRotation, info)

        // Use fallback hit vec for nonstrict ac's
        if (!interactionConfig.useRayCast && blockResult == null) {
            blockResult = BlockHitResult(info.hitVec, info.clickSide, info.clickPos, false)
        }

        // Run placement
        placeBlock(blockResult ?: return, Hand.MAIN_HAND, interactionConfig.swingHand)
        renderInfo.add(info to currentTime)
    }

    private fun SafeContext.updateSneaking() {
        sneakTicks--
        placeInfoAge++

        if (!safeWalk) return

        /*val vec = movementVector(y = -0.5) * player.moveDelta
        val predictedBox = player.boundingBox.offset(vec)
        val isNearLedge = world.isBlockSpaceEmpty(player, predictedBox)*/

        val sneak = lastRotation?.let {
            currentRotation dist it > YAW_THRESHOLD && player.isOnGround
        } ?: (sneakTicks > 0 && placeInfoAge < 4)

        if (sneak) sneakTicks = 3
    }

    private fun buildRenderer(event: RenderEvent.StaticESP) {
        val c = GuiSettings.primaryColor

        renderInfo.removeIf {
            val (info, time) = it

            val pos = info.placedPos.toFastVec()
            val seconds = (currentTime - time) / 1000.0

            val sides = buildSideMesh(pos) { meshPos ->
                renderInfo.any { it.first.placedPos.toFastVec() == meshPos }
            }

            val box = Box(info.placedPos)
            val alpha = transform(seconds, 0.0, 0.5, 1.0, 0.0).coerceIn(0.0, 1.0)

            event.renderer.build(
                box,
                c.multAlpha(0.3 * alpha),
                c.multAlpha(alpha),
                sides,
                DirectionMask.OutlineMode.AND
            )

            seconds > 1
        }
    }

    private fun assumeYawByDirection(moveYaw: Double): Double {
        val moveYawReversed = wrap(moveYaw + 180)

        return when (direction) {
            LookingDirection.FREE -> listOf(moveYawReversed)
            LookingDirection.ClAMPED -> yawList + diagonalYawList
            LookingDirection.STRAIGHT -> yawList
            LookingDirection.DIAGONAL -> diagonalYawList
        }.minBy { angleDifference(moveYawReversed, it) }
    }

    // Calculates the distance from the player to the edge of the block
    private fun SafeContext.distanceToEdge(pos: BlockPos, from: Vec3d = player.pos) =
        edgeOf(pos) dist Vec3d(from.x, pos.y.toDouble(), from.z)

    private fun SafeContext.edgeOf(pos: BlockPos): Vec3d {
        val x = player.pos.x.coerceIn(pos.x.toDouble(), pos.x.toDouble() + 1)
        val z = player.pos.z.coerceIn(pos.z.toDouble(), pos.z.toDouble() + 1)
        return Vec3d(x, pos.y.toDouble(), z)
    }

    // Checks if the rotation matches the placement requirements
    private fun castRotation(rotation: Rotation, info: PlaceInfo): BlockHitResult? {
        val blockResult = rotation.rayCast(interactionConfig.reach)?.blockResult ?: return null
        if (blockResult.blockPos != info.clickPos || blockResult.side != info.clickSide) return null
        return blockResult
    }
}
