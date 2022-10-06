package com.lambda.client.module.modules.movement

import com.lambda.client.event.events.AddCollisionBoxToListEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.PlayerMoveEvent
import com.lambda.client.manager.managers.TimerManager.modifyTimer
import com.lambda.client.mixin.extension.playerY
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.threads.safeListener
import com.lambda.mixin.accessor.player.AccessorEntityPlayerSP
import net.minecraft.block.Block
import net.minecraft.init.Blocks
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BlockPos.PooledMutableBlockPos
import net.minecraftforge.client.event.InputUpdateEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import net.minecraftforge.fml.common.gameevent.TickEvent.ClientTickEvent
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.hypot


object Jesus : Module(
    name = "Jesus",
    description = "Allows you to walk on water",
    category = Category.MOVEMENT
) {

    private val mode by setting("Mode", Mode.STRICT)

    enum class Mode {
        SOLID,
        STRICT,
        DOLPHIN
    }

    private val preventJump by setting("Prevent Jumping", false, { mode == Mode.SOLID || mode == Mode.STRICT }, description = "Prevent jumping when using jesus")

    private val bb = AxisAlignedBB(-1.0, -1.0, -1.0, 0.0, 0.0, 0.0)

    private var ticksEnabled = 0
    private var fakeY = 0.0

    init {

        onDisable {

            ticksEnabled = 0

            var i = 0
            while (true) {
                i++
                fakeY += .1
                if (fakeY > 0) break
                mc.player.connection.sendPacket(CPacketPlayer.Position(mc.player.posX, mc.player.posY + fakeY, mc.player.posZ, false))
            }

            modifyTimer(50 * i.toFloat())

            fakeY = .0

        }

        safeListener<ClientTickEvent> {

            if (it.phase == TickEvent.Phase.START)
                ticksEnabled++

        }

        safeListener<AddCollisionBoxToListEvent> {

            if (mc.gameSettings.keyBindSneak.isKeyDown)
                return@safeListener

            if ((mode == Mode.SOLID || mode == Mode.STRICT)
                && mc.world.getBlockState(BlockPos(mc.player.positionVector.add(.0, -.1 + mc.player.motionY, .0))).material.isLiquid)
                it.collisionBoxList.add(bb.offset(mc.player.posX, floor(mc.player.posY), mc.player.posZ))

        }

        safeListener<PlayerMoveEvent> { event ->

            (player as AccessorEntityPlayerSP).lcSetLastReportedY(-99.9)

            if (mc.gameSettings.keyBindSneak.isKeyDown)
                return@safeListener

            (mc.player as AccessorEntityPlayerSP).lcSetLastReportedY(-999.0)

            if (mc.player.isInWater || mc.world.getBlockState(BlockPos(mc.player.positionVector)).material.isLiquid) event.y = (.11.also { mc.player.motionY = it })

            if (mc.player.onGround && !checkBlockCollisionNoLiquid(mc.player.entityBoundingBox.offset(.0, -.01, .0),
                    listOf(Blocks.AIR, Blocks.WATER, Blocks.FLOWING_WATER, Blocks.LAVA, Blocks.FLOWING_LAVA))) {

                if (mode == Mode.DOLPHIN) {

                    if (hypot(event.x, event.y) > .2873 * .9) {
                        event.x *= .95
                        event.z *= .95
                    }

                } else if (mode == Mode.STRICT) {
                    val lava = !checkBlockCollisionNoLiquid(mc.player.entityBoundingBox.offset(.0, -.01, .0), listOf(Blocks.AIR, Blocks.LAVA, Blocks.FLOWING_LAVA))
                    // .38 is from lava liquid speed at max speed, 1.24 is from water liquid speed at max speed
                    // because of the way "Lambda Client" handled its "PlayerMoveEvent" I have to use "magic numbers" to compensate
                    event.x *= if (lava) .57 else 1.09
                    event.z *= if (lava) .57 else 1.09
                }

            }
        }

        safeListener<InputUpdateEvent> {

            if (preventJump &&
                !checkBlockCollisionNoLiquid(mc.player.entityBoundingBox.offset(.0, -.01, .0),
                    listOf<Block>(Blocks.WATER, Blocks.FLOWING_WATER, Blocks.LAVA, Blocks.FLOWING_LAVA))
            )
                it.movementInput.jump = false

        }

        safeListener<PacketEvent.Send> {

            if (it.packet is CPacketPlayer) {
                if (mc.gameSettings.keyBindSneak.isKeyDown) {

                    if (mode == Mode.STRICT)
                        mc.player.posY -= fakeY

                    fakeY = 0.0

                    return@safeListener

                }

                val playerBB = mc.player.entityBoundingBox
                if (mc.player.isInWater ||
                    !mc.world.getBlockState(BlockPos(mc.player.positionVector.add(.0, -.1 + mc.player.motionY, .0))).material.isLiquid ||
                    mc.world.getCollisionBoxes(mc.player, playerBB.offset(0.0, -.0001, 0.0)).isEmpty()) {
                    fakeY = 0.0
                    return@safeListener
                }

                val packet = it.packet

                if (mode == Mode.STRICT) {

                    if ((-.4).coerceAtLeast(fakeY).also { fakeY = it } > -.4) {

                        fakeY -= .08
                        fakeY *= .98
                        packet.playerY += fakeY

                    } else
                        packet.playerY += fakeY - if (ticksEnabled % 2 == 0) .0 else -.00001

                    if (checkBlockCollisionNoLiquid(mc.player.entityBoundingBox.offset(.0, packet.playerY - mc.player.posY, .0), listOf(Blocks.AIR, Blocks.WATER, Blocks.FLOWING_WATER, Blocks.LAVA, Blocks.FLOWING_LAVA)))
                        packet.playerY = mc.player.posY

                } else if (mode == Mode.SOLID) {

                    fakeY = 0.0

                    if (ticksEnabled % 2 == 0)
                        packet.playerY -= .001

                    if (checkBlockCollisionNoLiquid(mc.player.entityBoundingBox.offset(.0, packet.playerY - mc.player.posY, .0), listOf(Blocks.AIR, Blocks.WATER, Blocks.FLOWING_WATER, Blocks.LAVA, Blocks.FLOWING_LAVA)))
                        packet.playerY = mc.player.posY
                }

            }

        }

        safeListener<PacketEvent.Receive> {

            if (it.packet is SPacketPlayerPosLook)
                fakeY = mc.player.posY - it.packet.y

        }
    }

    // modified mc code
    private fun checkBlockCollisionNoLiquid(bb: AxisAlignedBB, allowed: List<Block>): Boolean {

        val minX = floor(bb.minX).toInt()
        val maxX = ceil(bb.maxX).toInt()
        val minY = floor(bb.minY).toInt()
        val maxY = ceil(bb.maxY).toInt()
        val minZ = floor(bb.minZ).toInt()
        val maxZ = ceil(bb.maxZ).toInt()

        val mutableBlockPos = PooledMutableBlockPos.retain()

        for (x in minX until maxX) {
            for (y in minY until maxY) {
                for (z in minZ until maxZ) {

                    val blockState = mc.world.getBlockState(mutableBlockPos.setPos(x, y, z))

                    if (!allowed.contains(blockState.block)) {
                        mutableBlockPos.release()
                        return true
                    }

                }
            }
        }

        mutableBlockPos.release()
        return false

    }

}