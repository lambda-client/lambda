package me.zeroeightsix.kami.module.modules.misc

import baritone.api.BaritoneAPI
import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.KamiMod.MODULE_MANAGER
import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.player.LagNotifier
import me.zeroeightsix.kami.module.modules.player.NoBreakAnimation
import me.zeroeightsix.kami.setting.Setting
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.colourUtils.ColourHolder
import net.minecraft.block.*
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.math.roundToInt


/**
 * @author Avanatiker
 * @since 20/08/2020
 */

@Module.Info(
        name = "HighwayTools",
        description = "Even Better High-ways for the greater good.",
        category = Module.Category.MISC
)
class HighwayTools : Module() {
    private val mode = register(Settings.e<Mode>("Mode", Mode.HIGHWAY))
    val baritoneMode: Setting<Boolean> = register(Settings.b("Baritone", true))
    private val blocksPerTick = register(Settings.integerBuilder("Blocks Per Tick").withMinimum(1).withValue(1).withMaximum(9).build())
    private val tickDelay = register(Settings.integerBuilder("Tick-Delay Place").withMinimum(0).withValue(0).withMaximum(10).build())
    private val tickDelayBreak = register(Settings.integerBuilder("Tick-Delay Break").withMinimum(0).withValue(0).withMaximum(10).build())
    private val rotate = register(Settings.b("Rotate", true))
    private val filled = register(Settings.b("Filled", true))
    private val outline = register(Settings.b("Outline", true))
    private val aFilled = register(Settings.integerBuilder("Filled Alpha").withMinimum(0).withValue(31).withMaximum(255).withVisibility { filled.value }.build())
    private val aOutline = register(Settings.integerBuilder("Outline Alpha").withMinimum(0).withValue(127).withMaximum(255).withVisibility { outline.value }.build())

    private var playerHotbarSlot = -1
    private var lastHotbarSlot = -1
    private var isSneaking = false
    private var buildDirectionSaved = 0
    private var buildDirectionCoordinateSaved = 0.0
    private var buildDirectionCoordinateSavedY = 0.0
    private val directions = listOf("North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West")

    //Stats
    private var totalBlocksPlaced = 0
    var totalBlocksDestroyed = 0
    private var totalBlocksDistanceWent = 0

    val blockQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private val doneQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private var blockOffsets = mutableListOf<Pair<BlockPos, Boolean>>()
    private var waitTicks = 0
    private var blocksPlaced = 0

    override fun onEnable() {
        if (mc.player == null) {
            disable()
            return
        }
        buildDirectionSaved = getPlayerDirection()
        playerHotbarSlot = mc.player.inventory.currentItem
        lastHotbarSlot = -1

        playerHotbarSlot = mc.player.inventory.currentItem
        buildDirectionCoordinateSavedY = mc.player.positionVector.y
        buildDirectionCoordinateSaved = if (buildDirectionSaved == 0 || buildDirectionSaved == 4) { mc.player.positionVector.x }
        else if (buildDirectionSaved == 2 || buildDirectionSaved == 6) { mc.player.positionVector.z }
        else { 0.0 }

        blockQueue.clear()
        doneQueueReset()
        updateTasks()
        if (buildDirectionSaved == 1 || buildDirectionSaved == 3 || buildDirectionSaved == 5 || buildDirectionSaved == 5 || buildDirectionSaved == 7) {
            MessageSendHelper.sendChatMessage("$chatName Module started." +
                    "\n    §9> §rSelected direction: §a" + directions[buildDirectionSaved] + "§r" +
                    "\n    §9> §rSnap to coordinates: §a" + mc.player.positionVector.x.roundToInt() + ", " + mc.player.positionVector.z.roundToInt() + "§r" +
                    "\n    §9> §rBaritone mode: §a" + baritoneMode.value + "§r")
        } else {
            MessageSendHelper.sendChatMessage("$chatName Module started." +
                    "\n    §9> §rSelected direction: §a" + directions[buildDirectionSaved] + "§r" +
                    "\n    §9> §rSnap to coordinate: §a" + buildDirectionCoordinateSaved.roundToInt() + "§r" +
                    "\n    §9> §rBaritone mode: §a" + baritoneMode.value + "§r")
        }

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
        if (mc.playerController == null) return

        if (!isDone()) {
            doTask()
        }
    }

