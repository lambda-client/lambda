package com.lambda.client.module.modules.combat

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.items.blockBlacklist
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.block.Block
import net.minecraft.block.BlockEnderChest
import net.minecraft.block.BlockObsidian
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.Entity
import net.minecraft.entity.EntityLivingBase
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.Packet
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.*
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.*
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.sqrt


internal object HoleFiller: Module(
    name = "HoleFiller",
    category = Category.COMBAT,
    description = "Attempt 1 sponsored by Sn0w"
) {
    var pos: BlockPos? = null
    private val placeableBlocks: Array<Block> = arrayOf(Blocks.AIR, Blocks.FLOWING_LAVA as Block, Blocks.LAVA as Block, Blocks.FLOWING_WATER as Block, Blocks.WATER as Block, Blocks.VINE, Blocks.SNOW_LAYER, Blocks.TALLGRASS as Block, Blocks.FIRE as Block)
    private val holes = ArrayList<BlockPos>()
    private val range by setting("Range", 4, 1..6, 1)
    private val rotate by setting("Rotate", false)
    private val toggle by setting("Toggleable", true)
    private val swing by setting("Swing", false)
    private val blockToUse by setting("Fill Block", FillBlock.OBSIDIAN)

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (findInHotbar() == -1) {
                disable()
            }
            if (holes.isEmpty()) {
                if (toggle) {
                    disable()
                }
                findNewHoles()
            }
            var posToFill: BlockPos? = null
            for (pos in ArrayList<Any?>(holes)) {
                if (pos == null) continue
                if (!valid(pos as BlockPos)) {
                    holes.remove(pos)
                    continue
                }
                posToFill = pos
            }
            if (findInHotbar() == -1) {
                MessageSendHelper.sendChatMessage("Fill blocks not found in hotbar, disabling")
                disable()
            }
            if (posToFill != null) {
                placeBlock(posToFill, findInHotbar(), rotate, rotate, swing)
                holes.remove(posToFill)
            }
        }

        onEnable {
            if (findInHotbar() == -1) disable()
            findNewHoles()
        }
    }

    private fun findNewHoles() {
        holes.clear()
        for (pos in getSphere(BlockPos(floor(mc.player.posX), floor(mc.player.posY), floor(mc.player.posZ)), range.toFloat())) {
            if (!mc.world.getBlockState(pos).block.equals(Blocks.AIR)) continue
            if (!mc.world.getBlockState(pos.add(0, 1, 0)).block.equals(Blocks.AIR)) continue
            if (!mc.world.getBlockState(pos.add(0, 2, 0)).block.equals(Blocks.AIR)) continue
            var possible = true
            for (seems_blocks in arrayOf(BlockPos(0, -1, 0), BlockPos(0, 0, -1), BlockPos(1, 0, 0), BlockPos(0, 0, 1), BlockPos(-1, 0, 0))) {
                val block: Block = mc.world.getBlockState(pos.add(seems_blocks as Vec3i)).block
                if (block !== Blocks.BEDROCK && block !== Blocks.OBSIDIAN && block !== Blocks.ENDER_CHEST && block !== Blocks.ANVIL) {
                    possible = false
                    break
                }
            }
            if (possible) holes.add(pos)
        }
    }

    private fun findInHotbar(): Int {
        for (i in 0..8) {
            val stack: ItemStack = mc.player.inventory.getStackInSlot(i)
            if (stack != ItemStack.EMPTY && stack.item is ItemBlock) {
                val block: Block = (stack.item as ItemBlock).block
                if (block is BlockEnderChest && (blockToUse == FillBlock.ENDER_CHEST)) return i
                if (block is BlockObsidian && (blockToUse == FillBlock.OBSIDIAN)) return i
            }
        }
        return -1
    }

    private fun getSphere(loc: BlockPos, r: Float): List<BlockPos> {
        val circleBlocks = ArrayList<BlockPos>()
        val cx: Int = loc.x
        val cy: Int = loc.y
        val cz: Int = loc.z
        var x = cx - r.toInt()
        while (x <= cx + r) {
            var z = cz - r.toInt()
            while (z <= cz + r) {
                var y = cy - r.toInt()
                while (true) {
                    val f = cy + r
                    if (y >= f) break
                    val dist = ((cx - x) * (cx - x) + (cz - z) * (cz - z) + (cy - y) * (cy - y))
                    if (dist < r * r) {
                        val l = BlockPos(x, y + 0, z)
                        circleBlocks.add(l)
                    }
                    y++
                }
                z++
            }
            x++
        }
        return circleBlocks
    }

    private fun valid(pos: BlockPos): Boolean {
        if (!mc.player.world.checkNoEntityCollision(AxisAlignedBB(pos))) return false
        if (!checkForNeighbours(pos)) return false
        val lState: IBlockState = mc.player.world.getBlockState(pos)
        if (lState.block === Blocks.AIR) {
            val lBlocks = arrayOf<BlockPos>(pos.north(), pos.south(), pos.east(), pos.west(), pos.up(), pos.down())
            for (l_Pos in lBlocks) {
                val lState2: IBlockState = mc.player.world.getBlockState(l_Pos)
                if (lState2.block !== Blocks.AIR) for (side in EnumFacing.values()) {
                    val neighbor: BlockPos = pos.offset(side)
                    if (mc.player.world.getBlockState(neighbor).block.canCollideCheck(mc.player.world.getBlockState(neighbor), false)) return true
                }
            }
            return false
        }
        return false
    }

    private fun checkForNeighbours(blockPos: BlockPos): Boolean {
        if (!hasNeighbour(blockPos)) {
            for (side in EnumFacing.values()) {
                val neighbour: BlockPos = blockPos.offset(side)
                if (hasNeighbour(neighbour)) return true
            }
            return false
        }
        return true
    }

    private fun hasNeighbour(blockPos: BlockPos): Boolean {
        for (side in EnumFacing.values()) {
            val neighbour: BlockPos = blockPos.offset(side)
            if (!mc.world.getBlockState(neighbour).material.isReplaceable) return true
        }
        return false
    }

    private fun placeBlock(pos: BlockPos, slot: Int, rotate: Boolean, rotateBack: Boolean, swing: Boolean): Boolean {
        if (isBlockEmpty(pos)) {
            var oldSlot = -1
            if (slot != mc.player.inventory.currentItem) {
                oldSlot = mc.player.inventory.currentItem
                mc.player.inventory.currentItem = slot
            }
            val facings = EnumFacing.values()
            for (f in facings) {
                val neighborBlock: Block = mc.world.getBlockState(pos.offset(f)).block
                val vec = Vec3d(pos.x + 0.5 + f.xOffset * 0.5, pos.y + 0.5 + f.yOffset * 0.5, pos.z + 0.5 + f.zOffset * 0.5)
                if (!placeableBlocks.contains(neighborBlock) && mc.player.getPositionEyes(mc.renderPartialTicks).distanceTo(vec) <= 4.25) {
                    val rot = floatArrayOf(mc.player.rotationYaw, mc.player.rotationPitch)
                    if (rotate) rotatePacket(vec.x, vec.y, vec.z)
                    if (!blockBlacklist.contains(neighborBlock)) mc.player.connection.sendPacket(CPacketEntityAction(mc.player as Entity, CPacketEntityAction.Action.START_SNEAKING) as Packet<*>)
                    mc.playerController.processRightClickBlock(mc.player, mc.world, pos.offset(f), f.opposite, Vec3d(pos as Vec3i), EnumHand.MAIN_HAND)
                    if (!blockBlacklist.contains(neighborBlock)) mc.player.connection.sendPacket(CPacketEntityAction(mc.player as Entity, CPacketEntityAction.Action.STOP_SNEAKING) as Packet<*>)
                    if (rotateBack) mc.player.connection.sendPacket(CPacketPlayer.Rotation(rot[0], rot[1], mc.player.onGround) as Packet<*>)
                    if (swing) mc.player.swingArm(EnumHand.MAIN_HAND)
                    if (oldSlot != -1) mc.player.inventory.currentItem = oldSlot
                    return true
                }
            }
        }
        return false
    }

    private fun rotatePacket(x: Double, y: Double, z: Double) {
        val diffX: Double = x - mc.player.posX
        val diffY: Double = y - mc.player.posY + mc.player.getEyeHeight()
        val diffZ: Double = z - mc.player.posZ
        val diffXZ = sqrt(diffX * diffX + diffZ * diffZ)
        val yaw = Math.toDegrees(atan2(diffZ, diffX)).toFloat() - 90.0f
        val pitch = (-Math.toDegrees(atan2(diffY, diffXZ))).toFloat()
        mc.player.connection.sendPacket(CPacketPlayer.Rotation(yaw, pitch, mc.player.onGround) as Packet<*>)
    }

    private fun isBlockEmpty(pos: BlockPos): Boolean {
            if (placeableBlocks.contains(mc.world.getBlockState(pos).block)) {
                var e: Entity
                val box = AxisAlignedBB(pos)
                val entityIterator: Iterator<Entity> = mc.world.loadedEntityList.iterator()
                do {
                    if (!entityIterator.hasNext()) return true
                    e = entityIterator.next()
                } while (e !is EntityLivingBase || !box.intersects(e.entityBoundingBox))
            }
        return false
    }

    private enum class FillBlock {
        OBSIDIAN, ENDER_CHEST
    }
}