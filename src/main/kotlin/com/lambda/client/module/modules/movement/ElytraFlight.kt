package com.lambda.client.module.modules.movement

import com.lambda.client.commons.extension.toDegree
import com.lambda.client.commons.extension.toRadian
import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ElytraTravelEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.boostedEntity
import com.lambda.client.mixin.extension.tickLength
import com.lambda.client.mixin.extension.timer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.client.util.EntityUtils.isInOrAboveLiquid
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.isInputting
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getGroundPos
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.item.EntityFireworkRocket
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.server.SPacketPlayerPosLook
import kotlin.math.*


object ElytraFlight : Module(
    name = "ElytraFlight",
    description = "Allows infinite and way easier Elytra flying",
    category = Category.MOVEMENT,
    modulePriority = 1000
) {
    val mode by setting("Mode", Mode.CONTROL).also {
        /* Revert states when player is changing a mode */
        it.listeners.add { reset() }
    }
    private val page by setting("Page", Page.GENERIC_SETTINGS)

    // region Generic Settings
    /* Takeoff */
    private val autoTakeoff by setting("Auto Takeoff", true, { page == Page.GENERIC_SETTINGS })
    private val takeoffHeight by setting("Takeoff Height", 1.2, 0.2..1.5, 0.1, { autoTakeoff && page == Page.GENERIC_SETTINGS })
    private val highPingOptimize by setting("High Ping Optimize", false, { autoTakeoff && page == Page.GENERIC_SETTINGS })

    /* Extra */
    private val durabilityWarning by setting("Durability Warning", true, { page == Page.GENERIC_SETTINGS })
    private val threshold by setting("Warning Threshold", 5, 1..50, 1, { durabilityWarning && page == Page.GENERIC_SETTINGS }, description = "Threshold of durability to start sending warnings")
    val elytraSounds by setting("Elytra Sounds", true, { page == Page.GENERIC_SETTINGS })
    private val swingSpeed by setting("Swing Speed", 1f, 0.80f..2f, 0.1f, { page == Page.GENERIC_SETTINGS })
    private val swingAmount by setting("Swing Amount", 1f, 0f..2f, 0.1f, { page == Page.GENERIC_SETTINGS })
    // endregion

    // region Mode Settings
    /* Boost */
    private val strictBoost by setting("Strict Boost", true, { mode == Mode.BOOST && page == Page.MODE_SETTINGS })
    private val speedBoost by setting("Speed B", 2.3, 0.1..10.0, 0.1, { mode == Mode.BOOST && page == Page.MODE_SETTINGS })
    private val upSpeedBoost by setting("Up Speed B", 1.0, 0.0..5.0, 0.1, { mode == Mode.BOOST && page == Page.MODE_SETTINGS && !strictBoost })
    private val downSpeedBoost by setting("Down Speed B", 1.0, 0.0..5.0, 0.1, { mode == Mode.BOOST && page == Page.MODE_SETTINGS && !strictBoost })

    /* Control */
    private val speedControl by setting("Speed C", 2.0, 0.0..10.0, 0.1, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val fallSpeedControl by setting("Fall Speed C", 0.0, 0.0..0.1, 0.01, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val downSpeedControl by setting("Down Speed C", 1.0, 0.0..5.0, 0.05, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val accelerate by setting("Accelerate Speed", false, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val accelerateStartSpeed by setting("Start Speed", 100.0, 0.0..100.0, 5.0, { accelerate && mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val accelerateTime by setting("Accelerate Time", 0f, 0f..20f, 0.25f, { accelerate && mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val dynamicAcceleration by setting("Dynamic Acceleration", false, { accelerate && mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val blockInteract by setting("Block Interact", false, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val forwardPitchControl by setting("Forward Pitch", -25f, -90f..90f, 5f, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })
    private val upPitchControl by setting("Up Pitch C", 75f, 15f..90f, 1f, { mode == Mode.CONTROL && page == Page.MODE_SETTINGS })

    /* Strict */
    private val upPitchStrict by setting("Up Pitch S", 75f, 15f..90f, 1f, { mode == Mode.STRICT && page == Page.MODE_SETTINGS })
    private val downPitchStrict by setting("Down Pitch S", 85f, 15f..90f, 1f, { mode == Mode.STRICT && page == Page.MODE_SETTINGS })

    /* Vanilla */
    private val fireworkSync by setting("Firework Sync", true, { mode == Mode.VANILLA && page == Page.MODE_SETTINGS }, description = "Fixes firework rubberband induced velocity desync")
    private val forwardPitchVanilla by setting("Forward Pitch V", -15f, -45f..45f, 1f, { mode == Mode.VANILLA && page == Page.MODE_SETTINGS })
    private val upPitchVanilla by setting("Up Pitch V", 75f, 0f..90f, 1f, { mode == Mode.VANILLA && page == Page.MODE_SETTINGS })
    private val downPitchVanilla by setting("Down Pitch V", 90f, 0f..90f, 1f, { mode == Mode.VANILLA && page == Page.MODE_SETTINGS })

    /* Settings used in multiple modes */
    private val pitchSpeed by setting("Pitch Speed", 15f, 1f..40f, 1f, { arrayListOf(Mode.CONTROL, Mode.STRICT, Mode.VANILLA).contains(mode) && page == Page.MODE_SETTINGS })
    private val upwardBoostAmount by setting("Upward Boost Amount", 1.0, 0.0..1.0, 0.02, { arrayListOf(Mode.CONTROL, Mode.STRICT).contains(mode) && page == Page.MODE_SETTINGS })
    // endregion

    enum class Mode(override val displayName: String, val move: SafeClientEvent.() -> Unit) : DisplayEnum {
        BOOST("Boost", { boostMode() }),
        CONTROL("Control", { controlMode() }),
        STRICT("Strict", { strictMode() }),
        VANILLA("Vanilla", { }) /* Don't modify motion. Only manage angles */
    }

    private enum class Page {
        GENERIC_SETTINGS, MODE_SETTINGS
    }

    // region States & other
    /* Generic states */
    private var elytraIsEquipped = false
    private var elytraDurability = 0
    private var outOfDurability = false
    private var speedPercentage = 0.0

    private var verticalDirection = 0
    private val SafeClientEvent.isFlying get() = player.isElytraFlying
    private val SafeClientEvent.boostedFireworks: List<EntityFireworkRocket> get() =
        world.loadedEntityList
            .filterIsInstance<EntityFireworkRocket>()
            .filter { it.boostedEntity == player }

    /* For manipulating angles in travel event */
    private var prevYaw = 0.0f
    private var prevPitch = 0.0f

    var packetYaw = 0f
    var packetPitch = 0f

    /* Strict */
    private const val MAX_SPEED = 2.0
    private const val FORWARD_PITCH = -4.09f
    private const val LOCK_THRESHOLD = 0.005
    // endregion

    // region Event Listeners
    init {
        safeListener<PlayerMoveEvent> {
            if (player.isSpectator) return@safeListener
            stateUpdate()
            if (elytraIsEquipped && elytraDurability > 1) {
                if (!isFlying) {
                    takeoff()
                } else {
                    mc.timer.tickLength = 50.0f
                    player.isSprinting = false
                    mode.move(this)
                }
                spoofRotation()
            } else if (!outOfDurability) reset()
        }

        safeListener<ElytraTravelEvent> {
            if (mode != Mode.STRICT && mode != Mode.CONTROL && mode != Mode.VANILLA) return@safeListener

            /* Modify leg swing */
            if (shouldSwing()) {
                player.prevLimbSwingAmount = player.limbSwingAmount
                player.limbSwing += swingSpeed
                val speedRatio = (player.speed / speedControl).toFloat()
                player.limbSwingAmount += ((speedRatio * swingAmount) - player.limbSwingAmount) * 0.4f
            }

            val shouldEditYaw = mode == Mode.STRICT || mode == Mode.VANILLA
            val shouldEditPitch = mode == Mode.STRICT || mode == Mode.VANILLA || mode == Mode.CONTROL

            /* Angle spoofing */
            when (it.phase) {
                Phase.PRE -> {
                    /* Update */
                    val dir = calcMoveYaw()

                    packetYaw = dir.toDegree().toFloat()
                    updatePitch(getTravelPitch())

                    /* Save prev states */
                    prevYaw = player.rotationYaw
                    prevPitch = player.rotationPitch

                    /* Spoof */
                    if (shouldEditYaw) player.rotationYaw = packetYaw
                    if (shouldEditPitch) player.rotationPitch = packetPitch
                }
                Phase.POST -> {
                    /* Revert */
                    player.rotationYaw = prevYaw
                    player.rotationPitch = prevPitch
                }
                Phase.PERI -> {} /* event doesn't have peri phase */
            }
        }

        safeListener<PacketEvent.Receive> { event ->
            if (player.isSpectator ||
                player.isInOrAboveLiquid ||
                !elytraIsEquipped ||
                elytraDurability <= 1 ||
                !isFlying
            ) return@safeListener

            when (event.packet) {
                is SPacketPlayerPosLook -> {
                    when (mode) {
                        Mode.CONTROL -> { /* Reset acceleration when ac setbacks player */
                            speedPercentage = min(accelerateStartSpeed, speedPercentage)
                        }
                        Mode.VANILLA -> { /* Remove fireworks if they cause setbacking */
                            if (fireworkSync) boostedFireworks.forEach { world.removeEntity(it) }
                        }
                        else -> {}
                    }
                }
            }
        }

        /* I'll just leave it here */
        onEnable { reset() }
        onDisable { reset() }
    }
    // endregion

    //region Generic Functions
    private fun SafeClientEvent.stateUpdate() {
        /* Slot Check */
        val armorSlot = player.inventory.armorInventory[2]
        elytraIsEquipped = armorSlot.item == Items.ELYTRA

        /* Durability Check */
        if (elytraIsEquipped) {
            val oldDurability = elytraDurability
            elytraDurability = armorSlot.maxDamage - armorSlot.itemDamage

            /* Durability Warning, runs when player is in the air and durability changed */
            if (!player.onGround && oldDurability != elytraDurability) {
                if (durabilityWarning && elytraDurability > 1 && elytraDurability < threshold * armorSlot.maxDamage / 100) {
                    warn("Warning: Elytra has ${elytraDurability - 1} durability remaining")
                } else if (elytraDurability <= 1 && !outOfDurability) {
                    outOfDurability = true
                    if (durabilityWarning) warn("Elytra is out of durability, holding player in the air")
                }
            }
        } else elytraDurability = 0

        /* Holds player in the air if run out of durability */
        if (!player.onGround && elytraDurability <= 1 && outOfDurability) {
            holdPlayer()
        } else outOfDurability = false /* Reset if player is on ground or replace with a new elytra */

        /* Update vertical direction */
        verticalDirection = 0
        if (player.movementInput.jump) verticalDirection++
        if (player.movementInput.sneak) verticalDirection--

        /* Reset acceleration */
        if (!isFlying || !isInputting || player.collidedHorizontally)
            speedPercentage = accelerateStartSpeed
    }

    private fun SafeClientEvent.warn(message: String) {
        val sound = PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f)
        mc.soundHandler.playSound(sound)
        sendChatMessage("$chatName $message")
    }

    private fun SafeClientEvent.holdPlayer() {
        mc.timer.tickLength = 50.0f
        player.setVelocity(0.0, -0.01, 0.0)
    }

    private fun SafeClientEvent.takeoff() {
        val distanceToGround = player.posY - world.getGroundPos(player).y

        /* Pause Takeoff if server is lagging, or player is on or close to ground, or player is in liquid */
        if (!autoTakeoff || (LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff) || player.onGround || distanceToGround < takeoffHeight || player.isInOrAboveLiquid) {
            /* Holds player in the air if server is lagging and the distance is enough for taking fall damage */
            if (LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff && distanceToGround > 4.0f) holdPlayer()
            reset()
            return
        }

        /* Run Takeoff if player is falling */
        if (player.motionY >= 0.0) return

        mc.timer.tickLength = 500f
        player.setVelocity(0.0, 0.0, 0.0)

        /* Glide when player is close to ground */
        if (distanceToGround < 0.3) player.motionY = -0.02

        /* Sending a lot of START_FALL_FLYING packets triggers anticheat */
        if (highPingOptimize && player.ticksExisted % 3 != 0) return

        val packet = CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING)
        connection.sendPacket(packet)
    }

    private fun getSpeed(): Double {
        val shouldAccelerate = accelerateTime != 0.0f && accelerateStartSpeed != 100.0 && accelerate

        return if (shouldAccelerate) {
            speedPercentage = min(speedPercentage + (100.0f - accelerateStartSpeed) / (accelerateTime * 20.0), 100.0)
            val speedMultiplier = speedPercentage / 100.0
            val dynamicSpeedMultiplier = if (dynamicAcceleration) (cos(speedMultiplier * PI) * -0.5 + 0.5) else 1.0

            speedControl * speedMultiplier * dynamicSpeedMultiplier
        } else speedControl
    }

    private fun getTravelPitch(): Float {
        return when (mode) {
            Mode.CONTROL -> {
                if (verticalDirection > 0 && isInputting)
                    -upPitchControl
                else
                    forwardPitchControl
            }
            Mode.STRICT -> {
                when (verticalDirection) {
                    0 -> FORWARD_PITCH
                    1 -> -upPitchStrict
                    else -> downPitchStrict
                }
            }
            Mode.VANILLA -> {
                when (verticalDirection) {
                    0 -> forwardPitchVanilla
                    1 -> -upPitchVanilla
                    else -> downPitchVanilla
                }
            }
            else -> 0.0f
        }
    }

    private fun updatePitch(pitchTo: Float) {
        packetPitch = (
            if (packetPitch < pitchTo)
                min(packetPitch + pitchSpeed, pitchTo)
            else if (packetPitch > pitchTo)
                max(packetPitch - pitchSpeed, pitchTo)
            else packetPitch).coerceIn(-90f, 90f)
    }

    private fun SafeClientEvent.calcBoostAmount(): Double {
        /* NCP has bad speed checks when falling */
        if (player.motionY <= 0.0) return 0.09

        /* Boost factor for upward movement based on speed and pitch */
        var upwardBoostFactor = if (player.speed > 1.5) 0.03 else 0.0
        if (player.rotationPitch < -40f) upwardBoostFactor *= 1.9

        return upwardBoostFactor * upwardBoostAmount
    }

    private fun SafeClientEvent.shouldLockY() =
        (player.speed > 1.95) && // accelerating is close to finish
            player.motionY in -LOCK_THRESHOLD..0.0 // y motion is close to 0
    // endregion

    // region Mode Functions
    private fun SafeClientEvent.boostMode() {
        val dir = player.rotationYaw.toDouble().toRadian()

        if (strictBoost) {
            if (player.motionY >= .0) return
            if (player.movementInput.forwardKeyDown) {
                player.motionX -= sin(dir) * speedBoost / 30.0
                player.motionZ += cos(dir) * speedBoost / 30.0
            }

            if (player.speed > 2.0) { // limit speed
                player.motionX = -sin(dir) * 2.0
                player.motionZ = cos(dir) * 2.0
            }
        } else {
            // non strict boost
            player.motionX -= player.movementInput.moveForward * sin(dir) * speedBoost / 30.0
            player.motionZ += player.movementInput.moveForward * cos(dir) * speedBoost / 30.0
            if (player.movementInput.jump) player.motionY += upSpeedBoost / 15 else if (player.movementInput.sneak) player.motionY -= downSpeedBoost / 15
        }
    }

    private fun SafeClientEvent.controlMode() {
        val dir = calcMoveYaw()

        if (verticalDirection > 0 && isInputting) {
            /* Makes motion difference smaller and upflying more stable for UpdatedNCP */
            speedPercentage = (player.speed / speedControl).coerceIn(0.0, 1.0) * 100.0

            /* Boost speed */
            player.motionX -= sin(dir) * calcBoostAmount()
            player.motionZ += cos(dir) * calcBoostAmount()

            /* Let mc physic engine make upward flying stable */
            return
        }

        var motionY = 0.0
        if (isInputting) motionY = -fallSpeedControl
        if (verticalDirection < 0) motionY = -downSpeedControl

        player.setVelocity(0.0, motionY, 0.0)

        if (!isInputting) return

        /* Force update pitch without acceleration while moving forward */
        packetPitch = forwardPitchControl

        /* Move */
        player.motionX = -sin(dir) * getSpeed()
        player.motionZ = cos(dir) * getSpeed()
    }

    private fun SafeClientEvent.strictMode() {
        val dir = player.rotationYaw.toRadian().toDouble()

        if (!isInputting) {
            player.motionX = .0
            player.motionZ = .0
            return
        }

        if (shouldLockY()) player.motionY = .0

        /* Boost speed */
        player.motionX -= sin(dir) * calcBoostAmount()
        player.motionZ += cos(dir) * calcBoostAmount()

        if (player.speed < MAX_SPEED) return

        /* Limit speed */
        player.motionX = -sin(dir) * MAX_SPEED
        player.motionZ = cos(dir) * MAX_SPEED
    }
    // endregion

    /* Firework velocity desync fix */
    @JvmStatic
    fun shouldModify() = isEnabled &&
        mode == Mode.VANILLA &&
        fireworkSync &&
        runSafeR { boostedFireworks.isNotEmpty() } == true

    fun shouldSwing(): Boolean {
        return isEnabled && mc.player.isElytraFlying && mode == Mode.CONTROL
    }

    private fun SafeClientEvent.spoofRotation() {
        if (player.isSpectator || !elytraIsEquipped || elytraDurability <= 1 || !isFlying) return

        var cancelRotation = false
        var rotation = Vec2f(player)

        when (mode) {
            Mode.CONTROL -> {
                if (isInputting)
                    rotation = Vec2f(packetYaw, packetPitch)
                else
                    /* Cancels rotation packets if player is not moving and not clicking */
                    cancelRotation = verticalDirection != -1 && ((!mc.gameSettings.keyBindUseItem.isKeyDown && !mc.gameSettings.keyBindAttack.isKeyDown && blockInteract) || !blockInteract)
            }
            Mode.STRICT, Mode.VANILLA -> {
                rotation = Vec2f(packetYaw, packetPitch)
            }
            else -> {
                /* we don't need to spoof angles */
            }
        }

        sendPlayerPacket {
            if (cancelRotation) cancelRotate() else rotate(rotation)
        }
    }

    private fun reset() {
        /* We need to reset pitch because we interpolating it */
        packetPitch = when (mode) {
            Mode.CONTROL -> forwardPitchControl
            Mode.STRICT -> FORWARD_PITCH
            else -> { 0f }
        }

        speedPercentage = accelerateStartSpeed /* Reset acceleration progress */
        mc.timer.tickLength = 50.0f
    }

    override fun getHudInfo() = mode.displayName
}