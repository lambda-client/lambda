package me.zeroeightsix.kami.module.modules.misc

import baritone.api.BaritoneAPI
import baritone.api.pathing.goals.GoalXZ
import com.mojang.realmsclient.gui.ChatFormatting
import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.player.Freecam
import me.zeroeightsix.kami.module.modules.player.NoBreakAnimation
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.colourUtils.ColourHolder
import net.minecraft.block.*
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/**
 * @author Avanatiker
 * @since 15/08/2020
 */
@Module.Info(
        name = "HighwayTools",
        description = "Better High-ways for the greater good.",
        category = Module.Category.MISC
)
class HighwayTools : Module() {
    private val mode = register(Settings.e<Mode>("Mode", Mode.HIGHWAY))
    private val baritoneModee = register(Settings.b("Baritone", true))
    private val infoMessage = register(Settings.b("Logs", true))
    private val blocksPerTick = register(Settings.integerBuilder("BlocksPerTick").withMinimum(1).withValue(1).withMaximum(9).build())
    private val tickDelay = register(Settings.integerBuilder("TickDelay").withMinimum(0).withValue(1).withMaximum(10).build())
    private val rotate = register(Settings.b("Rotate", true))
    private val filled = register(Settings.b("Filled", true))
    private val outline = register(Settings.b("Outline", true))
    private val aFilled = register(Settings.integerBuilder("FilledAlpha").withMinimum(0).withValue(31).withMaximum(255).withVisibility { filled.value }.build())
    private val aOutline = register(Settings.integerBuilder("OutlineAlpha").withMinimum(0).withValue(127).withMaximum(255).withVisibility { outline.value }.build())

    private var offsetStep = 0
    private var delayStep = 0
    private var blocksDonePlacing = 0
    private var playerHotbarSlot = -1
    private var lastHotbarSlot = -1
    private var isSneaking = false
    private var totalTicksRunning = 0
    private var firstRun = false
    private var missingObiDisable = false
    private var ismining = false
    private var nextBlockPos = BlockPos(0,0,0)
    private var currentBlockPos = BlockPos(0,0,0)
    private val directions = listOf("North", "East", "South", "West")

    private var buildDirectionSaved = 0
    private var buildDirectionCoordinateSaved = 0.0
    private var buildDirectionCoordinateSavedY = 0.0
    private var offsetPattern = arrayOfNulls<Vec3d>(0)
    private var maxSteps = 0

    private var totalBlocksPlaced = 0
    private var totalBlocksDestroyed = 0
    private var totalBlocksDistanceWent = 0

    private var placementPendingBlockTiles = ConcurrentHashMap<BlockPos, ColourHolder>()
    private var placedBlocksIteration = ConcurrentHashMap<BlockPos, ColourHolder>()
    private var pendingWrongBlocks = ConcurrentHashMap<BlockPos, ColourHolder>()

    override fun onEnable() {
        if (mc.player == null) {
            disable()
            return
        }
        firstRun = true

        playerHotbarSlot = mc.player.inventory.currentItem
        lastHotbarSlot = -1
        buildDirectionSaved = getPlayerDirection()
        buildDirectionCoordinateSavedY = mc.player.positionVector.y
        if (buildDirectionSaved == 0 || buildDirectionSaved == 2) {
            buildDirectionCoordinateSaved = mc.player.positionVector.x
        }
        else {
            buildDirectionCoordinateSaved = mc.player.positionVector.z
        }

        if (mode.value == Mode.FLAT) {
            offsetPattern = OffsetsBlocks.FLAT
            maxSteps = OffsetsBlocks.FLAT.size
        }
        if (mode.value == Mode.HIGHWAY) {
            if (buildDirectionSaved == 0) {
                offsetPattern = OffsetsBlocks.HIGHWAY_0
                maxSteps = OffsetsBlocks.HIGHWAY_0.size
            }
            else if (buildDirectionSaved == 1) {
                offsetPattern = OffsetsBlocks.HIGHWAY_1
                maxSteps = OffsetsBlocks.HIGHWAY_1.size
            }
            else if (buildDirectionSaved == 2) {
                offsetPattern = OffsetsBlocks.HIGHWAY_2
                maxSteps = OffsetsBlocks.HIGHWAY_2.size
            }
            else if (buildDirectionSaved == 3) {
                offsetPattern = OffsetsBlocks.HIGHWAY_3
                maxSteps = OffsetsBlocks.HIGHWAY_3.size
            }
        }

        MessageSendHelper.sendChatMessage("$chatName Module started." +
                "\n    §9> §rSelected direction: §a" + directions[getPlayerDirection()] + "§r" +
                "\n    §9> §rSnap to coordinate: §a" + buildDirectionCoordinateSaved.roundToInt() + "§r" +
                "\n    §9> §rBaritone mode: §a" + baritoneModee.value + "§r")
    }

