package com.lambda.client.module.modules.movement

import com.lambda.client.event.Phase
import com.lambda.client.event.events.OnUpdateWalkingPlayerEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.event.listener.listener
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.mixin.extension.playerPosLookPitch
import com.lambda.client.mixin.extension.playerPosLookYaw
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.MovementUtils
import com.lambda.client.util.MovementUtils.calcMoveYaw
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener
import net.minecraft.network.play.client.CPacketConfirmTeleport
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketCloseWindow
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.Vec3d
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.sin


object Flight : Module(
    name = "Flight",
    description = "Makes the player fly",
    category = Category.MOVEMENT,
    modulePriority = 500
) {
    // non packet
    private val mode by setting("Mode", FlightMode.PACKET).also {
        it.listeners.add { if (it.value == FlightMode.PACKET) sendRubberbandPacket() }
    }
    private val speed by setting("Speed", 1.0f, 0f..10f, 0.1f, { mode != FlightMode.PACKET })
    private val glideSpeed by setting("Glide Speed", 0.05, 0.0..0.3, 0.001, { mode != FlightMode.PACKET })

    // packet
    private val packetMode by setting("Packet Mode", PacketMode.FAST, { mode == FlightMode.PACKET })
    private val bounds by setting("Packet Type", PacketType.NEGATIVE, { mode == FlightMode.PACKET })
    private val factor by setting("Bypass Factor", 1f, 0f..10f, .1f, { mode == FlightMode.PACKET })
    private val concealFactor by setting("Conceal Factor", 2f, 0f..10f, .1f, { mode == FlightMode.PACKET })
    private val conceal by setting("Conceal", false, { mode == FlightMode.PACKET })
    private val antiKick by setting("Anti Kick", true, { mode == FlightMode.PACKET })

    private enum class FlightMode {
        PACKET, VANILLA, STATIC
    }

    private enum class PacketType {
        POSITIVE, NEGATIVE, STRICT
    }

    private enum class PacketMode {
        FAST, SETBACK
    }

    private const val BASE_SPEED = .2873
    private const val CONCEAL_SPEED = .0624
    private const val SQRT_TWO_OVER_TWO = .707106781
    private const val ANTIKICK_AMOUNT = .03125

    private val history = HashMap<Int, Vec3d>()
    private val filter = ArrayList<CPacketPlayer.Position>()
    private var tpID = -1
    private var ticksEnabled = 0

    init {
        onDisable {
            runSafe {
                tpID = -1
                ticksEnabled = 0
                player.noClip = false

                player.capabilities?.apply {
                    isFlying = false
                    flySpeed = 0.05f
                }
            }
        }

        onEnable {
            if (mode != FlightMode.PACKET) return@onEnable
            sendRubberbandPacket()
        }

        safeListener<PlayerMoveEvent> {
            when (mode) {
                // uses the same concepts as https://gist.github.com/Doogie13/aa04c6a8eb496c1afdb9c675e2ebd91c
                // completely written from scratch, however
                FlightMode.PACKET -> {
                    player.noClip = true

                    // region Motion
                    val concealing = world.collidesWithAnyBlock(player.entityBoundingBox) || conceal
                    var motionY: Double
                    var up = 0

                    // we must use else if to allow phasing
                    if (mc.gameSettings.keyBindJump.isKeyDown)
                        up++
                    else if (mc.gameSettings.keyBindSneak.isKeyDown)
                        up--

                    motionY = if (up == 0)
                        .0
                    else
                        CONCEAL_SPEED * up.toDouble()

                    var motionXZ: Double = if (!MovementUtils.isInputting)
                        .0
                    else if (concealing)
                        CONCEAL_SPEED
                    else
                        BASE_SPEED

                    if (motionY != .0 && motionXZ == BASE_SPEED)
                        motionY = .0

                    if (motionXZ == CONCEAL_SPEED && motionY == CONCEAL_SPEED) {
                        motionXZ *= SQRT_TWO_OVER_TWO
                        motionY *= SQRT_TWO_OVER_TWO
                    }

                    //endregion

                    //region packets

                    val calcFactor =
                        if (hypot(motionXZ, motionY) < .0625)
                            concealFactor
                        else
                            factor

                    var factorInt = floor(calcFactor).toInt()
                    val diff = calcFactor - factorInt

                    if (++ticksEnabled % 10 < diff * 10)
                        factorInt++

                    if (factorInt == 0) {
                        player.setVelocity(.0, .0, .0)
                        return@safeListener
                    }

                    if (motionXZ == .0 && motionY == .0)
                        factorInt = 1

                    val yaw = calcMoveYaw()

                    val baseX = -sin(yaw) * motionXZ
                    val baseY = motionY
                    val baseZ = cos(yaw) * motionXZ

                    var currentX = baseX
                    var currentY = if (antiKick && ticksEnabled % 10 == 0) -ANTIKICK_AMOUNT else baseY
                    var currentZ = baseZ

                    for (i in 1..factorInt) {
                        // should never happen
                        if (i > 10)
                            break

                        val moveVec = Vec3d(currentX, currentY, currentZ)

                        var yOffset: Double

                        //region bounds
                        when (bounds) {
                            PacketType.STRICT -> {
                                var random = (Math.random() * 256) + 256

                                (random + player.posY > (if (player.dimension == -1) 127 else 255))
                                random *= -1

                                yOffset = random
                            }
                            PacketType.POSITIVE -> {
                                yOffset = 1337.0
                            }
                            PacketType.NEGATIVE -> {
                                yOffset = -1337.0
                            }
                        }
                        //endregion

                        //region sending

                        val boundsPacket = CPacketPlayer.Position(player.posX + moveVec.x, player.posY + moveVec.y + yOffset, player.posZ + moveVec.z, true)
                        val movePacket = CPacketPlayer.Position(player.posX + moveVec.x, player.posY + moveVec.y, player.posZ + moveVec.z, true)

                        filter.add(movePacket)
                        filter.add(boundsPacket)

                        connection.sendPacket(movePacket)
                        connection.sendPacket(boundsPacket)

                        history[++tpID] = moveVec.add(player.positionVector)
                        connection.sendPacket(CPacketConfirmTeleport(tpID))

                        currentX += baseX
                        currentY += baseY
                        currentZ += baseZ

                        //endregion

                    }

                    //endregion

                    if (packetMode == PacketMode.FAST)
                        player.setVelocity(currentX - baseX, currentY - baseY, currentZ - baseZ)
                    else
                        player.setVelocity(.0, .0, .0)

                }
                FlightMode.STATIC -> {
                    var up = 0

                    if (mc.gameSettings.keyBindJump.isKeyDown) up++

                    if (mc.gameSettings.keyBindSneak.isKeyDown) up--

                    player.motionY = if (up == 0) -glideSpeed else speed * up.toDouble()

                    if (!MovementUtils.isInputting)
                        return@safeListener

                    val yaw = calcMoveYaw()
                    player.motionX = -sin(yaw) * speed
                    player.motionZ = cos(yaw) * speed

                }
                FlightMode.VANILLA -> {
                    player.capabilities.isFlying = true
                    player.capabilities.flySpeed = speed / 11.11f

                    if (glideSpeed != 0.0
                        && !mc.gameSettings.keyBindJump.isKeyDown
                        && !mc.gameSettings.keyBindSneak.isKeyDown) player.motionY = -glideSpeed
                }
            }
        }

        listener<OnUpdateWalkingPlayerEvent> {
            if (it.phase != Phase.PRE || mode != FlightMode.PACKET) return@listener
            sendPlayerPacket {
                cancelAll()
            }
        }

        safeListener<PacketEvent.Receive> {
            if (mode != FlightMode.PACKET) return@safeListener

            when (val packet = it.packet) {
                is SPacketPlayerPosLook -> {
                    val id = packet.teleportId

                    if (history.containsKey(packet.teleportId) && tpID != -1) {
                        history[id]?.let { vec ->
                            if (vec.x == packet.x && vec.y == packet.y && vec.z == packet.z) {
                                if (packetMode != PacketMode.SETBACK)
                                    it.cancel()

                                history.remove(id)
                                player.connection.sendPacket(CPacketConfirmTeleport(id))
                                return@safeListener
                            }
                        }
                    }

                    packet.playerPosLookYaw = player.rotationYaw
                    packet.playerPosLookPitch = player.rotationPitch

                    player.connection.sendPacket(CPacketConfirmTeleport(id))

                    tpID = id
                }
                is SPacketCloseWindow -> {
                    it.cancel()
                }
            }
        }

        safeListener<PacketEvent.Send> {
            if (mode != FlightMode.PACKET || it.packet !is CPacketPlayer) return@safeListener

            if (!filter.contains(it.packet))
                it.cancel()
            else
                filter.remove(it.packet)

        }
    }
    
    private fun sendRubberbandPacket(){
        runSafeR {
            val position = CPacketPlayer.Position(.0, .0, .0, true)
            filter.add(position)
            connection.sendPacket(position)
        } ?: disable()
    }
}
