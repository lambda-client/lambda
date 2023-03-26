package com.lambda.client.module.modules.movement

import com.lambda.client.commons.extension.toRadian
import com.lambda.client.event.Phase
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ElytraTravelEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.boostedEntity
import com.lambda.client.mixin.extension.playerPosLookPitch
import com.lambda.client.mixin.extension.tickLength
import com.lambda.client.mixin.extension.timer
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.text.MessageSendHelper.sendChatMessage
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getGroundPos
import com.lambda.client.util.world.isLiquidBelow
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.item.EntityFireworkRocket
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.server.SPacketEntityMetadata
import net.minecraft.network.play.server.SPacketPlayerPosLook
import kotlin.math.*

object ElytraFlight : Module(
    name = "ElytraFlight",
    description = "Allows infinite and way easier Elytra flying",
    category = Category.MOVEMENT,
    modulePriority = 1000
) {
    val mode = setting("Mode", ElytraFlightMode.CONTROL)
    private val page by setting("Page", Page.GENERIC_SETTINGS)

    // region Generic Settings
    private val durabilityWarning by setting("Durability Warning", true, { page == Page.GENERIC_SETTINGS })
    private val threshold by setting("Warning Threshold", 5, 1..50, 1, { durabilityWarning && page == Page.GENERIC_SETTINGS }, description = "Threshold of durability to start sending warnings")
    private var autoLanding by setting("Auto Landing", false, { page == Page.GENERIC_SETTINGS })

    /* Acceleration */
    private val accelerateStartSpeed by setting("Start Speed", 100, 0..100, 5, { (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.CREATIVE || mode.value == ElytraFlightMode.PACKET) && page == Page.GENERIC_SETTINGS })
    private val accelerateTime by setting("Accelerate Time", 0.0f, 0.0f..20.0f, 0.25f, { (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.CREATIVE || mode.value == ElytraFlightMode.PACKET) &&  page == Page.GENERIC_SETTINGS })

    /* Spoof Pitch */
    private val spoofPitch by setting("Spoof Pitch", true, { (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.CREATIVE || mode.value == ElytraFlightMode.PACKET) && page == Page.GENERIC_SETTINGS })
    private val blockInteract by setting("Block Interact", false, { spoofPitch && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.CREATIVE || mode.value == ElytraFlightMode.PACKET) &&  page == Page.GENERIC_SETTINGS })
    private val forwardPitch by setting("Forward Pitch", -25, -90..90, 5, { spoofPitch && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.CREATIVE || mode.value == ElytraFlightMode.PACKET) &&  page == Page.GENERIC_SETTINGS })

    /* Extra */
    val elytraSounds by setting("Elytra Sounds", true, { page == Page.GENERIC_SETTINGS })
    private val swingSpeed by setting("Swing Speed", 1.0f, 0.0f..2.0f, 0.1f, { page == Page.GENERIC_SETTINGS && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.PACKET) })
    private val swingAmount by setting("Swing Amount", 0.8f, 0.0f..2.0f, 0.1f, { page == Page.GENERIC_SETTINGS && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.PACKET) })
    // endregion

    // region Takeoff Settings
    private val autoTakeoff by setting("Auto Takeoff", true, { page == Page.TAKEOFF_SETTINGS })
    private val minTakeoffHeight by setting("Min Takeoff Height", 1.2f, 0.0f..1.5f, 0.1f, { autoTakeoff && page == Page.TAKEOFF_SETTINGS })
    private val takeoffTimer by setting("Takeoff Timer", 0.1f, 0.1f..1.0f, 0.1f, { autoTakeoff && page == Page.TAKEOFF_SETTINGS })
    private val takeoffGlide by setting("Takeoff Glide", 0.02, 0.01..0.1, 0.01, { autoTakeoff && page == Page.TAKEOFF_SETTINGS })
    private val takeoffFreeze by setting("Takeoff Freeze", true, { autoTakeoff && page == Page.TAKEOFF_SETTINGS })
    // endregion

    // region Mode Settings
    /* Boost */
    private val strictBoost by setting("Strict Boost", true, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS })
    private val speedBoost by setting("Boost Amount", 2.3f, 0.0f..10.0f, 0.1f, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS })
    private val upSpeedBoost by setting("Up Speed B", 1.0f, 0.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS && !strictBoost })
    private val downSpeedBoost by setting("Down Speed B", 1.0f, 0.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS && !strictBoost })

    /* Control */
    private val boostPitchControl by setting("Base Boost Pitch", 20, 0..90, 5, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val ncpStrict by setting("NCP Strict", true, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val legacyLookBoost by setting("Legacy Look Boost", false, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val altitudeHoldControl by setting("Auto Control Altitude", false, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val dynamicDownSpeed by setting("Dynamic Down Speed", false, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val speedControl by setting("Speed C", 1.81f, 0.0f..10.0f, 0.1f, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val fallSpeedControl by setting("Fall Speed C", 0.00000000000003f, 0.0f..0.3f, 0.01f, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val downSpeedControl by setting("Down Speed C", 1.0f, 1.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.CONTROL && page == Page.MODE_SETTINGS })
    private val fastDownSpeedControl by setting("Dynamic Down Speed C", 2.0f, 1.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.CONTROL && dynamicDownSpeed && page == Page.MODE_SETTINGS })

    /* Strict */
    private val speedStrict by setting("Speed S", 2.0f, 1.5f..2.0f, 0.1f, { mode.value == ElytraFlightMode.STRICT && page == Page.MODE_SETTINGS })
    private val speedBoostStrict by setting("Speed Boost S", 2.3, 1.0..5.0, 0.1, { mode.value == ElytraFlightMode.STRICT && page == Page.MODE_SETTINGS })
    private val upPitchStrict by setting("Up Pitch S", 75.0, 15.0..90.0, 1.0, { mode.value == ElytraFlightMode.STRICT && page == Page.MODE_SETTINGS })
    private val downPitchStrict by setting("Down Pitch S", 75.0, 15.0..90.0, 1.0, { mode.value == ElytraFlightMode.STRICT && page == Page.MODE_SETTINGS })
    private val pitchSpeedStrict by setting("Pitch Speed S", 15.0, 1.0..30.0, 1.0, { mode.value == ElytraFlightMode.STRICT && page == Page.MODE_SETTINGS })

    /* Creative */
    private val speedCreative by setting("Speed CR", 1.8f, 0.0f..10.0f, 0.1f, { mode.value == ElytraFlightMode.CREATIVE && page == Page.MODE_SETTINGS })
    private val fallSpeedCreative by setting("Fall Speed CR", 0.00001f, 0.0f..0.3f, 0.01f, { mode.value == ElytraFlightMode.CREATIVE && page == Page.MODE_SETTINGS })
    private val upSpeedCreative by setting("Up Speed CR", 1.0f, 1.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.CREATIVE && page == Page.MODE_SETTINGS })
    private val downSpeedCreative by setting("Down Speed CR", 1.0f, 1.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.CREATIVE && page == Page.MODE_SETTINGS })

    /* Packet */
    private val speedPacket by setting("Speed P", 1.8f, 0.0f..20.0f, 0.1f, { mode.value == ElytraFlightMode.PACKET && page == Page.MODE_SETTINGS })
    private val fallSpeedPacket by setting("Fall Speed P", 0.00001f, 0.0f..0.3f, 0.01f, { mode.value == ElytraFlightMode.PACKET && page == Page.MODE_SETTINGS })
    private val downSpeedPacket by setting("Down Speed P", 1.0f, 0.1f..5.0f, 0.1f, { mode.value == ElytraFlightMode.PACKET && page == Page.MODE_SETTINGS })


    /* Vanilla */
    private val upPitchVanilla by setting("Up Pitch V", 30f, 0f..90f, 5f, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS })
    private val downPitchVanilla by setting("Down Pitch V", 0f, 0f..90f, 5f, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS })
    private val rocketPitchVanilla by setting("Rocket Pitch V", 50f, 0f..90f, 5f, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS })
    // endregion

    enum class ElytraFlightMode {
        BOOST, CONTROL, STRICT, CREATIVE, PACKET, VANILLA
    }

    private enum class Page {
        GENERIC_SETTINGS, TAKEOFF_SETTINGS, MODE_SETTINGS
    }

    // region used variables
    /* Generic states */
    private var elytraIsEquipped = false
    private var elytraDurability = 0
    private var outOfDurability = false
    private var wasInLiquid = false
    private var isFlying = false
    private var isPacketFlying = false
    private var isStandingStillH = false
    private var isStandingStill = false
    private var speedPercentage = 0.0f

    /* Control*/
    private var hoverTarget = -1.0
    private var packetYaw = 0.0f
    var packetPitch = 0.0f
    private var hoverState = false
    private var boostingTick = 0

    /* Vanilla */
    private var lastY = 0.0
    private var shouldDescend = false
    private var lastHighY = 0.0

    /* Strict */
    private var prevPitch = 0.0f
    private const val LOCK_THRESHOLD = 0.005
    // endregion

    // region Event Listeners
    init {
        safeListener<PacketEvent.Receive> {
            if (player.isSpectator || !elytraIsEquipped || elytraDurability <= 1 || !isFlying || mode.value == ElytraFlightMode.BOOST) return@safeListener
            if (it.packet is SPacketPlayerPosLook && mode.value != ElytraFlightMode.PACKET) {
                val packet = it.packet
                packet.playerPosLookPitch = player.rotationPitch
            }

            /* Cancels the elytra opening animation */
            if (it.packet is SPacketEntityMetadata && isPacketFlying) {
                val packet = it.packet
                if (packet.entityId == player.entityId) it.cancel()
            }
        }

        safeListener<PlayerMoveEvent> {
            if (player.isSpectator) return@safeListener
            stateUpdate()
            if (elytraIsEquipped && elytraDurability > 1) {
                if (autoLanding) {
                    landing()
                    return@safeListener
                }
                if (!isFlying && !isPacketFlying) {
                    takeoff()
                } else {
                    mc.timer.tickLength = 50.0f
                    player.isSprinting = false
                    when (mode.value) {
                        ElytraFlightMode.BOOST -> boostMode()
                        ElytraFlightMode.CONTROL -> controlMode()
                        ElytraFlightMode.STRICT -> strictMode()
                        ElytraFlightMode.CREATIVE -> creativeMode()
                        ElytraFlightMode.PACKET -> packetMode()
                        ElytraFlightMode.VANILLA -> vanillaMode()
                    }
                }
                spoofRotation()
            } else if (!outOfDurability) {
                reset(true)
            }
        }

        safeListener<ElytraTravelEvent> {
            if (mode.value != ElytraFlightMode.STRICT) return@safeListener

            when (it.phase) {
                Phase.PRE -> {
                    updatePitch()
                    prevPitch = player.rotationPitch
                    player.rotationPitch = packetPitch
                }
                Phase.POST -> {
                    player.rotationPitch = prevPitch
                }
                Phase.PERI -> {} // event doesn't have peri phase
            }
        }

    }
    // endregion

    //region Generic Functions
    private fun SafeClientEvent.stateUpdate() {
        /* Elytra Check */
        val armorSlot = player.inventory.armorInventory[2]
        elytraIsEquipped = armorSlot.item == Items.ELYTRA

        /* Elytra Durability Check */
        if (elytraIsEquipped) {
            val oldDurability = elytraDurability
            elytraDurability = armorSlot.maxDamage - armorSlot.itemDamage

            /* Elytra Durability Warning, runs when player is in the air and durability changed */
            if (!player.onGround && oldDurability != elytraDurability) {
                if (durabilityWarning && elytraDurability > 1 && elytraDurability < threshold * armorSlot.maxDamage / 100) {
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    sendChatMessage("$chatName Warning: Elytra has " + (elytraDurability - 1) + " durability remaining")
                } else if (elytraDurability <= 1 && !outOfDurability) {
                    outOfDurability = true
                    if (durabilityWarning) {
                        mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                        sendChatMessage("$chatName Elytra is out of durability, holding player in the air")
                    }
                }
            }
        } else elytraDurability = 0

        /* Holds player in the air if run out of durability */
        if (!player.onGround && elytraDurability <= 1 && outOfDurability) {
            holdPlayer()
        } else if (outOfDurability) outOfDurability = false /* Reset if players is on ground or replace with a new elytra */

        /* wasInLiquid check */
        if (player.isInWater || player.isInLava) {
            wasInLiquid = true
        } else if (player.onGround || isFlying || isPacketFlying) {
            wasInLiquid = false
        }

        /* Elytra flying status check */
        isFlying = player.isElytraFlying || (player.capabilities.isFlying && mode.value == ElytraFlightMode.CREATIVE)

        /* Movement input check */
        isStandingStillH = player.movementInput.moveForward == 0f && player.movementInput.moveStrafe == 0f
        isStandingStill = isStandingStillH && !player.movementInput.jump && !player.movementInput.sneak

        /* Reset acceleration */
        if (!isFlying || isStandingStill) speedPercentage = accelerateStartSpeed.toFloat()

        /* Modify leg swing */
        if (shouldSwing()) {
            player.prevLimbSwingAmount = player.limbSwingAmount
            player.limbSwing += swingSpeed
            val speedRatio = (player.speed / getSettingSpeed()).toFloat()
            player.limbSwingAmount += ((speedRatio * swingAmount) - player.limbSwingAmount) * 0.4f
        }
    }

    private fun SafeClientEvent.reset(cancelFlying: Boolean) {
        wasInLiquid = false
        isFlying = false
        isPacketFlying = false
        mc.timer.tickLength = 50.0f
        player.capabilities.flySpeed = 0.05f
        if (cancelFlying) player.capabilities.isFlying = false
    }

    /* Holds player in the air */
    private fun SafeClientEvent.holdPlayer() {
        mc.timer.tickLength = 50.0f
        player.setVelocity(0.0, -0.01, 0.0)
    }

    /* Auto landing */
    private fun SafeClientEvent.landing() {
        when {
            player.onGround -> {
                sendChatMessage("$chatName Landed!")
                autoLanding = false
                return
            }
            world.isLiquidBelow(player) -> {
                sendChatMessage("$chatName Liquid below, disabling.")
                autoLanding = false
            }
            LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff -> {
                holdPlayer()
            }
            player.capabilities.isFlying || !player.isElytraFlying || isPacketFlying -> {
                reset(true)
                takeoff()
                return
            }
            else -> {
                when {
                    player.posY > world.getGroundPos(player).y + 1.0 -> {
                        mc.timer.tickLength = 50.0f
                        player.motionY = max(min(-(player.posY - world.getGroundPos(player).y) / 20.0, -0.5), -5.0)
                    }
                    player.motionY != 0.0 -> { /* Pause falling to reset fall distance */
                        if (!mc.isSingleplayer) mc.timer.tickLength = 200.0f /* Use timer to pause longer */
                        player.motionY = 0.0
                    }
                    else -> {
                        player.motionY = -0.2
                    }
                }
            }
        }
        player.setVelocity(0.0, player.motionY, 0.0) /* Kills horizontal motion */
    }

    private fun SafeClientEvent.takeoff() {
        val closeToGround = player.posY <= world.getGroundPos(player).y + minTakeoffHeight && !wasInLiquid && !mc.isSingleplayer

        /* Pause Takeoff if server is lagging, player is in water/lava, or player is on ground */
        if (!autoTakeoff || (LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff) || player.onGround) {
            /* Holds player in the air if server is lagging and the distance is enough for taking fall damage */
            if (LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff && player.posY - world.getGroundPos(player).y > 4.0f) holdPlayer()
            reset(player.onGround)
            return
        }

        /* Run Takeoff if player is falling */
        if (player.motionY < -0.02) {
            if (closeToGround) {
                mc.timer.tickLength = 25.0f
                return
            }

            /* Cringe moment when you use elytra flight in single player world */
            if (!mc.isSingleplayer) {
                if (!wasInLiquid) {
                    if (takeoffFreeze) player.setVelocity(0.0, player.motionY, 0.0)
                    player.motionY = -takeoffGlide
                }

                mc.timer.tickLength = 50f / takeoffTimer
            }

            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING))
            hoverTarget = player.posY + 0.2
        }
    }

    /**
     *  Calculate yaw for control and packet mode
     */
    private fun SafeClientEvent.getYaw(): Double {
        val yawRad = calcMoveYaw()
        packetYaw = Math.toDegrees(yawRad).toFloat()
        return yawRad
    }

    /**
     * Calculate a speed with a non linear acceleration over time
     *
     * @return boostingSpeed if [boosting] is true, else return a accelerated speed.
     */
    private fun getSpeed(boosting: Boolean): Double {
        return when {
            boosting -> (if (ncpStrict) min(speedControl, 2.0f) else speedControl).toDouble()

            accelerateTime != 0.0f && accelerateStartSpeed != 100 -> {
                speedPercentage = min(speedPercentage + (100.0f - accelerateStartSpeed) / (accelerateTime * 20.0f), 100.0f)
                val speedMultiplier = speedPercentage / 100.0

                getSettingSpeed() * speedMultiplier * (cos(speedMultiplier * PI) * -0.5 + 0.5)
            }

            else -> getSettingSpeed().toDouble()
        }
    }

    private fun getSettingSpeed(): Float {
        return when (mode.value) {
            ElytraFlightMode.BOOST -> speedBoost
            ElytraFlightMode.CONTROL -> speedControl
            ElytraFlightMode.STRICT -> speedStrict
            ElytraFlightMode.CREATIVE -> speedCreative
            ElytraFlightMode.PACKET -> speedPacket
            ElytraFlightMode.VANILLA -> 1f

        }
    }

    private fun SafeClientEvent.setSpeed(yaw: Double, boosting: Boolean) {
        val acceleratedSpeed = getSpeed(boosting)
        player.setVelocity(sin(-yaw) * acceleratedSpeed, player.motionY, cos(yaw) * acceleratedSpeed)
    }
    // endregion

    // region Mode Functions
    /* Boost mode */
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

    // region Control Mode
    private fun SafeClientEvent.controlMode() {
        /* States and movement input */
        val currentSpeed = player.speed
        val moveUp = if (!legacyLookBoost) player.movementInput.jump else player.rotationPitch < -10.0f && !isStandingStillH
        val moveDown = if (InventoryMove.isEnabled && !InventoryMove.sneak && mc.currentScreen != null || moveUp) false else player.movementInput.sneak

        /* Dynamic down speed */
        val calcDownSpeed = if (dynamicDownSpeed) {
            val minDownSpeed = min(downSpeedControl, fastDownSpeedControl).toDouble()
            val maxDownSpeed = max(downSpeedControl, fastDownSpeedControl).toDouble()
            if (player.rotationPitch > 0) {
                player.rotationPitch / 90.0 * (maxDownSpeed - minDownSpeed) + minDownSpeed
            } else minDownSpeed
        } else downSpeedControl.toDouble()

        /* Hover */
        if (hoverTarget < 0.0 || moveUp) hoverTarget = player.posY else if (moveDown) hoverTarget = player.posY - calcDownSpeed
        hoverState = (if (hoverState) player.posY < hoverTarget else player.posY < hoverTarget - 0.1) && altitudeHoldControl

        /* Set velocity */
        if (!isStandingStillH || moveUp) {
            if ((moveUp || hoverState) && (currentSpeed >= 0.8 || player.motionY > 1.0)) {
                upwardFlight(currentSpeed, getYaw())
            } else { /* Runs when pressing wasd */
                packetPitch = forwardPitch.toFloat()
                player.motionY = -fallSpeedControl.toDouble()
                setSpeed(getYaw(), moveUp)
                boostingTick = 0
            }
        } else player.setVelocity(0.0, 0.0, 0.0) /* Stop moving if no inputs are pressed */

        if (moveDown) player.motionY = -calcDownSpeed /* Runs when holding shift */
    }

    private fun SafeClientEvent.upwardFlight(currentSpeed: Double, yaw: Double) {
        val multipliedSpeed = 0.128 * min(speedControl, 2.0f)
        val strictPitch = Math.toDegrees(asin((multipliedSpeed - sqrt(multipliedSpeed * multipliedSpeed - 0.0348)) / 0.12)).toFloat()
        val basePitch = if (ncpStrict && strictPitch < boostPitchControl && !strictPitch.isNaN()) -strictPitch
        else -boostPitchControl.toFloat()
        val targetPitch = if (player.rotationPitch < 0.0f) {
            max(player.rotationPitch * (90.0f - boostPitchControl.toFloat()) / 90.0f - boostPitchControl.toFloat(), -90.0f)
        } else -boostPitchControl.toFloat()

        packetPitch = if (packetPitch <= basePitch && boostingTick > 2) {
            if (packetPitch < targetPitch) packetPitch += 17.0f
            if (packetPitch > targetPitch) packetPitch -= 17.0f
            max(packetPitch, targetPitch)
        } else basePitch
        boostingTick++

        /* These are actually the original Minecraft elytra fly code lol */
        val pitch = Math.toRadians(packetPitch.toDouble())
        val targetMotionX = sin(-yaw) * sin(-pitch)
        val targetMotionZ = cos(yaw) * sin(-pitch)
        val targetSpeed = sqrt(targetMotionX * targetMotionX + targetMotionZ * targetMotionZ)
        val upSpeed = currentSpeed * sin(-pitch) * 0.04
        val fallSpeed = cos(pitch) * cos(pitch) * 0.06 - 0.08

        player.motionX -= upSpeed * targetMotionX / targetSpeed - (targetMotionX / targetSpeed * currentSpeed - player.motionX) * 0.1
        player.motionY += upSpeed * 3.2 + fallSpeed
        player.motionZ -= upSpeed * targetMotionZ / targetSpeed - (targetMotionZ / targetSpeed * currentSpeed - player.motionZ) * 0.1

        /* Passive motion loss */
        player.motionX *= 0.99
        player.motionY *= 0.98
        player.motionZ *= 0.99
    }
    // endregion

    // region Strict Mode
    private fun SafeClientEvent.strictMode() {
        val dir = Math.toRadians(player.rotationYaw.toDouble())

        if (player.movementInput.backKeyDown) {
            player.motionX = .0
            player.motionZ = .0
            return
        }

        if (player.motionY <= .0) { // ncp has bad speed checks when falling
            if (player.movementInput.forwardKeyDown) {
                player.motionX -= sin(dir) / 30.0 * speedBoostStrict
                player.motionZ += cos(dir) / 30.0 * speedBoostStrict
            }

            if (player.speed > speedStrict) { // limit speed
                player.motionX = -sin(dir) * speedStrict
                player.motionZ = cos(dir) * speedStrict
            }
        }

        if (shouldLockY()) player.motionY = 0.0
    }

    private fun SafeClientEvent.shouldLockY() =
        (player.speed > speedControl * 0.9) && // accelerating is close to finish
            player.motionY in -LOCK_THRESHOLD..0.0 // y motion is close to 0

    /* Pitch "interpolation" for strict mode */
    private fun SafeClientEvent.updatePitch() {
        var verticalMovement = 0
        if (player.movementInput.jump) verticalMovement++
        if (player.movementInput.sneak) verticalMovement--

        val pitchTo =
            if (verticalMovement == 0)
                calcForwardPitch()
            else if (verticalMovement > 0)
                -upPitchStrict
            else
                downPitchStrict

        if (packetPitch < pitchTo)
            packetPitch = min(packetPitch + pitchSpeedStrict, pitchTo).toFloat()
        else if (packetPitch > pitchTo)
            packetPitch = max(packetPitch - pitchSpeedStrict, pitchTo).toFloat()

        // more stable solution for UpdatedNCP (it flags when you looking up instantly after takeoff)
        packetPitch = packetPitch.coerceIn(-85.0f, 90.0f)
    }

    /* Returns pitch with smallest y lost. Needed for y locking */
    private fun calcForwardPitch() =
        -4.10 - ((2.0 - speedStrict) * 2.5)

    // endregion

    /* Creative Mode */
    private fun SafeClientEvent.creativeMode() {
        if (player.onGround) {
            reset(true)
            return
        }

        packetPitch = forwardPitch.toFloat()

        player.capabilities.isFlying = true

        val motionY = when {
            isStandingStill -> 0.0
            player.movementInput.jump -> upSpeedCreative.toDouble()
            player.movementInput.sneak -> -downSpeedCreative.toDouble()
            else -> -fallSpeedCreative.toDouble()
        }

        player.setVelocity(0.0, motionY, 0.0)
        if (!isStandingStillH) setSpeed(getYaw(), false)
    }

    /* Packet Mode */
    private fun SafeClientEvent.packetMode() {
        isPacketFlying = !player.onGround
        connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING))

        /* Set velocity */
        if (!isStandingStillH) { /* Runs when pressing wasd */
            setSpeed(getYaw(), false)
        } else player.setVelocity(0.0, 0.0, 0.0)
        player.motionY = (if (player.movementInput.sneak) -downSpeedPacket else -fallSpeedPacket).toDouble()
    }

    /* Vanilla Mode */
    private fun SafeClientEvent.vanillaMode() {
        val playerY = player.posY
        val lastShouldDescend = shouldDescend
        val isBoosted = world.getLoadedEntityList().any { it is EntityFireworkRocket && it.boostedEntity == player }

        shouldDescend = lastY > playerY && lastHighY - 60 < playerY

        packetPitch = if (isBoosted) {
            -rocketPitchVanilla
        } else if (shouldDescend) {
            if (!lastShouldDescend) {
                lastHighY = playerY
            }
            downPitchVanilla
        } else {
            -upPitchVanilla
        }

        lastY = playerY
    }
    // endregion

    fun shouldSwing(): Boolean {
        return isEnabled && isFlying && !autoLanding && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.PACKET)
    }

    private fun SafeClientEvent.spoofRotation() {
        if (player.isSpectator || !elytraIsEquipped || elytraDurability <= 1 || !isFlying) return

        var cancelRotation = false
        var rotation = Vec2f(player)

        if (autoLanding) {
            rotation = Vec2f(rotation.x, -20f)
        } else {
            when (mode.value) {
                ElytraFlightMode.CREATIVE, ElytraFlightMode.CONTROL, ElytraFlightMode.PACKET -> {
                    if (!isStandingStill && mode.value != ElytraFlightMode.CREATIVE) rotation = Vec2f(packetYaw, rotation.y)
                    if (spoofPitch) {
                        if (!isStandingStill) rotation = Vec2f(rotation.x, packetPitch)

                        /* Cancels rotation packets if player is not moving and not clicking */
                        cancelRotation = isStandingStill && ((!mc.gameSettings.keyBindUseItem.isKeyDown && !mc.gameSettings.keyBindAttack.isKeyDown && blockInteract) || !blockInteract)
                    }
                }
                ElytraFlightMode.VANILLA, ElytraFlightMode.STRICT -> {
                    rotation = Vec2f(rotation.x, packetPitch)
                }
                else -> {
                    // boost mode, we don't need to spoof angles
                }
            }
        }

        sendPlayerPacket {
            if (cancelRotation) cancelRotate() else rotate(rotation)
        }
    }

    init {
        onEnable {
            autoLanding = false
            speedPercentage = accelerateStartSpeed.toFloat() /* For acceleration */
            hoverTarget = -1.0 /* For control mode */
        }

        onDisable {
            runSafe { reset(true) }
        }

        /* Reset isFlying states when switching mode */
        mode.listeners.add {
            runSafe { reset(true) }
        }
    }
}