    override fun onDisable() {
        if (mc.player == null) return

        // load initial player hand
        if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
            mc.player.inventory.currentItem = playerHotbarSlot
        }
        if (isSneaking) {
            mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING))
            isSneaking = false
        }
        playerHotbarSlot = -1
        lastHotbarSlot = -1
        missingObiDisable = false
        BaritoneAPI.getProvider().primaryBaritone.pathingBehavior.cancelEverything()
        MessageSendHelper.sendChatMessage("$chatName Module stopped." +
                "\n    §9> §rPlaced obsidian: §a" + totalBlocksPlaced + "§r" +
                "\n    §9> §rDestroyed blocks: §a" + totalBlocksDestroyed + "§r" +
                "\n    §9> §rDistance: §a" + totalBlocksDistanceWent + "§r")
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0
        totalBlocksDistanceWent = 0
    }

    override fun onUpdate() {
        if (mc.player == null || KamiMod.MODULE_MANAGER.isModuleEnabled(Freecam::class.java)) {
            return
        }

        if (!firstRun) {
            delayStep = if (delayStep < tickDelay.value) {
                delayStep++
                return
            } else {
                0
            }
        }

        if (firstRun) {
            firstRun = false
            if (findObiInHotbar() == -1) {
                missingObiDisable = true
            }
        }

        var blocksPlaced = 0

        blocksDonePlacing = 0

        // get workload and render
        placementPendingBlockTiles.clear()
        placedBlocksIteration.clear()
        pendingWrongBlocks.clear()
        for(x in 0 until maxSteps) {
            val offsetPos = BlockPos(offsetPattern[x])
            val snappedCoords = BlockPos(mc.player.positionVector)
            snappedCoords.y = buildDirectionCoordinateSavedY.toInt()
            if (getPlayerDirection() == 0 || getPlayerDirection() == 2) {
                snappedCoords.x = buildDirectionCoordinateSaved.toInt()
            }
            else if (getPlayerDirection() == 1 || getPlayerDirection() == 3) {
                snappedCoords.z = buildDirectionCoordinateSaved.toInt()
            }
            val targetPos = BlockPos(snappedCoords).add(offsetPos.x, offsetPos.y, offsetPos.z)
            if (x == 1) {
                currentBlockPos = nextBlockPos
                nextBlockPos = targetPos
            }
            val block = mc.world.getBlockState(targetPos).block
            if (block is BlockAir) {
                placementPendingBlockTiles[targetPos] = ColourHolder(35, 188, 254)
            }
            if (block is BlockObsidian) {
                placementPendingBlockTiles[targetPos] = ColourHolder(50, 50, 50)
                blocksDonePlacing++
            }
        }

        // actually do the work
        while (blocksPlaced < blocksPerTick.value) {
            if (offsetStep >= maxSteps) {
                offsetStep = 0
                break
            }
            val offsetPos = BlockPos(offsetPattern[offsetStep])
            var targetPos = BlockPos(mc.player.positionVector).add(offsetPos.x, offsetPos.y, offsetPos.z)
            if (mode.value == Mode.HIGHWAY) {
                val snappedCoords = BlockPos(mc.player.positionVector)
                snappedCoords.y = buildDirectionCoordinateSavedY.toInt()
                if (getPlayerDirection() == 0 || getPlayerDirection() == 2) {
                    snappedCoords.x = buildDirectionCoordinateSaved.toInt()
                }
                else if (getPlayerDirection() == 1 || getPlayerDirection() == 3) {
                    snappedCoords.z = buildDirectionCoordinateSaved.toInt()
                }
                targetPos = BlockPos(snappedCoords).add(offsetPos.x, offsetPos.y, offsetPos.z)
            }
            if (placeBlock(targetPos)) {
                placedBlocksIteration[targetPos] = ColourHolder(53, 222, 66)
                blocksPlaced++
                totalBlocksPlaced++
            }
            offsetStep++
        }

        // check if row is ready for moving on
        if (baritoneModee.value && (blocksDonePlacing == maxSteps)) {
            moveOneBlock()
        }

        if (blocksPlaced > 0) {
            if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
                mc.player.inventory.currentItem = playerHotbarSlot
                lastHotbarSlot = playerHotbarSlot
            }
            if (isSneaking) {
                mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.STOP_SNEAKING))
                isSneaking = false
            }
        }

        totalTicksRunning++

        if (missingObiDisable && infoMessage.value) {
            missingObiDisable = false
            MessageSendHelper.sendChatMessage("$chatName " + ChatFormatting.RED + "Disabled" + ChatFormatting.RESET + ", Obsidian missing!")
            disable()
        }
    }

    private fun getPlayerDirection(): Int {
        val yaw = (mc.player.rotationYaw % 360 + 360) % 360
        if (yaw >= 135 && yaw < 225) {
            return 0 //NORTH
        }
        else if (yaw >= 225 && yaw < 315) {
            return 1 //EAST
        }
        else if (yaw >= 315 || yaw < 45) {
            return 2 //SOUTH
        }
        else if (yaw >= 45 && yaw < 135){
            return 3 //WEST
        }
        else {
            return -1
        }
    }

    private fun placeBlock(pos: BlockPos): Boolean {
        // check if block is already placed
        val block = mc.world.getBlockState(pos).block
        if (block !is BlockAir && block !is BlockLiquid) {
            if (block !is BlockObsidian) {
                pendingWrongBlocks[pos] = ColourHolder(222, 0, 0)
                val backupYaw = mc.player.rotationYaw
                val backupPitch = mc.player.rotationPitch
                mineBlock(pos, true)
                Executors.newSingleThreadScheduledExecutor().schedule({
                    mineBlock(pos, false)
                    mc.player.rotationYaw = backupYaw
                    mc.player.rotationPitch = backupPitch
                    totalBlocksDestroyed++
                }, tickDelay.value * 50L, TimeUnit.MILLISECONDS)
                blocksDonePlacing--
            }
            return false
        }

        // check if entity blocks placing
        for (entity in mc.world.getEntitiesWithinAABBExcludingEntity(null, AxisAlignedBB(pos))) {
            if (entity !is EntityItem && entity !is EntityXPOrb) {
                return false
            }
        }
        val side = getPlaceableSide(pos) ?: return false

        // check if we have a block adjacent to blockpos to click at
        val neighbour = pos.offset(side)
        val opposite = side.opposite

        // check if neighbor can be right clicked
        if (!BlockUtils.canBeClicked(neighbour)) {
            return false
        }

        val hitVec = Vec3d(neighbour).add(0.5, 0.5, 0.5).add(Vec3d(opposite.directionVec).scale(0.5))
        val neighbourBlock = mc.world.getBlockState(neighbour).block
        val obiSlot = findObiInHotbar()

        if (obiSlot == -1) {
            missingObiDisable = true
            return false
        }
        if (lastHotbarSlot != obiSlot) {
            mc.player.inventory.currentItem = obiSlot
            lastHotbarSlot = obiSlot
        }
        if (!isSneaking && BlockUtils.blackList.contains(neighbourBlock) || BlockUtils.shulkerList.contains(neighbourBlock)) {
            mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING))
            isSneaking = true
        }
        if (rotate.value) {
            BlockUtils.faceVectorPacketInstant(hitVec)
        }

        mc.playerController.processRightClickBlock(mc.player, mc.world, neighbour, opposite, hitVec, EnumHand.MAIN_HAND)
        mc.player.swingArm(EnumHand.MAIN_HAND)
        mc.rightClickDelayTimer = 4

        if (KamiMod.MODULE_MANAGER.isModuleEnabled(NoBreakAnimation::class.java)) {
            KamiMod.MODULE_MANAGER.getModuleT(NoBreakAnimation::class.java).resetMining()
        }
        return true
    }

    private fun mineBlock(pos: BlockPos, pre: Boolean) {
        // get mining tool in mainhand
        if (InventoryUtils.getSlotsHotbar(278) == null && InventoryUtils.getSlotsNoHotbar(278) != null) {
            InventoryUtils.moveToHotbar(278, 130, (tickDelay.value * 16).toLong())
            return
        } else if (InventoryUtils.getSlots(0, 35, 278) == null) {
            MessageSendHelper.sendChatMessage("$chatName No pickaxe was found in inventory, disabling.")
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            this.disable()
            return
        }
        InventoryUtils.swapSlotToItem(278)
        lookAtBlock(pos)

        /* Packet mining lol */
        if (pre) {
            mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit))
            //if (state != AutoObsidian.State.SEARCHING) state = AutoObsidian.State.MINING else searchingState = AutoObsidian.SearchingState.MINING
        } else {
            mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit))
        }
        mc.player.swingArm(EnumHand.MAIN_HAND)
    }

    // just experimental
    private fun mineBlockBaritone(pos: BlockPos) {
        ismining = true
        val bapi = BaritoneAPI.getProvider().getPrimaryBaritone().getCommandManager()
        bapi.execute("sel 1 " + pos.x + " " + pos.y + " " + pos.z)
        bapi.execute("sel 2 " + pos.x + " " + pos.y + " " + pos.z)
        bapi.execute("sel ca")
        bapi.execute("sel clear")
        BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(GoalXZ(nextBlockPos.getX(), nextBlockPos.getZ()))
        ismining = false
    }

    private fun moveOneBlock() {
        // set head rotation to get max walking speed
        if (getPlayerDirection() == 0) {
            mc.player.rotationYaw = -180F
        }
        else if (getPlayerDirection() == 1) {
            mc.player.rotationYaw = -90F
        }
        else if (getPlayerDirection() == 2) {
            mc.player.rotationYaw = 0F
        } else {
            mc.player.rotationYaw = 90F
        }
        mc.player.rotationPitch = 0F
        //move to next block pos
        BaritoneAPI.getProvider().primaryBaritone.customGoalProcess.setGoalAndPath(GoalXZ(nextBlockPos.getX(), nextBlockPos.getZ()))
        totalBlocksDistanceWent++
    }

    private fun lookAtBlock(pos: BlockPos) {
        val vec3d = Vec3d((pos.x + 0.5) - mc.player.posX, pos.y - (mc.player.eyeHeight + mc.player.posY), (pos.z + 0.5) - mc.player.posZ)
        val lookAt = EntityUtils.getRotationFromVec3d(vec3d)
        mc.player.rotationYaw = lookAt[0].toFloat()
        mc.player.rotationPitch = lookAt[1].toFloat()
    }

    override fun onWorldRender(event: RenderEvent) {
        if (mc.player == null) return
        val side = GeometryMasks.Quad.ALL
        val renderer = ESPRenderer(event.partialTicks)
        renderer.aFilled = if (filled.value) aFilled.value else 0
        renderer.aOutline = if (outline.value) aOutline.value else 0
        if (!placementPendingBlockTiles.isEmpty()) {
            for ((pos, colour) in placementPendingBlockTiles) {
                renderer.add(pos, colour, side)
            }
        }
        if (!placedBlocksIteration.isEmpty()) {
            for ((pos, colour) in placedBlocksIteration) {
                renderer.add(pos, colour, side)
            }
        }
        if (!pendingWrongBlocks.isEmpty()) {
            for ((pos, colour) in pendingWrongBlocks) {
                renderer.add(pos, colour, side)
            }
        }
        renderer.render()
    }

    private fun findObiInHotbar(): Int {
        // search blocks in hotbar
        var slot = -1
        for (i in 0..8) {
            // filter out non-block items
            val stack = mc.player.inventory.getStackInSlot(i)
            if (stack == ItemStack.EMPTY || stack.getItem() !is ItemBlock) {
                continue
            }
            val block = (stack.getItem() as ItemBlock).block
            if (block is BlockObsidian) {
                slot = i
                break
            }
            //else if (block is BlockNetherrack) {
            //    slot = i
            //    break
            //}
        }
        return slot
    }

    private enum class Mode {
        FLAT, HIGHWAY
    }

    private object Offsets_Space {
        val HIGHWAY_0 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(0.0, -1.0, -1.0),
                Vec3d(-1.0, -1.0, -1.0),
                Vec3d(1.0, -1.0, -1.0),
                Vec3d(-2.0, -1.0, -1.0),
                Vec3d(2.0, -1.0, -1.0),
                Vec3d(-3.0, -1.0, -1.0),
                Vec3d(3.0, -1.0, -1.0),
                Vec3d(3.0, 0.0, -1.0),
                Vec3d(-3.0, 0.0, -1.0)
        )
        val HIGHWAY_1 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(1.0, -1.0, 0.0),
                Vec3d(1.0, -1.0, -1.0),
                Vec3d(1.0, -1.0, 1.0),
                Vec3d(1.0, -1.0, -2.0),
                Vec3d(1.0, -1.0, 2.0),
                Vec3d(1.0, -1.0, -3.0),
                Vec3d(1.0, -1.0, 3.0),
                Vec3d(1.0, 0.0, 3.0),
                Vec3d(1.0, 0.0, -3.0)
        )
        val HIGHWAY_2 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(0.0, -1.0, 1.0),
                Vec3d(-1.0, -1.0, 1.0),
                Vec3d(1.0, -1.0, 1.0),
                Vec3d(-2.0, -1.0, 1.0),
                Vec3d(2.0, -1.0, 1.0),
                Vec3d(-3.0, -1.0, 1.0),
                Vec3d(3.0, -1.0, 1.0),
                Vec3d(3.0, 0.0, 1.0),
                Vec3d(-3.0, 0.0, 1.0)
        )
        val HIGHWAY_3 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(-1.0, -1.0, 0.0),
                Vec3d(-1.0, -1.0, -1.0),
                Vec3d(-1.0, -1.0, 1.0),
                Vec3d(-1.0, -1.0, -2.0),
                Vec3d(-1.0, -1.0, 2.0),
                Vec3d(-1.0, -1.0, -3.0),
                Vec3d(-1.0, -1.0, 3.0),
                Vec3d(-1.0, 0.0, 3.0),
                Vec3d(-1.0, 0.0, -3.0)
        )
    }

    private object OffsetsBlocks {
        val FLAT = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(1.0, -1.0, 0.0),
                Vec3d(-1.0, -1.0, 0.0),
                Vec3d(0.0, -1.0, 1.0),
                Vec3d(0.0, -1.0, -1.0),
                Vec3d(1.0, -1.0, 1.0),
                Vec3d(-1.0, -1.0, -1.0),
                Vec3d(1.0, -1.0, -1.0),
                Vec3d(-1.0, -1.0, 1.0),
                Vec3d(2.0, -1.0, 0.0),
                Vec3d(-2.0, -1.0, 0.0),
                Vec3d(0.0, -1.0, 2.0),
                Vec3d(0.0, -1.0, -2.0),
                Vec3d(1.0, -1.0, 2.0),
                Vec3d(-1.0, -1.0, 2.0),
                Vec3d(2.0, -1.0, -1.0),
                Vec3d(2.0, -1.0, 1.0),
                Vec3d(-2.0, -1.0, -1.0),
                Vec3d(-2.0, -1.0, 1.0),
                Vec3d(1.0, -1.0, -2.0),
                Vec3d(-1.0, -1.0, -2.0),
                Vec3d(2.0, -1.0, 2.0),
                Vec3d(-2.0, -1.0, 2.0),
                Vec3d(-2.0, -1.0, -2.0),
                Vec3d(2.0, -1.0, -2.0)
        )
        val HIGHWAY_0 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(0.0, -1.0, -1.0),
                Vec3d(-1.0, -1.0, -1.0),
                Vec3d(1.0, -1.0, -1.0),
                Vec3d(-2.0, -1.0, -1.0),
                Vec3d(2.0, -1.0, -1.0),
                Vec3d(-3.0, -1.0, -1.0),
                Vec3d(3.0, -1.0, -1.0),
                Vec3d(3.0, 0.0, -1.0),
                Vec3d(-3.0, 0.0, -1.0)
        )
        val HIGHWAY_1 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(1.0, -1.0, 0.0),
                Vec3d(1.0, -1.0, -1.0),
                Vec3d(1.0, -1.0, 1.0),
                Vec3d(1.0, -1.0, -2.0),
                Vec3d(1.0, -1.0, 2.0),
                Vec3d(1.0, -1.0, -3.0),
                Vec3d(1.0, -1.0, 3.0),
                Vec3d(1.0, 0.0, 3.0),
                Vec3d(1.0, 0.0, -3.0)
        )
        val HIGHWAY_2 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(0.0, -1.0, 1.0),
                Vec3d(-1.0, -1.0, 1.0),
                Vec3d(1.0, -1.0, 1.0),
                Vec3d(-2.0, -1.0, 1.0),
                Vec3d(2.0, -1.0, 1.0),
                Vec3d(-3.0, -1.0, 1.0),
                Vec3d(3.0, -1.0, 1.0),
                Vec3d(3.0, 0.0, 1.0),
                Vec3d(-3.0, 0.0, 1.0)
        )
        val HIGHWAY_3 = arrayOf<Vec3d?>(
                Vec3d(0.0, -1.0, 0.0),
                Vec3d(-1.0, -1.0, 0.0),
                Vec3d(-1.0, -1.0, -1.0),
                Vec3d(-1.0, -1.0, 1.0),
                Vec3d(-1.0, -1.0, -2.0),
                Vec3d(-1.0, -1.0, 2.0),
                Vec3d(-1.0, -1.0, -3.0),
                Vec3d(-1.0, -1.0, 3.0),
                Vec3d(-1.0, 0.0, 3.0),
                Vec3d(-1.0, 0.0, -3.0)
        )
    }

    companion object {
        private fun getPlaceableSide(pos: BlockPos): EnumFacing? {
            for (side in EnumFacing.values()) {
                val neighbour = pos.offset(side)
                if (!mc.world.getBlockState(neighbour).block.canCollideCheck(mc.world.getBlockState(neighbour), false)) {
                    continue
                }
                val blockState = mc.world.getBlockState(neighbour)
                if (!blockState.material.isReplaceable) {
                    return side
                }
            }
            return null
        }
    }
}