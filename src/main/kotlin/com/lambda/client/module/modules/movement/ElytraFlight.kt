package com.lambda.client.module.modules.movement

import com.lambda.client.commons.extension.toRadian
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.*
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.LagNotifier
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.MovementUtils.speed
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.*
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
import net.minecraft.item.ItemFirework
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerTryUseItem
import net.minecraft.network.play.server.SPacketEntityMetadata
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.EnumHand
import net.minecraft.util.math.RayTraceResult
import kotlin.math.*


// TODO: Rewrite
object ElytraFlight : Module(
    name = "ElytraFlight",
    description = "Allows infinite and way easier Elytra flying",
    category = Category.MOVEMENT,
    modulePriority = 1000
) {
    val mode = setting("Mode", ElytraFlightMode.CONTROL)
    private val page by setting("Page", Page.GENERIC_SETTINGS)
    private val durabilityWarning by setting("Durability Warning", true, { page == Page.GENERIC_SETTINGS })
    private val threshold by setting("Warning Threshold", 5, 1..50, 1, { durabilityWarning && page == Page.GENERIC_SETTINGS }, description = "Threshold of durability to start sending warnings")
    private var autoLanding by setting("Auto Landing", false, { page == Page.GENERIC_SETTINGS })

    /* Generic Settings */
    /* Takeoff */
    private val easyTakeOff by setting("Easy Takeoff", true, { page == Page.GENERIC_SETTINGS })
    private val timerControl by setting("Takeoff Timer", true, { easyTakeOff && page == Page.GENERIC_SETTINGS })
    private val highPingOptimize by setting("High Ping Optimize", false, { easyTakeOff && page == Page.GENERIC_SETTINGS })
    private val minTakeoffHeight by setting("Min Takeoff Height", 1.2f, 0.0f..1.5f, 0.1f, { easyTakeOff && !highPingOptimize && page == Page.GENERIC_SETTINGS })

    /* Acceleration */
    private val accelerateStartSpeed by setting("Start Speed", 100, 0..100, 5, { mode.value != ElytraFlightMode.BOOST && mode.value != ElytraFlightMode.VANILLA && page == Page.GENERIC_SETTINGS })
    private val accelerateTime by setting("Accelerate Time", 0.0f, 0.0f..20.0f, 0.25f, { mode.value != ElytraFlightMode.BOOST && mode.value != ElytraFlightMode.VANILLA && page == Page.GENERIC_SETTINGS })

    /* Spoof Pitch */
    private val spoofPitch by setting("Spoof Pitch", true, { mode.value != ElytraFlightMode.BOOST && mode.value != ElytraFlightMode.VANILLA && page == Page.GENERIC_SETTINGS })
    private val blockInteract by setting("Block Interact", false, { spoofPitch && mode.value != ElytraFlightMode.BOOST && mode.value != ElytraFlightMode.VANILLA && page == Page.GENERIC_SETTINGS })
    private val forwardPitch by setting("Forward Pitch", 0, -90..90, 5, { spoofPitch && mode.value != ElytraFlightMode.BOOST && mode.value != ElytraFlightMode.VANILLA && page == Page.GENERIC_SETTINGS })

    /* Extra */
    val elytraSounds by setting("Elytra Sounds", true, { page == Page.GENERIC_SETTINGS })
    private val swingSpeed by setting("Swing Speed", 1.0f, 0.0f..2.0f, 0.1f, { page == Page.GENERIC_SETTINGS && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.PACKET) })
    private val swingAmount by setting("Swing Amount", 0.8f, 0.0f..2.0f, 0.1f, { page == Page.GENERIC_SETTINGS && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.PACKET) })
    /* End of Generic Settings */

    /* Mode Settings */
    /* Boost */
    private val speedBoost by setting("Speed B", 1.0f, 0.0f..10.0f, 0.1f, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS })
    private val upSpeedBoost by setting("Up Speed B", 1.0f, 0.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS })
    private val downSpeedBoost by setting("Down Speed B", 1.0f, 0.0f..5.0f, 0.1f, { mode.value == ElytraFlightMode.BOOST && page == Page.MODE_SETTINGS })

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
    private val rocketPitch by setting("Rocket Pitch", 50f, 20f..80f, 2f, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS }, description = "If you are boosted by a rocket, this pitch will be used. Note: on 2B2T, if you are moving too slowly when boosted, you will rubberband", unit = "°")
    private val upPitch by setting("Up Pitch", 36f, 0f..60f, 2f, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS }, description = "If you are moving up or you are pressing space, this pitch will be used", unit = "°")
    private val downPitch by setting("Down Pitch", 35f, 0f..40f, 1f, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS }, description = "Pitch used when you are moving down", unit = "°")
    private val controlSpeed by setting("Control Speed", true, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS }, description = "Enable to set pitch controls based on your speed")
    private val speedThreshold by setting("Speed Threshold", 43, 5..100, 1, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS && controlSpeed }, description = "If you are going faster then the speed threshold, the Up Pitch value will be used", unit = " MPS")
    private val pitchPercentPath by setting("Pitch Percent Path", 60, 1..100, 1, { mode.value == ElytraFlightMode.VANILLA && page == Page.MODE_SETTINGS && controlSpeed }, description = "Rotates the pitch into the Down Pitch value. Low percents rotate faster, high percents rotate slower", unit = "%")

    /* Fireworks */
    private val fireworkUseMode by setting("Firework Use Mode", FireworkUseMode.SPEED, { mode.value == ElytraFlightMode.FIREWORKS && page == Page.MODE_SETTINGS })
    private val delay by setting("Fireworks Delay Ticks", 30, 0..100, 1, { mode.value == ElytraFlightMode.FIREWORKS && fireworkUseMode == FireworkUseMode.DELAY && page == Page.MODE_SETTINGS })
    private val fireworkUseStartSpeed by setting ("Fireworks Use Min Speed", 1.0, 0.01..3.0, 0.01, { mode.value == ElytraFlightMode.FIREWORKS && fireworkUseMode == FireworkUseMode.SPEED && page == Page.MODE_SETTINGS })
    private val fireworkBlockAvoid by setting("Avoid Blocks", false, { mode.value == ElytraFlightMode.FIREWORKS && page == Page.MODE_SETTINGS },
        description = "Don't use fireworks if player is facing into a block")
    private val fireworkBlockAvoidDist by setting("Avoid Blocks Raytrace Distance", 2.0, 1.0..10.0, 0.1, { mode.value == ElytraFlightMode.FIREWORKS && page == Page.MODE_SETTINGS && fireworkBlockAvoid })
    private val fireworksVControl by setting("Boosted V Control", true, { mode.value == ElytraFlightMode.FIREWORKS && page == Page.MODE_SETTINGS })
    private val fireworksVSpeed by setting("Boosted V Control Speed", 1.0, 0.0..3.0, 0.1, { mode.value == ElytraFlightMode.FIREWORKS && fireworksVControl && page == Page.MODE_SETTINGS })
    private const val minFireworkUseDelayTicks = 20

    enum class FireworkUseMode {
        DELAY, SPEED
    }

    /* End of Mode Settings */

    enum class ElytraFlightMode {
        BOOST, CONTROL, CREATIVE, PACKET, VANILLA, FIREWORKS
    }

    private enum class Page {
        GENERIC_SETTINGS, MODE_SETTINGS
    }

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

    /* Control mode states */
    private var hoverTarget = -1.0
    private var packetYaw = 0.0f
    var packetPitch = 0.0f
    private var hoverState = false
    private var boostingTick = 0

    /* Vanilla mode state */
    private var upPitchTimer: Long = 0

    /* Fireworks mode state */
    private var fireworkTickTimer: TickTimer = TickTimer(TimeUnit.TICKS)

    /* Event Listeners */
    init {
        safeListener<PacketEvent.Receive> {
            if (player.isSpectator || !elytraIsEquipped || elytraDurability <= 1 || !isFlying || mode.value == ElytraFlightMode.BOOST) return@safeListener
            if (it.packet is SPacketPlayerPosLook && mode.value != ElytraFlightMode.PACKET && mode.value != ElytraFlightMode.FIREWORKS) {
                val packet = it.packet
                packet.playerPosLookPitch = player.rotationPitch
            }

            /* Cancels the elytra opening animation */
            if (it.packet is SPacketEntityMetadata && isPacketFlying) {
                val packet = it.packet
                if (packet.entityId == player.entityId) it.cancel()
            }
        }

        safeListener<PlayerTravelEvent> {
            if (player.isSpectator) return@safeListener
            stateUpdate(it)
            if (elytraIsEquipped && elytraDurability > 1) {
                if (autoLanding) {
                    landing(it)
                    return@safeListener
                }
                if (!isFlying && !isPacketFlying) {
                    takeoff(it)
                } else {
                    mc.timer.tickLength = 50.0f
                    player.isSprinting = false
                    when (mode.value) {
                        ElytraFlightMode.BOOST -> boostMode()
                        ElytraFlightMode.CONTROL -> controlMode(it)
                        ElytraFlightMode.CREATIVE -> creativeMode()
                        ElytraFlightMode.PACKET -> packetMode(it)
                        ElytraFlightMode.VANILLA -> vanillaMode()
                        ElytraFlightMode.FIREWORKS -> fireworksMode()
                    }
                }
                spoofRotation()
            } else if (!outOfDurability) {
                reset(true)
            }
        }
    }
    /* End of Event Listeners */

    /* Generic Functions */
    private fun SafeClientEvent.stateUpdate(event: PlayerTravelEvent) {
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
            holdPlayer(event)
        } else if (outOfDurability) outOfDurability = false /* Reset if player is on the ground or replace with a new elytra */

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
    private fun SafeClientEvent.holdPlayer(event: PlayerTravelEvent) {
        event.cancel()
        mc.timer.tickLength = 50.0f
        player.setVelocity(0.0, -0.01, 0.0)
    }

    /* Auto landing */
    private fun SafeClientEvent.landing(event: PlayerTravelEvent) {
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
                holdPlayer(event)
            }
            player.capabilities.isFlying || !player.isElytraFlying || isPacketFlying -> {
                reset(true)
                takeoff(event)
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
                    else -> player.motionY = -0.2
                }
            }
        }
        player.setVelocity(0.0, player.motionY, 0.0) /* Kills horizontal motion */
        event.cancel()
    }

    /* The best takeoff method <3 */
    private fun SafeClientEvent.takeoff(event: PlayerTravelEvent) {
        /* Pause Takeoff if server is lagging, player is in water/lava, or player is on ground */
        val timerSpeed = if (highPingOptimize) 400.0f else 200.0f
        val height = if (highPingOptimize) 0.0f else minTakeoffHeight
        val closeToGround = player.posY <= world.getGroundPos(player).y + height && !wasInLiquid && !mc.isSingleplayer

        if (!easyTakeOff || (LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff) || player.onGround) {
            if (LagNotifier.isBaritonePaused && LagNotifier.pauseTakeoff && player.posY - world.getGroundPos(player).y > 4.0f) holdPlayer(event) /* Holds player in the air if server is lagging and the distance is enough for taking fall damage */
            reset(player.onGround)
            return
        }

        if (player.motionY < 0 && !highPingOptimize || player.motionY < -0.02) {
            if (closeToGround) {
                mc.timer.tickLength = 25.0f
                return
            }

            if (!highPingOptimize && !wasInLiquid && !mc.isSingleplayer) { /* Cringe moment when you use elytra flight in single player world */
                event.cancel()
                player.setVelocity(0.0, -0.02, 0.0)
            }

            if (timerControl && !mc.isSingleplayer) mc.timer.tickLength = timerSpeed * 2.0f
            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING))
            hoverTarget = player.posY + 0.2
        } else if (highPingOptimize && !closeToGround) {
            mc.timer.tickLength = timerSpeed
        }
    }

    /**
     *  Calculate yaw for control and packet mode
     *
     *  @return Yaw in radians based on player rotation yaw and movement input
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
            ElytraFlightMode.CREATIVE -> speedCreative
            ElytraFlightMode.PACKET -> speedPacket
            ElytraFlightMode.VANILLA -> 1f
            ElytraFlightMode.FIREWORKS -> 1f
        }
    }

    private fun SafeClientEvent.setSpeed(yaw: Double, boosting: Boolean) {
        val acceleratedSpeed = getSpeed(boosting)
        player.setVelocity(sin(-yaw) * acceleratedSpeed, player.motionY, cos(yaw) * acceleratedSpeed)
    }
    /* End of Generic Functions */

    /* Boost mode */
    private fun SafeClientEvent.boostMode() {
        val yaw = player.rotationYaw.toDouble().toRadian()
        player.motionX -= player.movementInput.moveForward * sin(yaw) * speedBoost / 20
        if (player.movementInput.jump) {
            player.motionY += upSpeedBoost / 15
        } else if (player.movementInput.sneak) {
            player.motionY -= downSpeedBoost / 15
        }
        player.motionZ += player.movementInput.moveForward * cos(yaw) * speedBoost / 20
    }

    /* Control Mode */
    private fun SafeClientEvent.controlMode(event: PlayerTravelEvent) {
        /* States and movement input */
        val currentSpeed = sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)
        val moveUp = if (!legacyLookBoost) {
            player.movementInput.jump
        } else {
            player.rotationPitch < -10.0f && !isStandingStillH
        }

        val moveDown = if (InventoryMove.isEnabled && !InventoryMove.sneak && mc.currentScreen != null || moveUp) {
            false
        } else {
            player.movementInput.sneak
        }

        /* Dynamic down speed */
        val calcDownSpeed = if (dynamicDownSpeed) {
            val minDownSpeed = min(downSpeedControl, fastDownSpeedControl).toDouble()
            val maxDownSpeed = max(downSpeedControl, fastDownSpeedControl).toDouble()
            if (player.rotationPitch > 0) {
                player.rotationPitch / 90.0 * (maxDownSpeed - minDownSpeed) + minDownSpeed
            } else minDownSpeed
        } else downSpeedControl.toDouble()

        /* Hover */
        if (hoverTarget < 0.0 || moveUp) {
            hoverTarget = player.posY
        } else if (moveDown) {
            hoverTarget = player.posY - calcDownSpeed
        }
        hoverState = (if (hoverState) player.posY < hoverTarget else player.posY < hoverTarget - 0.1)
            && altitudeHoldControl

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

        event.cancel()
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
    /* End of Control Mode */

    /* Creative Mode */
    private fun SafeClientEvent.creativeMode() {
        if (player.onGround) {
            reset(true)
            return
        }

        packetPitch = forwardPitch.toFloat()
        player.capabilities.isFlying = true
        player.capabilities.flySpeed = getSpeed(false).toFloat()

        val motionY = when {
            isStandingStill -> 0.0
            player.movementInput.jump -> upSpeedCreative.toDouble()
            player.movementInput.sneak -> -downSpeedCreative.toDouble()
            else -> -fallSpeedCreative.toDouble()
        }
        player.setVelocity(0.0, motionY, 0.0) /* Remove the creative flight acceleration and set the motionY */
    }

    /* Packet Mode */
    private fun SafeClientEvent.packetMode(event: PlayerTravelEvent) {
        isPacketFlying = !player.onGround
        connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_FALL_FLYING))

        /* Set velocity */
        if (!isStandingStillH) { /* Runs when pressing wasd */
            setSpeed(getYaw(), false)
        } else player.setVelocity(0.0, 0.0, 0.0)
        player.motionY = (if (player.movementInput.sneak) -downSpeedPacket else -fallSpeedPacket).toDouble()

        event.cancel()
    }

    private fun SafeClientEvent.vanillaMode() {
        val playerSpeed = (sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)).toFloat()*20 // This is the player's speed
        val speedPercentOfMax = playerSpeed/speedThreshold*100 // This is used to calculate the percent of the max speed. 50 means 50%
        packetPitch = when {
            // If the player is boosted with a firework, use -rocketPitch
            world.loadedEntityList.any { it is EntityFireworkRocket && it.boostedEntity == player } -> -rocketPitch
            // If the player is moving up, the player is pressing space, or upPitchTimer is still going, use -upPitch
            player.motionY > 0 || System.currentTimeMillis() < upPitchTimer || player.movementInput.jump -> -upPitch
            // If controlSpeed is enabled and the speed is over the speedThreshold, then....
            controlSpeed && playerSpeed > speedThreshold -> {
                upPitchTimer = System.currentTimeMillis() + 1000 // Set upPitchTimer for 1 second
                -upPitch} // Use -upPitch
            // Simple expression that slowly curves the pitch into downPitch
            controlSpeed && speedPercentOfMax < pitchPercentPath -> speedPercentOfMax/pitchPercentPath*downPitch
            // If none of the other conditions are met, use downPitch
            else -> downPitch
        }
    }

    private fun SafeClientEvent.fireworksMode() {
        val isBoosted = world.getLoadedEntityList().any { it is EntityFireworkRocket && it.boostedEntity == player }
        val currentSpeed = sqrt(player.motionX * player.motionX + player.motionZ * player.motionZ)

        if (isBoosted) {
            if (fireworksVControl) {
                if (player.movementInput.jump) {
                    player.motionY = fireworksVSpeed
                } else if (player.movementInput.sneak) {
                    player.motionY = -fireworksVSpeed
                }
            }
            fireworkTickTimer.reset()
        } else when (fireworkUseMode) {
            FireworkUseMode.DELAY -> {
                if (fireworkTickTimer.tick(delay, true)) useFirework()
            }
            FireworkUseMode.SPEED -> {
                if (currentSpeed < fireworkUseStartSpeed
                    && fireworkTickTimer.tick(minFireworkUseDelayTicks, true)
                ) useFirework()
            }
        }
    }

    private fun SafeClientEvent.useFirework() {
        if (fireworkBlockAvoid) {
            player.rayTrace(fireworkBlockAvoidDist, 1f)?.let {
                if (it.typeOfHit == RayTraceResult.Type.BLOCK) return
            }
        }
        playerController.syncCurrentPlayItem()

        val holdingFireworksMainhand = isBoostingFirework(player.serverSideItem)
        val holdingFireworksOffhand = isBoostingFirework(player.offhandSlot.stack)

        if (holdingFireworksMainhand) {
            connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.MAIN_HAND))
        } else if (holdingFireworksOffhand) {
            connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.OFF_HAND))
        } else {
            player.hotbarSlots.firstItem<ItemFirework, HotbarSlot> { isBoostingFirework(it) }?.let {
                spoofHotbar(it.hotbarSlot)
                connection.sendPacket(CPacketPlayerTryUseItem(EnumHand.MAIN_HAND))
                resetHotbar()
                return
            }

            swapToItemOrMove<ItemFirework>(this@ElytraFlight, { isBoostingFirework(it) })
            fireworkTickTimer.reset(minFireworkUseDelayTicks * 40L)
        }
    }

    private fun isBoostingFirework(it: ItemStack): Boolean {
        return it.item is ItemFirework
            && it.getSubCompound("Fireworks")?.hasKey("Flight") == true
    }

    fun shouldSwing(): Boolean {
        return isEnabled
            && isFlying
            && !autoLanding
            && (mode.value == ElytraFlightMode.CONTROL || mode.value == ElytraFlightMode.PACKET)
    }

    private fun SafeClientEvent.spoofRotation() {
        if (player.isSpectator || !elytraIsEquipped || elytraDurability <= 1 || !isFlying) return

        var cancelRotation = false
        var rotation = Vec2f(player)

        if (autoLanding) {
            rotation = Vec2f(rotation.x, -20f)
        } else if (mode.value != ElytraFlightMode.BOOST
            && mode.value != ElytraFlightMode.VANILLA
            && mode.value != ElytraFlightMode.FIREWORKS
        ) {
            if (!isStandingStill && mode.value != ElytraFlightMode.CREATIVE) {
                rotation = Vec2f(packetYaw, rotation.y)
            }
            if (spoofPitch) {
                if (!isStandingStill) rotation = Vec2f(rotation.x, packetPitch)

                /* Cancels rotation packets if player is not moving and not clicking */
                cancelRotation = isStandingStill && ((!mc.gameSettings.keyBindUseItem.isKeyDown && !mc.gameSettings.keyBindAttack.isKeyDown && blockInteract) || !blockInteract)
            }
        } else if (mode.value == ElytraFlightMode.VANILLA) {
            rotation = Vec2f(rotation.x, packetPitch)
        }

        sendPlayerPacket {
            if (cancelRotation) {
                cancelRotate()
            } else {
                rotate(rotation)
            }
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