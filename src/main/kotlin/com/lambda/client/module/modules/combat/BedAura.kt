package com.lambda.client.module.modules.combat

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.manager.managers.CombatManager
import com.lambda.client.manager.managers.PlayerPacketManager.sendPlayerPacket
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.player.NoGhostItems
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.combat.CrystalUtils.calcCrystalDamage
import com.lambda.client.util.items.*
import com.lambda.client.util.math.RotationUtils
import com.lambda.client.util.math.RotationUtils.getRotationTo
import com.lambda.client.util.math.Vec2f
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.math.VectorUtils.toVec3d
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.getClosestVisibleSide
import com.lambda.client.util.world.getHitVecOffset
import com.lambda.client.util.world.isReplaceable
import net.minecraft.init.Blocks
import net.minecraft.init.Items
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemBed
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.tileentity.TileEntityBed
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*

@CombatManager.CombatModule
object BedAura : Module(
    name = "BedAura",
    description = "Places beds to kill enemies",
    category = Category.COMBAT,
    modulePriority = 70
) {
    private val ignoreSecondBaseBlock by setting("Ignore Second Base Block", false)
    private val suicideMode by setting("Suicide Mode", false)
    private val hitDelay by setting("Hit Delay", 5, 1..10, 1, unit = " ticks")
    private val refillDelay by setting("Refill Delay", 2, 1..5, 1, unit = " ticks")
    private val minDamage by setting("Min Damage", 10f, 1f..20f, 0.25f)
    private val maxSelfDamage by setting("Max Self Damage", 4f, 1f..10f, 0.25f, { !suicideMode })
    private val range by setting("Range", 5f, 1f..5f, 0.25f)

    private val placeMap = TreeMap<Pair<Float, Float>, BlockPos>(compareByDescending { it.first }) // <<TargetDamage, SelfDamage>, BlockPos>
    private val bedMap = TreeMap<Float, BlockPos>(compareBy { it }) // <SquaredDistance, BlockPos>
    private val refillTimer = TickTimer(TimeUnit.TICKS)
    private var state = State.NONE
    private var clickPos = BlockPos(0, -6969, 0)
    private var lastRotation = Vec2f.ZERO
    private var hitTickCount = 0
    private var inactiveTicks = 0

    private enum class State {
        NONE, PLACE, EXPLODE
    }

    override fun getHudInfo(): String {
        return (mc.player?.inventorySlots?.countItem<ItemBed>() ?: 0).toString()
    }

    override fun isActive(): Boolean {
        return isEnabled && inactiveTicks <= 5
    }

    init {
        onDisable {
            state = State.NONE
            inactiveTicks = 6
        }

        safeListener<PacketEvent.PostSend> {
            if (!CombatManager.isOnTopPriority(this@BedAura) || it.packet !is CPacketPlayer || state == State.NONE || CombatSetting.pause) return@safeListener

            val hand = getBedHand() ?: EnumHand.MAIN_HAND
            val facing = if (state == State.PLACE) EnumFacing.UP else getClosestVisibleSide(clickPos) ?: EnumFacing.UP
            val hitVecOffset = getHitVecOffset(facing)
            val packet = CPacketPlayerTryUseItemOnBlock(clickPos, facing, hand, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat())

            connection.sendPacket(packet)
            player.swingArm(hand)
            state = State.NONE
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (player.dimension == 0 || !CombatManager.isOnTopPriority(BedAura) || CombatSetting.pause) {
                state = State.NONE
                resetRotation()
                inactiveTicks = 6
                return@safeListener
            }

            inactiveTicks++
            if (canRefill() && (refillTimer.tick(refillDelay) || (NoGhostItems.syncMode != NoGhostItems.SyncMode.PLAYER && NoGhostItems.isEnabled))) {
                player.storageSlots.firstItem<ItemBed, Slot>()?.let {
                    quickMoveSlot(this@BedAura, it)
                }
            }

            updatePlaceMap()
            updateBedMap()

            if (hitTickCount >= hitDelay) {
                bedMap.values.firstOrNull()?.let { preExplode(it) } ?: getPlacePos()?.let { prePlace(it) }
            } else {
                hitTickCount++
                getPlacePos()?.let { prePlace(it) }
            }
            if (inactiveTicks <= 5) sendRotation()
            else resetRotation()
        }
    }

    private fun SafeClientEvent.canRefill(): Boolean {
        return player.hotbarSlots.firstEmpty() != null
            && player.storageSlots.firstItem<ItemBed, Slot>() != null
    }

    private fun SafeClientEvent.updatePlaceMap() {
        val cacheMap = CombatManager.target?.let {
            val posList = VectorUtils.getBlockPosInSphere(player.getPositionEyes(1f), range)
            val damagePosMap = HashMap<Pair<Float, Float>, BlockPos>()

            for (pos in posList) {
                val topSideVec = pos.toVec3d(0.5, 1.0, 0.5)
                val rotation = getRotationTo(topSideVec)
                val facing = EnumFacing.fromAngle(rotation.x.toDouble())
                if (!canPlaceBed(pos)) continue

                val targetDamage = calcCrystalDamage(pos.offset(facing), it)
                if (targetDamage < minDamage) continue

                val selfDamage = calcCrystalDamage(pos.offset(facing), player)
                if (suicideMode || targetDamage > selfDamage && selfDamage <= maxSelfDamage) {
                    damagePosMap[Pair(targetDamage, selfDamage)] = pos
                }
            }

            damagePosMap
        }
        placeMap.clear()
        if (cacheMap != null) placeMap.putAll(cacheMap)
    }

    private fun SafeClientEvent.canPlaceBed(pos: BlockPos): Boolean {
        if (!world.getBlockState(pos).isSideSolid(world, pos, EnumFacing.UP)) return false
        val bedPos1 = pos.up()
        val bedPos2 = getSecondBedPos(bedPos1)
        return (!ignoreSecondBaseBlock || world.getBlockState(bedPos2.down()).isSideSolid(world, bedPos2.down(), EnumFacing.UP))
            && !isFire(bedPos1)
            && !isFire(bedPos2)
            && world.getBlockState(bedPos1).isReplaceable
            && (!ignoreSecondBaseBlock || world.getBlockState(bedPos2).isReplaceable)
    }

    private fun SafeClientEvent.isFire(pos: BlockPos): Boolean {
        return world.getBlockState(pos).block == Blocks.FIRE
    }

    private fun SafeClientEvent.updateBedMap() {
        val cacheMap = CombatManager.target?.let {
            val damagePosMap = HashMap<Float, BlockPos>()
            for (tileEntity in world.loadedTileEntityList) {
                if (tileEntity !is TileEntityBed) continue
                if (!tileEntity.isHeadPiece) continue

                val dist = player.distanceTo(tileEntity.pos).toFloat()
                if (dist > range) continue

                damagePosMap[dist] = tileEntity.pos
            }
            damagePosMap
        }
        bedMap.clear()
        if (cacheMap != null) bedMap.putAll(cacheMap)
    }

    private fun getPlacePos() = placeMap.values.firstOrNull()

    private fun SafeClientEvent.prePlace(pos: BlockPos) {
        if (getExplodePos() != null || player.allSlots.countItem<ItemBed>() == 0) return
        if (getBedHand() == null) swapToItem<ItemBed>()

        preClick(pos, Vec3d(0.5, 1.0, 0.5))
        state = State.PLACE
    }

    private fun getExplodePos() = bedMap.values.firstOrNull()

    private fun SafeClientEvent.preExplode(pos: BlockPos) {
        hitTickCount = 0
        preClick(pos, Vec3d(0.5, 0.0, 0.5))
        state = State.EXPLODE
    }

    private fun SafeClientEvent.preClick(pos: BlockPos, hitOffset: Vec3d) {
        inactiveTicks = 0
        clickPos = pos
        lastRotation = getRotationTo(Vec3d(pos).add(hitOffset))
    }

    private fun SafeClientEvent.getSecondBedPos(pos: BlockPos): BlockPos {
        val vec3d = Vec3d(pos).add(0.5, 0.0, 0.5)
        val rotation = getRotationTo(vec3d)
        val facing = EnumFacing.fromAngle(rotation.x.toDouble())
        return pos.offset(facing)
    }

    private fun SafeClientEvent.getBedHand(): EnumHand? {
        return when (Items.BED) {
            player.heldItemMainhand.item -> EnumHand.MAIN_HAND
            player.heldItemOffhand.item -> EnumHand.OFF_HAND
            else -> null
        }
    }

    private fun sendRotation() {
        sendPlayerPacket {
            rotate(lastRotation)
        }
    }

    private fun SafeClientEvent.resetRotation() {
        lastRotation = Vec2f(RotationUtils.normalizeAngle(player.rotationYaw), player.rotationPitch)
    }
}