    fun done() {
        doneQueueReset()
        updateTasks()
        totalBlocksDistanceWent++
    }

    private fun addTask(bps: BlockPos, ts: TaskState, bb: Boolean) {
        blockQueue.add(BlockTask(bps, ts, bb))
    }

    private fun printDebug() {
        MessageSendHelper.sendChatMessage("#### LOG ####")
        for (bt in blockQueue) {
            MessageSendHelper.sendChatMessage(bt.getBlockPos().toString() + " " + bt.getTaskState().toString() + " " + bt.getBlock().toString())
        }
        MessageSendHelper.sendChatMessage("#### DONE ####")
        for (bt in doneQueue) {
            MessageSendHelper.sendChatMessage(bt.getBlockPos().toString() + " " + bt.getTaskState().toString() + " " + bt.getBlock().toString())
        }
    }

    private fun doTask(): Boolean {
        if (!isDone() && !MODULE_MANAGER.getModuleT(LagNotifier::class.java).paused) {
            if (waitTicks == 0) {
                val blockAction = blockQueue.peek()
                BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.registerProcess(KamiMod.highwayToolsProcess)
                if (blockAction.getTaskState() == TaskState.BREAK) {
                    val block = mc.world.getBlockState(blockAction.getBlockPos()).block
                    for (side in EnumFacing.values()) {
                        val neighbour = blockAction.getBlockPos().offset(side)
                        var found = false
                        if (mc.world.getBlockState(neighbour).block is BlockLiquid) {
                            for (bt in blockQueue) {
                                if (bt.getBlockPos() == neighbour) {
                                    found = true
                                }
                            }
                            if (!found) {
                                var insideBuild = false
                                for ((pos, buildBlock) in blockOffsets) {
                                    if (neighbour == pos) {
                                        if (!buildBlock) { insideBuild = true }
                                    }
                                }
                                if (insideBuild) {
                                    addTask(neighbour, TaskState.PLACE, false)
                                } else {
                                    addTask(neighbour, TaskState.PLACE, true)
                                }
                            }
                        }
                    }
                    when (block) {
                        is BlockAir -> {
                            blockAction.setTaskState(TaskState.BROKE)
                            doTask()
                        }
                        is BlockLiquid -> {
                            blockAction.setTaskState(TaskState.PLACE)
                            doTask()
                        }
                        else -> {
                            mineBlock(blockAction.getBlockPos(), true)
                            blockAction.setTaskState(TaskState.BREAKING)
                            waitTicks = if (block is BlockNetherrack || block is BlockMagma) {
                                0
                            } else {
                                //val efficiencyLevel = 5
                                //waitTicks = (block.blockHardness * 5.0 / (8 + efficiencyLevel * efficiencyLevel + 1) / 20).toInt()
                                5
                            }
                        }
                    }
                } else if (blockAction.getTaskState() == TaskState.BREAKING) {
                    mineBlock(blockAction.getBlockPos(), false)
                    blockAction.setTaskState(TaskState.BROKE)
                } else if (blockAction.getTaskState() == TaskState.BROKE) {
                    val block = mc.world.getBlockState(blockAction.getBlockPos()).block
                    if (block is BlockAir) {
                        totalBlocksDestroyed++
                        waitTicks = tickDelayBreak.value
                        if (blockAction.getBlock()) {
                            blockAction.setTaskState(TaskState.PLACE)
                        } else {
                            blockAction.setTaskState(TaskState.DONE)
                        }
                        doTask()
                    } else {
                        blockAction.setTaskState(TaskState.BREAK)
                    }
                } else if (blockAction.getTaskState() == TaskState.PLACE) {
                    if (placeBlock(blockAction.getBlockPos())) {
                        blockAction.setTaskState(TaskState.PLACED)
                        if (blocksPerTick.value > blocksPlaced + 1) {
                            blocksPlaced++
                            doTask()
                        } else {
                            blocksPlaced = 0
                        }
                        waitTicks = tickDelay.value
                        totalBlocksPlaced++
                    } else {
                        return false
                    }
                } else if (blockAction.getTaskState() == TaskState.PLACED) {
                    if (blockAction.getBlock()) {
                        blockAction.setTaskState(TaskState.DONE)
                    } else {
                        blockAction.setTaskState(TaskState.BREAK)
                    }
                    doTask()
                } else if (blockAction.getTaskState() == TaskState.DONE) {
                    blockQueue.remove()
                    doneQueue.add(blockAction)
                    doTask()
                }
            } else {
                waitTicks--
            }
            return true
        } else {
            return false
        }
    }

