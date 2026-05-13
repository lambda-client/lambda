/*
 * Copyright 2026 Lambda
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

package com.lambda.module.modules.movement

import baritone.api.pathing.goals.GoalGetToBlock
import com.lambda.config.Group
import com.lambda.config.applyEdits
import com.lambda.config.automation.AutomationConfig.Companion.setDefaultAutomationConfig
import com.lambda.context.SafeContext
import com.lambda.event.events.ClientEvent
import com.lambda.event.events.MovementEvent
import com.lambda.event.events.PacketEvent
import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.interaction.BaritoneHandler
import com.lambda.interaction.managers.rotating.IRotationRequest.Companion.rotationRequest
import com.lambda.interaction.material.StackSelection.Companion.select
import com.lambda.module.Module
import com.lambda.module.hud.Speedometer
import com.lambda.module.modules.movement.BetterFirework.canOpenElytra
import com.lambda.module.modules.movement.BetterFirework.canTakeoff
import com.lambda.module.modules.movement.BetterFirework.startFirework
import com.lambda.module.tag.ModuleTag
import com.lambda.threading.runSafe
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.SpeedUnit
import com.lambda.util.Timer
import com.lambda.util.extension.isElytraFlying
import com.lambda.util.math.dist
import com.lambda.util.math.flooredBlockPos
import com.lambda.util.math.isLoaded
import com.lambda.util.player.MovementUtils.addSpeed
import com.lambda.util.player.SlotUtils.hotbarStacks
import com.lambda.util.player.SlotUtils.inventoryStacks
import com.lambda.util.player.hasFirework
import com.lambda.util.world.raycast.InteractionMask
import com.lambda.util.world.raycast.RayCastUtils.blockResult
import com.lambda.util.world.raycast.RayCastUtils.rayCast
import net.minecraft.component.DataComponentTypes
import net.minecraft.entity.Entity
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket
import net.minecraft.network.packet.s2c.play.PlayerPositionLookS2CPacket
import net.minecraft.sound.SoundEvents
import net.minecraft.util.math.Vec3d
import java.lang.Math.toDegrees
import java.lang.Math.toRadians
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.time.Duration.Companion.seconds

object ElytraFly : Module(
    name = "ElytraFly",
    description = "Allows you to fly with an elytra",
    tag = ModuleTag.Movement,
) {
    @JvmStatic val mode by setting("Mode", FlyMode.Bounce)

    private val inventory by setting("Inventory", true, "Allow using fireworks from the players inventory") { mode == FlyMode.GrimControl }

    private val takeoff by setting("Takeoff", true, "Automatically jumps and initiates gliding") { mode == FlyMode.Bounce }
    private val autoPitch by setting("Auto Pitch", true, "Automatically pitches the players rotation down to bounce at faster speeds") { mode == FlyMode.Bounce }
    private val pitch by setting("Pitch", 80, 0..90, 1) { mode == FlyMode.Bounce && autoPitch }
    private val jump by setting("Jump", true, "Automatically jumps") { mode == FlyMode.Bounce }
    private val flagPause by setting("Flag Pause", 5, 0..100, 1, "How long to pause if the server flags you for a movement check", "ticks") { mode == FlyMode.Bounce }
    private val yMotion by setting("Y Motion", false, "Cancels the players y velocity to aid speed") { mode == FlyMode.Bounce }
    private val yMotionStartSpeed by setting("Y Motion Start Speed", 30, 5..40, 1, "bps") { mode == FlyMode.Bounce && yMotion }
    private val speedLimit by setting("Speed Limit", 110, 10..400, 1, "bps") { mode == FlyMode.Bounce && yMotion }

    private const val ObstaclePasserGroup = "Obstacle Passer"
    @Group(ObstaclePasserGroup) private val passObstacles by setting("Pass Obstacles", true, "Automatically paths around obstacles using baritone") { mode == FlyMode.Bounce }
    @Group(ObstaclePasserGroup) private val applyPauseAfterBaritone by setting("Apply Pause After Baritone", false, "Ticks the flag pause after baritone has finished pathing") { mode == FlyMode.Bounce && passObstacles }
    @Group(ObstaclePasserGroup) private val acceptableOffsetRange by setting("Acceptable Offset Range", 2.0, 0.1..5.0, 0.01, "Acceptable offset from the original flight line to allow when starting to fly again after passing obstacles") { mode == FlyMode.Bounce && passObstacles }
    @Group(ObstaclePasserGroup) private val obstacleLookAhead by setting("Obstacle Look-Ahead", 15, 0..50, 1, "Looks ahead of the player to see if obstacles are in the way") { mode == FlyMode.Bounce && passObstacles }
    @Group(ObstaclePasserGroup) private val directionStep by setting("Direction Step", 45.0, 0.0..180.0, 0.1, "The step size to use when locking the flight direction") { mode == FlyMode.Bounce && passObstacles }

    private val boostSpeed by setting("Boost", 0.00, 0.0..0.5, 0.005, description = "Speed to add when flying")
    private val rocketSpeed by setting("Rocket Speed", 0.0, 0.0..2.0, description = "Speed multiplier that the rocket gives you") { mode == FlyMode.Enhanced }

    private val mute by setting("Mute Elytra", false, "Mutes the elytra sound when gliding")

    private var startPos = Vec3d.ZERO
    private var jumpThisTick = false
    private var previouslyFlying: Boolean? = null
    private var passingToPos: Vec3d? = null
    private var glidePause = 0

    private var flipFlop = false
    private var lastDuration = 1.0
    private val fireworkTimer = Timer()

    init {
        setDefaultAutomationConfig {
            applyEdits {
                hideAllBlocksExcept(::inventoryConfig, ::rotationConfig)
            }
        }

        listen<TickEvent.Pre> {
            when (mode) {
                FlyMode.Bounce -> onTickBounce()
                FlyMode.GrimControl -> onTickGrimControl()
                else -> {}
            }
        }

        listen<TickEvent.Post> {
            if (glidePause > 0 && !applyPauseAfterBaritone) glidePause--
        }

        onEnable {
            startPos = player.pos
        }

        onDisable {
            passingToPos = null
            if (passObstacles) BaritoneHandler.cancel()
        }

        listen<PacketEvent.Receive.Pre> { event ->
            if (event.packet !is PlayerPositionLookS2CPacket) return@listen
            if (mode == FlyMode.Bounce && player.isGliding) {
                val snappedDir = getSnappedDir()
                val closestLinePoint = player.pos.findClosestPointOnLine(snappedDir)
                pathToValidPoint(closestLinePoint, snappedDir, true)
            }
        }

        listen<MovementEvent.InputUpdate> { event ->
            if (mode == FlyMode.Bounce && ((player.isGliding && jump) || jumpThisTick)) {
                event.input.jump()
                jumpThisTick = false
            }
        }

        listen<MovementEvent.Player.Pre> {
            if (player.isElytraFlying && !player.isUsingItem) {
                addSpeed(boostSpeed)
            }
        }

        listen<ClientEvent.Sound> { event ->
            if (!mute) return@listen
            if (event.sound.id != SoundEvents.ITEM_ELYTRA_FLYING.id) return@listen
            event.cancel()
        }
    }

    private fun SafeContext.onTickGrimControl() {
        if (!player.isGliding) return
        if (fireworkTimer.timePassed(lastDuration.seconds)) {
            findFirework()?.let {
                lastDuration = (it.get(DataComponentTypes.FIREWORKS)?.flightDuration ?: 1) * 0.5 + 0.5
                startFirework(inventory)
                fireworkTimer.reset()
            }
        }

        var vec = Vec3d.ZERO
        val yaw = player.yaw
        if (mc.options.forwardKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw))
        if (mc.options.backKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 180f))
        if (mc.options.leftKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw - 90f))
        if (mc.options.rightKey.isPressed) vec = vec.add(Vec3d.fromPolar(0f, yaw + 90f))
        if (mc.options.jumpKey.isPressed) vec = vec.add(Vec3d(0.0, 1.0, 0.0))
        if (mc.options.sneakKey.isPressed) vec = vec.add(Vec3d(0.0, -1.0, 0.0))
        if (vec.lengthSquared() < 1e-4 && player.hasFirework) {
            if (flipFlop) {
                flipFlop = false
                rotationRequest { rotation(0f, 0f) }
            } else {
                flipFlop = true
                rotationRequest { rotation(180f, 0f) }
            }
        } else {
            val rot = vec.yawAndPitch
            rotationRequest { rotation(rot.y, rot.x) }
        }.submit()
    }

    private fun SafeContext.findFirework(): ItemStack? {
        val stack = Items.FIREWORK_ROCKET.select()
        return stack.bestItemMatch(player.hotbarStacks) ?: if (inventory) stack.bestItemMatch(player.inventoryStacks) else null
    }

    private fun SafeContext.onTickBounce() {
        if (!BaritoneHandler.isActive) passingToPos = null

        val playerPos = player.pos
        if (passObstacles && playerPos.let { Vec3d(it.x, startPos.y, it.z) } dist startPos > 0.1) run obstacleChecks@{
            val snappedDir = getSnappedDir()
            val closestLinePoint = playerPos.findClosestPointOnLine(snappedDir)
            passingToPos?.let { passingTo ->
                if (passingTo.isObstructed(snappedDir)) {
                    pathToValidPoint(passingTo, snappedDir)
                }
                return
            }
            if (playerPos dist closestLinePoint <= acceptableOffsetRange) {
                if (playerPos.let { Vec3d(it.x, closestLinePoint.y, it.z) }.isObstructed(snappedDir))
                    pathToValidPoint(closestLinePoint, snappedDir)
                else return@obstacleChecks
            } else pathToValidPoint(closestLinePoint, snappedDir, true)

            return
        }

        if (glidePause > 0 && applyPauseAfterBaritone) {
            glidePause--
            return
        }

        if (autoPitch) rotationRequest { pitch(pitch.toFloat()) }.submit()

        if (!player.isGliding) {
            if (takeoff && player.canTakeoff) {
                if (player.canOpenElytra) {
                    player.startGliding()
                    startFlyPacket()
                } else jumpThisTick = true
            }
            return
        }

        if (!player.getFlag(Entity.GLIDING_FLAG_INDEX) || yMotion) {
            player.setFlag(Entity.GLIDING_FLAG_INDEX, true)
            startFlyPacket()
        }
    }

    private fun SafeContext.getSnappedDir(): Vec3d {
        val travelDiff = player.pos.subtract(startPos).normalize().let { Vec3d(it.x, 0.0, it.z) }
        return lockYawToStep(travelDiff)
    }

    context(safeContext: SafeContext)
    private fun pathToValidPoint(startSearchPos: Vec3d, dir: Vec3d, initialBlockedCheck: Boolean = false) {
        var skippingFirstCheck = !initialBlockedCheck
        var searchPos = startSearchPos
        while (skippingFirstCheck || searchPos.isObstructed(dir)) {
            searchPos = searchPos.add(dir.multiply(obstacleLookAhead.toDouble()))
            skippingFirstCheck = false
        }
        passTo(searchPos)
        safeContext.player.stopGliding()
        glidePause = flagPause
    }

    private fun passTo(pos: Vec3d) {
        passingToPos = pos
        BaritoneHandler.setGoalAndPath(GoalGetToBlock(pos.flooredBlockPos))
    }

    context(safeContext: SafeContext)
    private fun Vec3d.isObstructed(direction: Vec3d) =
        if (!isLoaded) false
        else {
            flooredBlockPos.down().let { downPos ->
                !safeContext.blockState(downPos).isSolidBlock(safeContext.world, downPos)
            } ||
                    rayCastObstructed(direction) ||
                    add(0.0, 1.0, 0.0).rayCastObstructed(direction) ||
                    add(0.0, 2.0, 0.0).rayCastObstructed(direction)
        }

    context(safeContext: SafeContext)
    private fun Vec3d.rayCastObstructed(direction: Vec3d) =
        safeContext.rayCast(
            this,
            direction,
            obstacleLookAhead.toDouble(),
            InteractionMask.Block
        )?.blockResult != null

    private fun Vec3d.findClosestPointOnLine(snappedDirection: Vec3d): Vec3d {
        val startToCurrent = subtract(startPos)
        val t = startToCurrent.dotProduct(snappedDirection) / snappedDirection.dotProduct(snappedDirection)
        return startPos.add(snappedDirection.multiply(t))
    }

    fun lockYawToStep(vector: Vec3d): Vec3d {
        val yaw = atan2(vector.z, vector.x)
        val yawDegrees = toDegrees(yaw)

        val normalizedYaw = (yawDegrees % 360.0 + 360.0) % 360.0

        val steps = normalizedYaw / directionStep
        val roundedSteps = steps.roundToInt()
        val lockedYawDegrees = roundedSteps * directionStep

        val normalizedLockedYawDegrees = (lockedYawDegrees % 360.0 + 360.0) % 360.0
        val lockedYaw = toRadians(normalizedLockedYawDegrees)

        val horizontalLength = hypot(vector.x, vector.z)
        val x = cos(lockedYaw) * horizontalLength
        val z = sin(lockedYaw) * horizontalLength

        return Vec3d(x, vector.y, z)
    }

    @JvmStatic
    fun getModifiedBounceVelocity(original: Vec3d) =
        runSafe {
            if (!yMotion || !player.isGliding || !player.isOnGround) return@runSafe original
            val speed = Speedometer.calculateSpeed(true, SpeedUnit.BlocksPerSecond)
            if (speed >= speedLimit) return@runSafe original
            if (speed <= yMotionStartSpeed) return@runSafe original
            Vec3d(original.x, 0.0, original.z)
        } ?: original

    private fun SafeContext.startFlyPacket() =
        connection.sendPacket(ClientCommandC2SPacket(player, ClientCommandC2SPacket.Mode.START_FALL_FLYING))

    @JvmStatic
    fun isGliding(): Boolean? = runSafe {
        val original: Boolean = player.getFlag(Entity.GLIDING_FLAG_INDEX)
        if (previouslyFlying == null) {
            previouslyFlying = original
            return@runSafe original
        }
        return if (
            isEnabled &&
            mode == FlyMode.Bounce &&
            previouslyFlying == true &&
            glidePause <= 0 &&
            !BaritoneHandler.isActive) true
        else {
            previouslyFlying = original
            original
        }
    }

    @JvmStatic
    fun boostRocket() = runSafe {
        val vec = player.rotationVector
        val velocity = player.velocity

        val d = 1.5 * if (mode == FlyMode.Enhanced) rocketSpeed else 1.0
        val e = 0.1 * if (mode == FlyMode.Enhanced) rocketSpeed else 1.0

        player.velocity = velocity.add(
            vec.x * e + (vec.x * d - velocity.x) * 0.5,
            vec.y * e + (vec.y * d - velocity.y) * 0.5,
            vec.z * e + (vec.z * d - velocity.z) * 0.5
        )
    }

    enum class FlyMode {
        Bounce,
        Enhanced,
        GrimControl
    }
}
