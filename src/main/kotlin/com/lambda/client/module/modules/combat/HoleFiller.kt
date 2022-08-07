package com.lambda.client.module.modules.combat

import com.lambda.client.commons.interfaces.DisplayEnum
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.FriendManager
import com.lambda.client.manager.managers.HotbarManager
import com.lambda.client.manager.managers.HotbarManager.resetHotbar
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.manager.managers.HotbarManager.spoofHotbar
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.combat.SurroundUtils
import com.lambda.client.util.combat.SurroundUtils.checkHole
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.items.*
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.*
import kotlinx.coroutines.launch
import net.minecraft.block.Block
import net.minecraft.client.entity.EntityPlayerSP
import net.minecraft.entity.player.EntityPlayer
import net.minecraft.init.Blocks
import net.minecraft.network.Packet
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.*
import net.minecraftforge.fml.common.gameevent.TickEvent
import kotlin.math.atan2
import kotlin.math.sqrt

@Suppress("UNUSED")
object HoleFiller :
    Module(name = "HoleFiller", description = "Fill holes", category = Category.COMBAT) {
    private val page = setting("Page", Page.GENERAL)
    private val range by setting("Hole Distance", 4, 0..4, 1)
    private val obby by setting("FillWith", Obby.OBSIDIAN)
    private val mode by setting("Mode", Mode.CONSTANT)
    private val playerRange by setting("Player Range", 4.0, 0.0..4.0, 0.25, { mode == Mode.SURPRISE })
    private val fillPlayerRange by setting("Fill Player Range", 2.0, 0.0..4.0, 0.25, { mode == Mode.SURPRISE })
    private val disableOnFinish by setting("DisableOnFinish", true, { mode == Mode.CONSTANT }, description = "Disable when no more holes")
    private val autoSwap by setting("Auto Swap", false)
    private val spoofHotbar by setting("Spoof Hotbar", true)
    private val holeType by setting("Hole Type", HoleType.BOTH)
    private val legitPlace by setting("Legit Place", false)
    private val rotate by setting("Rotate", true)
    private val yLevel by setting("Y Level Check", true, description = "Don't fill holes higher than the enemy Y axis")
    private val hand by setting("Hand", Hand.MAIN)
    private val debug by setting("debug", false)
    private val render by setting("Render", false, { debug })
    private val statusMessage by setting("Status Messages", false, { debug })
    //private val visibleCheck by setting("VisibleCheck", false)

    private enum class Page {
        GENERAL, PLACE
    }
    @Suppress("UNUSED")
    private enum class Hand(val enumHand: EnumHand) {
        MAIN(EnumHand.MAIN_HAND),
        OFF_HAND(EnumHand.OFF_HAND)
    }
    private enum class Mode {
        CONSTANT,
        SURPRISE
    }
    @Suppress("UNUSED")
    private enum class Obby(val block: Block) {
        OBSIDIAN(Blocks.OBSIDIAN),
        COBWEB(Blocks.WEB)
    }

    private enum class HoleType {
        OBSIDIAN,
        BEDROCK,
        BOTH
    }

    private val renderer = ESPRenderer()
    private val timer = TickTimer()
    private val cached = ArrayList<Triple<AxisAlignedBB, ColorHolder, Int>>()
    private var placePacket: CPacketPlayerTryUseItemOnBlock? = null
    private var closestPlayer: EntityPlayer? = null
    init {
        safeListener<TickEvent.ClientTickEvent> {
            defaultScope.launch {
                for (x in -range..range) for (y in -range..range) for (z in -range..range) {
                    closestPlayer = getClosestPlayer() as EntityPlayer?
                    if(!canPlace()) continue
                    if (x == 0 && y == 0 && z == 0) continue
                    val playerPos = player.positionVector.toBlockPos()
                    pos = playerPos.add(x, y, z)
                    val holeType = checkHole(pos)
                    if (holeType.shouldFill()) {
                        closestPlayer ?: continue
                        if(render) cached.add(Triple(AxisAlignedBB(pos), ColorHolder(255, 0, 0, 125), GeometryMasks.Quad.DOWN))
                        if (!isHoldingObby) swapToObby()

                        place(closestPlayer ?: continue)
                    }
                }
                if (debug && statusMessage) MessageSendHelper.sendChatMessage("Filled all holes")
                if (disableOnFinish && mode == Mode.CONSTANT) disable()
            }
        }
        safeListener<RenderWorldEvent>(69420) {
            if (!render) return@safeListener
            if (timer.tick(133L)) { // Avoid running this on a tick
                updateRenderer()
            }
            renderer.render(false)
        }
    }
    private val isHoldingObby
        get() = mc.player.serverSideItem.item.block.isObby()

    private fun SafeClientEvent.canPlace() =
        player.allSlots.countBlock(obby.block) > 0


    private fun SafeClientEvent.swapToObby() {
        if (!player.heldItemOffhand.item.block.isObby()) {
            if (spoofHotbar) {
                val slot = if (player.serverSideItem.item.block.isObby()) HotbarManager.serverSideHotbar
                else player.getObbySlot()?.hotbarSlot

                if (slot != null) {
                    spoofHotbar(slot, 1000L)
                }
            } else {
                if (player.serverSideItem.item != obby.block) {
                    player.getObbySlot()?.let {
                        swapToSlot(it)
                    }
                }
            }
        }
    }

    private var rotationPacket: Any? = null
    private lateinit var pos: BlockPos


    private fun SafeClientEvent.place(closestPlayer: EntityPlayer){

        if(player.distanceTo(closestPlayer.position.toVec3d()) > fillPlayerRange) return
        if(yLevel) if(closestPlayer.position.y < pos.y) return

        val playerIsNotInsideTheHole = closestPlayer.position != pos
        placePacket = placeInfo?.let {
            CPacketPlayerTryUseItemOnBlock(
                it.pos,
                it.side,
                hand.enumHand,
                it.hitVecOffset.x.toFloat(),
                it.hitVecOffset.y.toFloat(),
                it.hitVecOffset.z.toFloat()
            )
        }
        when(mode){
            Mode.SURPRISE -> {
                if (player.distanceTo(closestPlayer.position) <= playerRange &&
                    player.distanceTo(pos) <= fillPlayerRange &&
                    playerIsNotInsideTheHole
                ) {
                    player.swingArm(hand.enumHand)
                    if (rotate) (rotationPacket() as? Packet<*>)?.let { it -> connection.sendPacket(it) }
                    placePacket?.let { connection.sendPacket(it) }
                    resetHotbar()
                    placePacket = null
                    if(render) cached.clear()
                }
            }
            Mode.CONSTANT -> {
                player.swingArm(hand.enumHand)
                if (rotate) (rotationPacket() as? Packet<*>)?.let { it -> connection.sendPacket(it) }
                placePacket?.let { connection.sendPacket(it) }
                resetHotbar()
                placePacket = null
                if(render) cached.clear()
            }
        }

    }

    private fun SafeClientEvent.getClosestPlayer(): Any? {
        return player.world.getEntitiesWithinAABB(EntityPlayer::class.java, rangeBB)
            .toList().asSequence()
            .filter { it != null && !it.isDead && it.health > 0.0f }
            .filter { it != player && it != mc.renderViewEntity }
            .filter { !FriendManager.isFriend(it.name) }.firstOrNull()

    }

    private fun SafeClientEvent.rotationPacket(): Any? {
        when(val rotations: Any = if(legitPlace) getLegitRotations(vec) else getRotationTo(vec)){
            is FloatArray -> {
                rotationPacket =
                    CPacketPlayer.Rotation(
                        rotations[0],
                        rotations[1],
                        player.onGround
                    )
            }
            is Vec2f -> {
                rotationPacket =
                    CPacketPlayer.PositionRotation(
                        player.posX,
                        player.posY,
                        player.posZ,
                        rotations.x,
                        rotations.y,
                        player.onGround
                    )
            }
        }
        return rotationPacket
    }



    private fun EntityPlayerSP.getObbySlot() =
        this.hotbarSlots.firstBlock(obby.block)

    private fun Block.isObby() =
        this == Blocks.OBSIDIAN || this == Blocks.WEB

    private fun SurroundUtils.HoleType.shouldFill() =
        this == SurroundUtils.HoleType.OBSIDIAN || this == SurroundUtils.HoleType.BEDROCK




    private val rangeBB
        get() = AxisAlignedBB(
            mc.player.posX - playerRange,
            mc.player.posY - playerRange,
            mc.player.posZ - playerRange,
            mc.player.posX + playerRange,
            mc.player.posY + playerRange,
            mc.player.posZ + playerRange
        )


    private val SafeClientEvent.vec
        get() = getHitVec(pos, getClosestVisibleSide(pos) ?: EnumFacing.UP)

    private val SafeClientEvent.placeInfo: PlaceInfo?
        get() = getNeighbour(pos, 1, fillPlayerRange.toFloat())



    private fun getLegitRotations(vec: Vec3d): FloatArray {
        val eyesPos: Vec3d = mc.player.getPositionEyes(1.0f)
        val diffX = vec.x - eyesPos.x
        val diffY = vec.y - eyesPos.y
        val diffZ = vec.z - eyesPos.z
        val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)
        val yaw = Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90.0f
        val pitch = (-Math.toDegrees(atan2(diffY, diffXZ))).toFloat()
        return floatArrayOf(mc.player.rotationYaw + MathHelper.wrapDegrees(yaw - mc.player.rotationYaw), mc.player.rotationPitch + MathHelper.wrapDegrees(pitch - mc.player.rotationPitch))
    }



    private fun updateRenderer() {
        renderer.aFilled = 30
        renderer.aOutline = 128
        renderer.replaceAll(cached)
    }
}