    private fun updateTasks() {
        updateBlockArray()
        for ((a, b) in blockOffsets) {
            val block = mc.world.getBlockState(a).block
            if (b && block is BlockAir) { addTask(a, TaskState.PLACE, true) }
            else if (b && block !is BlockAir && block !is BlockObsidian) { addTask(a, TaskState.BREAK, true) }
            else if (!b && block !is BlockAir) { addTask(a, TaskState.BREAK, false) }
            else if (b && block is BlockObsidian) { addTask(a, TaskState.DONE, true) }
            else if (!b && block is BlockAir) { addTask(a, TaskState.DONE, false) }
        }
    }

    private fun updateRenderer(renderer: ESPRenderer): ESPRenderer {
        val side = GeometryMasks.Quad.ALL
        for (bt in blockQueue) {
            if (bt.getTaskState() != TaskState.DONE) { renderer.add(bt.getBlockPos(), bt.getTaskState().color, side) }
        }
        for (bt in doneQueue) {
            if (bt.getBlock()) { renderer.add(bt.getBlockPos(), bt.getTaskState().color, side) }
        }
        return renderer
    }

    fun getNextBlock(): BlockPos {
        // set head rotation to get max walking speed
        val nextBlockPos: BlockPos
        when (buildDirectionSaved) {
            0 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).north()
                mc.player.rotationYaw = -180F
            }
            1 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).north().east()
                mc.player.rotationYaw = -135F
            }
            2 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).east()
                mc.player.rotationYaw = -90F
            }
            3 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).south().east()
                mc.player.rotationYaw = -45F
            }
            4 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).south()
                mc.player.rotationYaw = 0F
            }
            5 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).south().west()
                mc.player.rotationYaw = 45F
            }
            6 -> {
                nextBlockPos = BlockPos(mc.player.positionVector).west()
                mc.player.rotationYaw = 90F
            }
            else -> {
                nextBlockPos = BlockPos(mc.player.positionVector).north().west()
                mc.player.rotationYaw = 135F
            }
        }
        mc.player.rotationPitch = 0F
        return nextBlockPos
    }

    private fun mineBlock(pos: BlockPos, pre: Boolean) {
        if (InventoryUtils.getSlotsHotbar(278) == null && InventoryUtils.getSlotsNoHotbar(278) != null) {
            InventoryUtils.moveToHotbar(278, 130, (tickDelay.value * 16).toLong())
            return
        } else if (InventoryUtils.getSlots(0, 35, 278) == null) {
            MessageSendHelper.sendChatMessage("$chatName No Pickaxe was found in inventory")
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            return
        }
        InventoryUtils.swapSlotToItem(278)
        lookAtBlock(pos)

        if (pre) {
            mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit))
        } else {
            mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, mc.objectMouseOver.sideHit))
        }
        mc.player.swingArm(EnumHand.MAIN_HAND)
    }

    private fun placeBlock(pos: BlockPos): Boolean
    {
        // check if block is already placed
        val block = mc.world.getBlockState(pos).block
        if (block !is BlockAir && block !is BlockLiquid) {
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

        //Swap to Obsidian in Hotbar or get from inventory
        if (InventoryUtils.getSlotsHotbar(49) == null && InventoryUtils.getSlotsNoHotbar(49) != null) {
            InventoryUtils.moveToHotbar(49, 130, (tickDelay.value * 16).toLong())
            InventoryUtils.quickMoveSlot(1, (tickDelay.value * 16).toLong())
            return false
        } else if (InventoryUtils.getSlots(0, 35, 49) == null) {
            MessageSendHelper.sendChatMessage("$chatName No Obsidian was found in inventory")
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            return false
        }
        InventoryUtils.swapSlotToItem(49)

        val hitVec = Vec3d(neighbour).add(0.5, 0.5, 0.5).add(Vec3d(opposite.directionVec).scale(0.5))
        val neighbourBlock = mc.world.getBlockState(neighbour).block

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

        if (MODULE_MANAGER.isModuleEnabled(NoBreakAnimation::class.java)) {
            MODULE_MANAGER.getModuleT(NoBreakAnimation::class.java).resetMining()
        }
        return true
    }

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

    private fun lookAtBlock(pos: BlockPos) {
        val vec3d = Vec3d((pos.x + 0.5) - mc.player.posX, pos.y - (mc.player.eyeHeight + mc.player.posY), (pos.z + 0.5) - mc.player.posZ)
        val lookAt = EntityUtils.getRotationFromVec3d(vec3d)
        mc.player.rotationYaw = lookAt[0].toFloat()
        mc.player.rotationPitch = lookAt[1].toFloat()
    }

    private fun updateBlockArray() {
        blockOffsets.clear()
        val b = BlockPos(mc.player.positionVector)

        when(mode.value) {
            Mode.HIGHWAY -> {
                when (buildDirectionSaved) {
                    0 -> { //NORTH
                        blockOffsets.add(Pair(b.down().north(), true))
                        blockOffsets.add(Pair(b.down().north().north(), true))
                        blockOffsets.add(Pair(b.down().north().north().east(), true))
                        blockOffsets.add(Pair(b.down().north().north().west(), true))
                        blockOffsets.add(Pair(b.down().north().north().east().east(), true))
                        blockOffsets.add(Pair(b.down().north().north().west().west(), true))
                        blockOffsets.add(Pair(b.down().north().north().east().east().east(), true))
                        blockOffsets.add(Pair(b.down().north().north().west().west().west(), true))
                        blockOffsets.add(Pair(b.north().north().east().east().east(), true))
                        blockOffsets.add(Pair(b.north().north().west().west().west(), true))
                        blockOffsets.add(Pair(b.north().north(), false))
                        blockOffsets.add(Pair(b.north().north().east(), false))
                        blockOffsets.add(Pair(b.north().north().west(), false))
                        blockOffsets.add(Pair(b.north().north().east().east(), false))
                        blockOffsets.add(Pair(b.north().north().west().west(), false))
                        blockOffsets.add(Pair(b.up().north().north(), false))
                        blockOffsets.add(Pair(b.up().north().north().east(), false))
                        blockOffsets.add(Pair(b.up().north().north().west(), false))
                        blockOffsets.add(Pair(b.up().north().north().east().east(), false))
                        blockOffsets.add(Pair(b.up().north().north().west().west(), false))
                        blockOffsets.add(Pair(b.up().north().north().east().east().east(), false))
                        blockOffsets.add(Pair(b.up().north().north().west().west().west(), false))
                        blockOffsets.add(Pair(b.up().up().north().north(), false))
                        blockOffsets.add(Pair(b.up().up().north().north().east(), false))
                        blockOffsets.add(Pair(b.up().up().north().north().west(), false))
                        blockOffsets.add(Pair(b.up().up().north().north().east().east(), false))
                        blockOffsets.add(Pair(b.up().up().north().north().west().west(), false))
                        blockOffsets.add(Pair(b.up().up().north().north().east().east().east(), false))
                        blockOffsets.add(Pair(b.up().up().north().north().west().west().west(), false))
                    }
                    1 -> { // NORTH-EAST
                        blockOffsets.add(Pair(b.north().east().down(), true))
                        blockOffsets.add(Pair(b.north().east().down().north(), true))
                        blockOffsets.add(Pair(b.north().east().down().east(), true))
                        blockOffsets.add(Pair(b.north().east().down().north().east(), true))
                        blockOffsets.add(Pair(b.north().east().down().north().north(), true))
                        blockOffsets.add(Pair(b.north().east().down().east().east(), true))
                        blockOffsets.add(Pair(b.north().east().down().east().east().south(), true))
                        blockOffsets.add(Pair(b.north().east().down().north().north().west(), true))
                        blockOffsets.add(Pair(b.north().east().down().east().east().south().east(), true))
                        blockOffsets.add(Pair(b.north().east().down().north().north().west().north(), true))
                        blockOffsets.add(Pair(b.north().east().east().east().south().east(), true))
                        blockOffsets.add(Pair(b.north().east().north().north().west().north(), true))
                        blockOffsets.add(Pair(b.north().east().north(), false))
                        blockOffsets.add(Pair(b.north().east().north().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().east(), false))
                        blockOffsets.add(Pair(b.north().east().east().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().east(), false))
                        blockOffsets.add(Pair(b.north().east().north().east().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().east().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().north(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().east(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().west(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().west().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().west().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().south(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().south().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().south().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().west().north().up(), false))
                        blockOffsets.add(Pair(b.north().east().north().north().west().north().up().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().south().east().up(), false))
                        blockOffsets.add(Pair(b.north().east().east().east().south().east().up().up(), false))
                    }
                    2 -> { //EAST
                        blockOffsets.add(Pair(b.down().east(), true))
                        blockOffsets.add(Pair(b.down().east().east(), true))
                        blockOffsets.add(Pair(b.down().east().east().south(), true))
                        blockOffsets.add(Pair(b.down().east().east().north(), true))
                        blockOffsets.add(Pair(b.down().east().east().south().south(), true))
                        blockOffsets.add(Pair(b.down().east().east().north().north(), true))
                        blockOffsets.add(Pair(b.down().east().east().south().south().south(), true))
                        blockOffsets.add(Pair(b.down().east().east().north().north().north(), true))
                        blockOffsets.add(Pair(b.east().east().south().south().south(), true))
                        blockOffsets.add(Pair(b.east().east().north().north().north(), true))
                        blockOffsets.add(Pair(b.east().east(), false))
                        blockOffsets.add(Pair(b.east().east().south(), false))
                        blockOffsets.add(Pair(b.east().east().north(), false))
                        blockOffsets.add(Pair(b.east().east().south().south(), false))
                        blockOffsets.add(Pair(b.east().east().north().north(), false))
                        blockOffsets.add(Pair(b.up().east().east(), false))
                        blockOffsets.add(Pair(b.up().east().east().south(), false))
                        blockOffsets.add(Pair(b.up().east().east().north(), false))
                        blockOffsets.add(Pair(b.up().east().east().south().south(), false))
                        blockOffsets.add(Pair(b.up().east().east().north().north(), false))
                        blockOffsets.add(Pair(b.up().east().east().south().south().south(), false))
                        blockOffsets.add(Pair(b.up().east().east().north().north().north(), false))
                        blockOffsets.add(Pair(b.up().up().east().east(), false))
                        blockOffsets.add(Pair(b.up().up().east().east().south(), false))
                        blockOffsets.add(Pair(b.up().up().east().east().north(), false))
                        blockOffsets.add(Pair(b.up().up().east().east().south().south(), false))
                        blockOffsets.add(Pair(b.up().up().east().east().north().north(), false))
                        blockOffsets.add(Pair(b.up().up().east().east().south().south().south(), false))
                        blockOffsets.add(Pair(b.up().up().east().east().north().north().north(), false))
                    }
                    3 -> { //SOUTH-EAST
                        blockOffsets.add(Pair(b.east().south().down(), true))
                        blockOffsets.add(Pair(b.east().south().down().east(), true))
                        blockOffsets.add(Pair(b.east().south().down().south(), true))
                        blockOffsets.add(Pair(b.east().south().down().east().south(), true))
                        blockOffsets.add(Pair(b.east().south().down().east().east(), true))
                        blockOffsets.add(Pair(b.east().south().down().south().south(), true))
                        blockOffsets.add(Pair(b.east().south().down().south().south().west(), true))
                        blockOffsets.add(Pair(b.east().south().down().east().east().north(), true))
                        blockOffsets.add(Pair(b.east().south().down().south().south().west().south(), true))
                        blockOffsets.add(Pair(b.east().south().down().east().east().north().east(), true))
                        blockOffsets.add(Pair(b.east().south().south().south().west().south(), true))
                        blockOffsets.add(Pair(b.east().south().east().east().north().east(), true))
                        blockOffsets.add(Pair(b.east().south().east(), false))
                        blockOffsets.add(Pair(b.east().south().east().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().south(), false))
                        blockOffsets.add(Pair(b.east().south().south().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().south(), false))
                        blockOffsets.add(Pair(b.east().south().east().south().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().south().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().east(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().south(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().north(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().north().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().north().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().west(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().west().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().west().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().north().east().up(), false))
                        blockOffsets.add(Pair(b.east().south().east().east().north().east().up().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().west().south().up(), false))
                        blockOffsets.add(Pair(b.east().south().south().south().west().south().up().up(), false))
                    }
                    4 -> { //SOUTH
                        blockOffsets.add(Pair(b.down().south(), true))
                        blockOffsets.add(Pair(b.down().south().south(), true))
                        blockOffsets.add(Pair(b.down().south().south().east(), true))
                        blockOffsets.add(Pair(b.down().south().south().west(), true))
                        blockOffsets.add(Pair(b.down().south().south().east().east(), true))
                        blockOffsets.add(Pair(b.down().south().south().west().west(), true))
                        blockOffsets.add(Pair(b.down().south().south().east().east().east(), true))
                        blockOffsets.add(Pair(b.down().south().south().west().west().west(), true))
                        blockOffsets.add(Pair(b.south().south().east().east().east(), true))
                        blockOffsets.add(Pair(b.south().south().west().west().west(), true))
                        blockOffsets.add(Pair(b.south().south(), false))
                        blockOffsets.add(Pair(b.south().south().east(), false))
                        blockOffsets.add(Pair(b.south().south().west(), false))
                        blockOffsets.add(Pair(b.south().south().east().east(), false))
                        blockOffsets.add(Pair(b.south().south().west().west(), false))
                        blockOffsets.add(Pair(b.up().south().south(), false))
                        blockOffsets.add(Pair(b.up().south().south().east(), false))
                        blockOffsets.add(Pair(b.up().south().south().west(), false))
                        blockOffsets.add(Pair(b.up().south().south().east().east(), false))
                        blockOffsets.add(Pair(b.up().south().south().west().west(), false))
                        blockOffsets.add(Pair(b.up().south().south().east().east().east(), false))
                        blockOffsets.add(Pair(b.up().south().south().west().west().west(), false))
                        blockOffsets.add(Pair(b.up().up().south().south(), false))
                        blockOffsets.add(Pair(b.up().up().south().south().east(), false))
                        blockOffsets.add(Pair(b.up().up().south().south().west(), false))
                        blockOffsets.add(Pair(b.up().up().south().south().east().east(), false))
                        blockOffsets.add(Pair(b.up().up().south().south().west().west(), false))
                        blockOffsets.add(Pair(b.up().up().south().south().east().east().east(), false))
                        blockOffsets.add(Pair(b.up().up().south().south().west().west().west(), false))
                    }
                    5 -> { // SOUTH-WEST
                        blockOffsets.add(Pair(b.south().west().down(), true))
                        blockOffsets.add(Pair(b.south().west().down().south(), true))
                        blockOffsets.add(Pair(b.south().west().down().west(), true))
                        blockOffsets.add(Pair(b.south().west().down().south().west(), true))
                        blockOffsets.add(Pair(b.south().west().down().south().south(), true))
                        blockOffsets.add(Pair(b.south().west().down().west().west(), true))
                        blockOffsets.add(Pair(b.south().west().down().west().west().north(), true))
                        blockOffsets.add(Pair(b.south().west().down().south().south().east(), true))
                        blockOffsets.add(Pair(b.south().west().down().west().west().north().west(), true))
                        blockOffsets.add(Pair(b.south().west().down().south().south().east().south(), true))
                        blockOffsets.add(Pair(b.south().west().west().west().north().west(), true))
                        blockOffsets.add(Pair(b.south().west().south().south().east().south(), true))
                        blockOffsets.add(Pair(b.south().west().south(), false))
                        blockOffsets.add(Pair(b.south().west().south().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().west(), false))
                        blockOffsets.add(Pair(b.south().west().west().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().west(), false))
                        blockOffsets.add(Pair(b.south().west().south().west().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().west().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().south(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().west(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().east(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().east().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().east().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().north(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().north().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().north().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().east().south().up(), false))
                        blockOffsets.add(Pair(b.south().west().south().south().east().south().up().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().north().west().up(), false))
                        blockOffsets.add(Pair(b.south().west().west().west().north().west().up().up(), false))
                    }
                    6 -> { //WEST
                        blockOffsets.add(Pair(b.down().west(), true))
                        blockOffsets.add(Pair(b.down().west().west(), true))
                        blockOffsets.add(Pair(b.down().west().west().south(), true))
                        blockOffsets.add(Pair(b.down().west().west().north(), true))
                        blockOffsets.add(Pair(b.down().west().west().south().south(), true))
                        blockOffsets.add(Pair(b.down().west().west().north().north(), true))
                        blockOffsets.add(Pair(b.down().west().west().south().south().south(), true))
                        blockOffsets.add(Pair(b.down().west().west().north().north().north(), true))
                        blockOffsets.add(Pair(b.west().west().south().south().south(), true))
                        blockOffsets.add(Pair(b.west().west().north().north().north(), true))
                        blockOffsets.add(Pair(b.west().west(), false))
                        blockOffsets.add(Pair(b.west().west().south(), false))
                        blockOffsets.add(Pair(b.west().west().north(), false))
                        blockOffsets.add(Pair(b.west().west().south().south(), false))
                        blockOffsets.add(Pair(b.west().west().north().north(), false))
                        blockOffsets.add(Pair(b.up().west().west(), false))
                        blockOffsets.add(Pair(b.up().west().west().south(), false))
                        blockOffsets.add(Pair(b.up().west().west().north(), false))
                        blockOffsets.add(Pair(b.up().west().west().south().south(), false))
                        blockOffsets.add(Pair(b.up().west().west().north().north(), false))
                        blockOffsets.add(Pair(b.up().west().west().south().south().south(), false))
                        blockOffsets.add(Pair(b.up().west().west().north().north().north(), false))
                        blockOffsets.add(Pair(b.up().up().west().west(), false))
                        blockOffsets.add(Pair(b.up().up().west().west().south(), false))
                        blockOffsets.add(Pair(b.up().up().west().west().north(), false))
                        blockOffsets.add(Pair(b.up().up().west().west().south().south(), false))
                        blockOffsets.add(Pair(b.up().up().west().west().north().north(), false))
                        blockOffsets.add(Pair(b.up().up().west().west().south().south().south(), false))
                        blockOffsets.add(Pair(b.up().up().west().west().north().north().north(), false))
                    }
                    7 -> { //NORTH-WEST
                        blockOffsets.add(Pair(b.west().north().down(), true))
                        blockOffsets.add(Pair(b.west().north().down().west(), true))
                        blockOffsets.add(Pair(b.west().north().down().north(), true))
                        blockOffsets.add(Pair(b.west().north().down().west().north(), true))
                        blockOffsets.add(Pair(b.west().north().down().west().west(), true))
                        blockOffsets.add(Pair(b.west().north().down().north().north(), true))
                        blockOffsets.add(Pair(b.west().north().down().north().north().east(), true))
                        blockOffsets.add(Pair(b.west().north().down().west().west().south(), true))
                        blockOffsets.add(Pair(b.west().north().down().north().north().east().north(), true))
                        blockOffsets.add(Pair(b.west().north().down().west().west().south().west(), true))
                        blockOffsets.add(Pair(b.west().north().north().north().east().north(), true))
                        blockOffsets.add(Pair(b.west().north().west().west().south().west(), true))
                        blockOffsets.add(Pair(b.west().north().west(), false))
                        blockOffsets.add(Pair(b.west().north().west().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().north(), false))
                        blockOffsets.add(Pair(b.west().north().north().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().north(), false))
                        blockOffsets.add(Pair(b.west().north().west().north().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().north().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().west(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().north(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().south(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().south().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().south().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().east(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().east().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().east().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().south().west().up(), false))
                        blockOffsets.add(Pair(b.west().north().west().west().south().west().up().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().east().north().up(), false))
                        blockOffsets.add(Pair(b.west().north().north().north().east().north().up().up(), false))
                    }
                }
            }
            Mode.FLAT -> {
                blockOffsets.add(Pair((b.down().north()), true))
                blockOffsets.add(Pair((b.down().east()), true))
                blockOffsets.add(Pair((b.down().south()), true))
                blockOffsets.add(Pair((b.down().west()), true))
                blockOffsets.add(Pair((b.down().north().east()), true))
                blockOffsets.add(Pair((b.down().north().west()), true))
                blockOffsets.add(Pair((b.down().south().east()), true))
                blockOffsets.add(Pair((b.down().south().west()), true))
            }
            null -> {
                disable()
            }
        }
    }

    fun isDone(): Boolean { return blockQueue.size == 0 }
    private fun doneQueueReset() { doneQueue.clear() }

    override fun onWorldRender(event: RenderEvent) {
        if (mc.player == null) return
        val renderer = ESPRenderer()
        renderer.aFilled = if (filled.value) aFilled.value else 0
        renderer.aOutline = if (outline.value) aOutline.value else 0
        updateRenderer(renderer)
        renderer.render(true)
    }

    fun getPlayerDirection(): Int {
        val yaw = (mc.player.rotationYaw % 360 + 360) % 360
        return if (yaw >= 158 && yaw < 203) { 0 } //NORTH
        else if (yaw >= 203 && yaw < 258) { 1 } //NORTH-EAST
        else if (yaw >= 258 && yaw < 293) { 2 } //EAST
        else if (yaw >= 293 && yaw < 338) { 3 } //SOUTH-EAST
        else if (yaw >= 338 || yaw < 23) { 4 } //SOUTH
        else if (yaw >= 23 && yaw < 68) { 5 } //SOUTH-WEST
        else if (yaw >= 68 && yaw < 113) { 6 } //WEST
        else { 7 } //NORTH-WEST
    }
}

class BlockTask(private val bp: BlockPos, private var tt: TaskState, private val bb: Boolean) {
    fun getBlockPos(): BlockPos { return bp }
    fun getTaskState(): TaskState { return tt }
    fun setTaskState(tts: TaskState) { tt = tts }
    fun getBlock(): Boolean { return bb }
}

enum class TaskState(val color: ColourHolder) {
    BREAK(ColourHolder(222, 0, 0)),
    BREAKING(ColourHolder(240, 222, 60)),
    BROKE(ColourHolder(240, 77, 60)),
    PLACE(ColourHolder(35, 188, 254)),
    PLACED(ColourHolder(53, 222, 66)),
    DONE(ColourHolder(50, 50, 50))
}

enum class Mode {
    FLAT, HIGHWAY
}
