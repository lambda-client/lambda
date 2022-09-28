package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.prevPosVector
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.combat.CrystalUtils.canPlace
import com.lambda.client.util.combat.CrystalUtils.canPlaceOn
import com.lambda.client.util.combat.SurroundUtils
import com.lambda.client.util.combat.SurroundUtils.checkHole
import com.lambda.client.util.items.swapToItem
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toBlockPos
import com.lambda.client.util.math.VectorUtils.toVec3dCenter
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.runSafeR
import com.lambda.client.util.threads.safeListener
import net.minecraft.entity.Entity
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraftforge.fml.common.gameevent.TickEvent

@CombatManager.CombatModule
object HoleMiner : Module(
    name = "HoleMiner",
    description = "Mines your opponent's hole",
    category = Category.COMBAT,
    modulePriority = 100
) {
    private val delay by setting("Delay", 2, 1..10, 1, unit = " ticks")
    private val range by setting("Range", 5.0f, 1.0f..8.0f, 0.25f)

    private val timer = TickTimer(TimeUnit.TICKS)
    private var miningPos: BlockPos? = null

    override fun getHudInfo() = CombatManager.target?.name ?: ""

    init {
        onEnable {
            runSafeR {
                val target = CombatManager.target
                if (target != null) {
                    if (checkHole(target) != SurroundUtils.HoleType.OBSIDIAN) {
                        MessageSendHelper.sendChatMessage("$chatName Target is not in a valid hole, disabling")
                        disable()
                    } else {
                        miningPos = findHoleBlock(target)
                    }
                } else {
                    MessageSendHelper.sendChatMessage("$chatName No target found, disabling")
                    disable()
                }
            } ?: disable()
        }

        onDisable {
            miningPos = null
        }

        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START || !CombatManager.isOnTopPriority(HoleMiner)) return@safeListener

            if (player.heldItemMainhand.item != Items.DIAMOND_PICKAXE) {
                if (!swapToItem(Items.DIAMOND_PICKAXE)) {
                    MessageSendHelper.sendChatMessage("$chatName No pickaxe found, disabling")
                    disable()
                    return@safeListener
                }
            }

            val pos = miningPos

            if (pos == null) {
                MessageSendHelper.sendChatMessage("$chatName No hole block to mine, disabling")
                disable()
            } else {
                CombatManager.target?.let {
                    if (it.prevPosVector.distanceTo(pos) > 2.0) {
                        MessageSendHelper.sendChatMessage("$chatName Target out of hole, disabling")
                        disable()
                        return@safeListener
                    }
                }

                if (world.isAirBlock(pos)) {
                    MessageSendHelper.sendChatMessage("$chatName Done mining")
                    disable()
                    return@safeListener
                }

                val center = pos.toVec3dCenter()
                val rotation = getRotationTo(center)
                sendPlayerPacket {
                    rotate(rotation)
                }

                val diff = player.getPositionEyes(1.0f).subtract(center)
                val normalizedVec = diff.normalize()
                val facing = EnumFacing.getFacingFromVector(normalizedVec.x.toFloat(), normalizedVec.y.toFloat(), normalizedVec.z.toFloat())

                if (timer.tick(delay.toLong())) {
                    connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, facing))
                    connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, facing))

                    player.swingArm(EnumHand.MAIN_HAND)
                }
            }
        }
    }

    private fun SafeClientEvent.findHoleBlock(entity: Entity): BlockPos? {
        val pos = entity.positionVector.toBlockPos()
        var closestPos = 114.514 to BlockPos.ORIGIN

        for (facing in EnumFacing.HORIZONTALS) {
            val offsetPos = pos.offset(facing)
            val dist = player.distanceTo(offsetPos)

            if (dist > range || dist > closestPos.first) continue
            if (world.getBlockState(offsetPos).block == Blocks.BEDROCK) continue
            if (!checkPos(offsetPos, facing)) continue

            closestPos = dist to offsetPos
        }

        return if (closestPos.second != BlockPos.ORIGIN) closestPos.second else null
    }

    private fun SafeClientEvent.checkPos(pos: BlockPos, facingIn: EnumFacing): Boolean {
        if (canPlaceOn(pos.down()) && world.isAirBlock(pos.up())) return true

        for (facing in EnumFacing.HORIZONTALS) {
            if (facing == facingIn.opposite) continue
            if (!canPlace(pos.offset(facing))) continue
            return true
        }

        return false
    }
}