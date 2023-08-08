package com.lambda.client.module.modules.movement

import baritone.api.pathing.goals.GoalXZ
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.manager.managers.TimerManager.resetTimer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.ViewLock
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.MovementUtils.setSpeed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getGroundPos
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec2f
import net.minecraft.util.math.Vec3d
import net.minecraftforge.client.event.InputUpdateEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.max
import kotlin.math.min

object ElytraFlight2b2t : Module(
    name = "ElytraFlight2b2t",
    description = "Go very fast on 2b2t",
    category = Category.MOVEMENT,
    modulePriority = 1000
) {
    private val takeoffTimerSpeed by setting("Takeoff Timer Tick Length", 395.0f, 100.0f..1000.0f, 5.0f,
        description = "How long each timer tick is during redeploy (ms). Lower length = faster timer. " +
            "Try increasing this if experiencing elytra timeout or rubberbands. This value is multiplied by 2 when setting timer", unit = "ms")
    private val baritoneBlockagePathing by setting("Baritone Blockage Pathing", true,
        description = "Use baritone to path around blockages on the highway.")
    private val baritonePathForwardBlocks by setting("Baritone Path Distance", 20, 1..50, 1,
        visibility = { baritoneBlockagePathing })
    private val baritoneEndDelayMs by setting("Baritone End Pathing Delay", 500, 0..2000, 50,
        visibility = { baritoneBlockagePathing }, unit = "ms")
    private val baritoneStartDelayMs by setting("Baritone Start Delay", 500, 0..2000, 50,
        visibility = { baritoneBlockagePathing }, unit = "ms")
    private val midairFallFly by setting("Mid-flight Packet Deploy", true,
        description = "Uses packets to redeploy when mid-flight.")
    private val autoFlyForward by setting("Auto Fly Forward", true,
        description = "Automatically move forward when flying.")
    private val rubberBandDetectionTime by setting("Rubberband Detection Time", 1110, 0..2000, 10,
        description = "Time period (ms) between which to detect rubberband teleports. Lower period = more sensitive.", unit = "ms")
    private val enableBoost by setting("Enable Boost", true,
        description = "Enable boost during mid-air flight.")
    private val boostDelayTicks by setting("Boost Delay", 11, 1..200, 1,
        visibility = { enableBoost }, unit = "ticks",
        description = "Number of ticks to wait before beginning boost")
    private val boostSpeedIncrease by setting("Boost Speed Increase", 0.65, 0.0..2.0, 0.01,
        visibility = { enableBoost },
        description = "Boost speed increase per tick (blocks per second / 2)")
    private val initialFlightSpeed by setting("Initial Flight Speed", 39.5, 35.0..80.0, 0.01,
        description = "Speed to start at for first successful deployment (blocks per second / 2).")
    private val speedMax by setting("Speed Max", 100.0, 40.0..100.0, 0.1,
        description = "Max flight speed (blocks per second / 2).")
    private val redeploySpeedDecreaseFactor by setting("Redeploy Speed Dec Factor", 1.1, 1.0..2.0, 0.01,
        description = "Decreases speed by a set factor during redeploys. Value is a divisor on current speed.")
    private val avoidUnloaded by setting("Avoid Unloaded", true,
        description = "Preserves speed while flying into unloaded chunks")
    private val autoViewLockManage by setting("Auto ViewLock Manage", true,
        description = "Automatically configures and toggles viewlock for straight flight on highways.")

    private const val TAKE_OFF_Y_VELOCITY = -0.16976 // magic number - do not question
    private const val MAGIC_PITCH = -2.52f
    private const val JUMP_DELAY = 10

    private var currentState = State.PAUSED
    private var isFlying = false
    private var isBaritoning = false

    private var timer = TickTimer(TimeUnit.TICKS)
    private var lastSPacketPlayerPosLook = Long.MIN_VALUE
    private var lastRubberband = Long.MIN_VALUE
    private var baritoneStartTime = 0L
    private var baritoneEndPathingTime = 0L

    private var shouldStartBoosting = false
    private var elytraIsEquipped = false
    private var wasInLiquid = false
    private var isStandingStill = false
    private var startedFlying = false
    private var stoppedFlying = false
    private var nextBlockMoveLoaded = true

    private var elytraDurability = 0
    private var flyTickCount = 0
    private var flyBlockedTickCount = 0
    private var currentFlightSpeed = 40.2

    private var flyPlayerLastPos = Vec3d.ZERO
    private var beforePathingPlayerPitchYaw = Vec2f.ZERO
    private var motionPrev = Vec2f(0.0f, 0.0f)

    private var scheduleBaritoneJob: Job? = null

    enum class State {
        FLYING, TAKEOFF, PAUSED, WALKING
    }

    override fun getHudInfo() = currentState.name

    init {
        onEnable {
            currentState = State.PAUSED
            timer.reset()
            shouldStartBoosting = false
            lastRubberband = Long.MIN_VALUE
            if (autoViewLockManage) configureViewLock()
        }

        onDisable {
            currentState = State.PAUSED
            resetFlightSpeed()
            BaritoneUtils.cancelEverything()
            shouldStartBoosting = false
            resetTimer()
            wasInLiquid = false
            isFlying = false
            if (autoViewLockManage) ViewLock.disable()
        }

        safeListener<ConnectionEvent.Disconnect> {
            disable()
        }

        safeListener<TickEvent.ClientTickEvent>(priority = 9999) {
            if (it.phase != TickEvent.Phase.END) return@safeListener
            when (currentState) {
                State.PAUSED -> {
                    val armorSlot = player.inventory.armorInventory[2]
                    elytraIsEquipped = armorSlot.item == Items.ELYTRA
                    if (!elytraIsEquipped) {
                        MessageSendHelper.sendChatMessage("No Elytra equipped")
                        disable()
                        return@safeListener
                    }
                    if (armorSlot.maxDamage <= 1) {
                        MessageSendHelper.sendChatMessage("Equipped Elytra broken or almost broken")
                        disable()
                        return@safeListener
                    }
                    currentState = State.TAKEOFF
                }
                State.WALKING -> {
                    if (autoViewLockManage && ViewLock.isEnabled) ViewLock.disable()
                    if (scheduleBaritoneJob?.isActive == true) return@safeListener
                    if (BaritoneUtils.isActive) {
                        isBaritoning = true
                        return@safeListener
                    }
                    // delay takeoff if we were pathing
                    if (isBaritoning) {
                        baritoneEndPathingTime = System.currentTimeMillis()
                        player.rotationPitch = beforePathingPlayerPitchYaw.x
                        player.rotationYaw = beforePathingPlayerPitchYaw.y
                        isBaritoning = false
                        return@safeListener
                    }
                    if (System.currentTimeMillis() - baritoneEndPathingTime < baritoneEndDelayMs) return@safeListener
                    currentState = State.TAKEOFF
                }
                State.TAKEOFF -> {
                    if (autoViewLockManage && ViewLock.isDisabled) ViewLock.enable()
                    resetTimer()
                    shouldStartBoosting = false
                    resetFlightSpeed()
                    if (baritoneBlockagePathing && player.onGround && timer.tick(JUMP_DELAY.toLong())) player.jump()
                    if ((withinRange(player.motionY)) && !player.isElytraFlying) {
                        timer.reset()
                        currentState = State.FLYING
                    } else if (midairFallFly && player.isElytraFlying) {
                        connection.sendPacket(CPacketPlayer(true))
                    }
                }
                State.FLYING -> {
                    if (autoViewLockManage && ViewLock.isDisabled) ViewLock.enable()
                    if (!player.isElytraFlying && flyTickCount++ > 30) {
                        pathForward()
                        currentState = State.WALKING
                    } else flyTickCount = 0
                    val playerCurrentPos = player.positionVector
                    if (!avoidUnloaded || (avoidUnloaded && nextBlockMoveLoaded)) {
                        if (playerCurrentPos.distanceTo(flyPlayerLastPos) < 2.0) {
                            if (flyBlockedTickCount++ > 20) {
                                pathForward()
                                currentState = State.WALKING
                            }
                        } else flyBlockedTickCount = 0
                    }
                    flyPlayerLastPos = playerCurrentPos
                    if (!enableBoost) return@safeListener
                    if (shouldStartBoosting) {
                        if (avoidUnloaded) {
                            if (nextBlockMoveLoaded && isFlying) {
                                setFlightSpeed(currentFlightSpeed + boostSpeedIncrease)
                            }
                        } else setFlightSpeed(currentFlightSpeed + boostSpeedIncrease)
                    } else if (timer.tick(boostDelayTicks, true)) shouldStartBoosting = true
                }
            }
        }

        safeListener<PacketEvent.Receive> {
            if (it.packet !is SPacketPlayerPosLook || currentState != State.FLYING) return@safeListener
            timer.reset()
            if (System.currentTimeMillis() - lastSPacketPlayerPosLook < rubberBandDetectionTime.toLong()) {
                resetFlightSpeed()
                shouldStartBoosting = false
                resetTimer()
                wasInLiquid = false
                isFlying = false
                currentState = if (baritoneBlockagePathing) {
                    pathForward()
                    State.WALKING
                } else State.TAKEOFF
                lastRubberband = System.currentTimeMillis()
            }
            lastSPacketPlayerPosLook = System.currentTimeMillis()
        }

        safeListener<PacketEvent.Send> {
            if (avoidUnloaded && !nextBlockMoveLoaded && it.packet is CPacketPlayer) it.cancel()
        }

        safeListener<PlayerTravelEvent> {
            stateUpdate()
            if (currentState == State.FLYING && elytraIsEquipped && elytraDurability > 1) {
                if (stoppedFlying) setFlightSpeed(currentFlightSpeed / redeploySpeedDecreaseFactor)
                if (isFlying) {
                    resetTimer()
                    player.isSprinting = false
                } else takeoff(it)
            }
            // rotation spoof also kicks us out of elytra during glide takeoffs due to rotation not matching flight speed
            spoofRotation()
        }

        safeListener<PlayerMoveEvent> {
            if (currentState == State.FLYING) {
                if (avoidUnloaded) {
                    if (nextBlockMoveLoaded && !world.isBlockLoaded(BlockPos(player.posX + it.x, player.posY, player.posZ + it.z), false)) {
                        nextBlockMoveLoaded = false
                        motionPrev = Vec2f(it.x.toFloat(), it.z.toFloat())
                        setSpeed(0.0)
                        player.motionY = 0.0
                        return@safeListener
                    } else if (!nextBlockMoveLoaded) {
                        if (!world.isBlockLoaded(BlockPos(player.posX + motionPrev.x, 1.0, player.posZ + motionPrev.y), false)) {
                            setSpeed(0.0)
                            player.motionY = 0.0
                            return@safeListener
                        }
                    }
                }
                setSpeed(currentFlightSpeed / 10.0)
                player.motionY = 0.0
            }
            nextBlockMoveLoaded = true
        }

        safeListener<InputUpdateEvent> {
            if (autoFlyForward && (currentState == State.FLYING || currentState == State.TAKEOFF) && !player.onGround) {
                it.movementInput.moveStrafe = 0.0f
                it.movementInput.moveForward = 1.0f
            }
        }
    }

    private fun withinRange(motion: Double) = motion >= TAKE_OFF_Y_VELOCITY - 0.05 && motion <= TAKE_OFF_Y_VELOCITY + 0.05

    private fun resetFlightSpeed() {
        setFlightSpeed(initialFlightSpeed)
    }

    private fun setFlightSpeed(speed: Double) {
        currentFlightSpeed = max(initialFlightSpeed, min(speed, speedMax))
    }

    private fun SafeClientEvent.stateUpdate() {
        /* Elytra Check */
        val armorSlot = player.inventory.armorInventory[2]
        elytraIsEquipped = armorSlot.item == Items.ELYTRA

        /* Elytra Durability Check */
        elytraDurability = if (elytraIsEquipped) {
            armorSlot.maxDamage - armorSlot.itemDamage
        } else 0

        /* wasInLiquid check */
        if (player.isInWater || player.isInLava) {
            wasInLiquid = true
        } else if (player.onGround || isFlying) {
            wasInLiquid = false
        }

        /* Elytra flying status check */
        startedFlying = !isFlying && player.isElytraFlying
        stoppedFlying = isFlying && !player.isElytraFlying
        isFlying = player.isElytraFlying

        /* Movement input check */
        val isStandingStillH = player.movementInput.moveForward == 0f && player.movementInput.moveStrafe == 0f
        isStandingStill = isStandingStillH && !player.movementInput.jump && !player.movementInput.sneak
    }

    private fun SafeClientEvent.takeoff(event: PlayerTravelEvent) {
        val timerSpeed = takeoffTimerSpeed
        val height = 0.1
        val closeToGround = player.posY <= world.getGroundPos(player).y + height && !wasInLiquid && !mc.isSingleplayer

        if (player.motionY >= -0.02) return
        if (closeToGround) {
            resetTimer()
            return
        }
        if (!wasInLiquid && !mc.isSingleplayer) {
            event.cancel()
            player.setVelocity(0.0, -0.02, 0.0)
        }

        if (!mc.isSingleplayer) modifyTimer(timerSpeed * 2.0f)
        connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING))
    }

    private fun SafeClientEvent.spoofRotation() {
        if (player.isSpectator || !elytraIsEquipped || elytraDurability <= 1 || !isFlying) return
        var rotation = com.lambda.client.util.math.Vec2f(player)
        if (!isStandingStill) rotation = com.lambda.client.util.math.Vec2f(rotation.x, MAGIC_PITCH)
        /* Cancels rotation packets if player is not moving and not clicking */
        val cancelRotation = isStandingStill
            && ((!mc.gameSettings.keyBindUseItem.isKeyDown && !mc.gameSettings.keyBindAttack.isKeyDown))
        sendPlayerPacket {
            if (cancelRotation) {
                cancelRotate()
            } else {
                rotate(rotation)
            }
        }
    }

    private fun SafeClientEvent.pathForward() {
        beforePathingPlayerPitchYaw = player.pitchYaw
        if (scheduleBaritoneJob?.isActive == true) return
        baritoneStartTime = System.currentTimeMillis()
        scheduleBaritoneJob = defaultScope.launch {
            delay(baritoneStartDelayMs.toLong())
            BaritoneUtils.primary?.playerContext?.let {
                BaritoneUtils.primary?.customGoalProcess?.setGoalAndPath(GoalXZ.fromDirection(
                    it.playerFeetAsVec(),
                    it.player().rotationYawHead,
                    baritonePathForwardBlocks.toDouble()
                ))
            }
        }
    }

    private fun configureViewLock() {
        ViewLock.mode.value = ViewLock.Mode.TRADITIONAL
        ViewLock.yaw.value = true
        ViewLock.autoYaw.value = true
        ViewLock.hardAutoYaw.value = true
        ViewLock.disableMouseYaw.value = true
        ViewLock.yawSlice.value = 8
        ViewLock.pitch.value = false
    }
}
