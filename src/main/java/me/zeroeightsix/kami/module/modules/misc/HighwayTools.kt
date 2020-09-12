package me.zeroeightsix.kami.module.modules.misc

import baritone.api.BaritoneAPI
import me.zeroeightsix.kami.KamiMod
import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.ModuleManager
import me.zeroeightsix.kami.module.modules.player.LagNotifier
import me.zeroeightsix.kami.module.modules.player.NoBreakAnimation
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.BlockUtils
import me.zeroeightsix.kami.util.InventoryUtils
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.ESPRenderer
import me.zeroeightsix.kami.util.graphics.GeometryMasks
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.block.Block
import net.minecraft.block.Block.getBlockById
import net.minecraft.block.Block.getIdFromBlock
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.ResourceLocation
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.registry.ForgeRegistries
import java.util.*
import java.util.stream.IntStream.range
import kotlin.math.floor
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
    private val page = register(Settings.e<Page>("Page", Page.MAIN))
    //Settings for module
    val baritoneMode = register(Settings.booleanBuilder("Baritone").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val blocksPerTick = register(Settings.integerBuilder("Blocks Per Tick").withMinimum(1).withValue(1).withMaximum(9).withVisibility { page.value == Page.MAIN }.build())
    private val tickDelay = register(Settings.integerBuilder("Tick-Delay Place").withMinimum(0).withValue(0).withMaximum(10).withVisibility { page.value == Page.MAIN }.build())
    private val tickDelayBreak = register(Settings.integerBuilder("Tick-Delay Break").withMinimum(0).withValue(0).withMaximum(10).withVisibility { page.value == Page.MAIN }.build())
    private val noViewReset = register(Settings.booleanBuilder("NoViewReset").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val rotate = register(Settings.booleanBuilder("Rotate").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val stats = register(Settings.booleanBuilder("ShowStats").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val filled = register(Settings.booleanBuilder("Filled").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val outline = register(Settings.booleanBuilder("Outline").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val aFilled = register(Settings.integerBuilder("Filled Alpha").withMinimum(0).withValue(31).withMaximum(255).withVisibility { filled.value && page.value == Page.MAIN }.build())
    private val aOutline = register(Settings.integerBuilder("Outline Alpha").withMinimum(0).withValue(127).withMaximum(255).withVisibility { outline.value && page.value == Page.MAIN }.build())

    //Custom build settings
    val clearSpace = register(Settings.booleanBuilder("ClearSpace").withValue(true).withVisibility { page.value == Page.BUILD }.build())
    var clearHeight = register(Settings.integerBuilder("ClearHeight").withMinimum(1).withValue(4).withMaximum(6).withVisibility { page.value == Page.BUILD && clearSpace.value }.build())
    private var buildWidth = register(Settings.integerBuilder("BuildWidth").withMinimum(1).withValue(7).withMaximum(9).withVisibility { page.value == Page.BUILD }.build())
    private val rims = register(Settings.booleanBuilder("Rims").withValue(true).withVisibility { page.value == Page.BUILD }.build())
    private var rimHeight = register(Settings.integerBuilder("RimHeight").withMinimum(1).withValue(1).withMaximum(clearHeight.value).withVisibility { rims.value && page.value == Page.BUILD }.build())
    private val cornerBlock = register(Settings.booleanBuilder("CornerBlock").withValue(true).withVisibility { page.value == Page.BUILD }.build())

    var ignoreBlocks = mutableListOf(ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "standing_sign")),
            ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "wall_sign")),
            ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "standing_banner")),
            ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "wall_banner")),
            ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "bedrock")),
            ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "portal")))
    var material: Block = ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "obsidian"))!!
    var fillerMat: Block = ForgeRegistries.BLOCKS.getValue(ResourceLocation("minecraft", "netherrack"))!!
    private var playerHotbarSlot = -1
    private var lastHotbarSlot = -1
    private var isSneaking = false
    var pathing = true
    private var stuckDetector = 0
    private var buildDirectionSaved = 0
    private var buildDirectionCoordinate = 0
    private val directions = listOf("North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West")

    //Stats
    private var totalBlocksPlaced = 0
    var totalBlocksDestroyed = 0
    private var totalBlocksDistance = 0

    //val blockQueue = PriorityQueue<BlockTask>(TaskComparator())
    var blockQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private val doneQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private var blockOffsets = mutableListOf<Pair<BlockPos, Boolean>>()
    private var waitTicks = 0
    private var blocksPlaced = 0
    private lateinit var currentBlockPos: BlockPos
    private lateinit var startingBlockPos: BlockPos

    fun isDone(): Boolean { return blockQueue.size == 0 }

    override fun onEnable() {
        if (mc.player == null) {
            disable()
            return
        }
        buildDirectionSaved = getPlayerDirection()
        startingBlockPos = BlockPos(floor(mc.player.posX).toInt(), floor(mc.player.posY).toInt(), floor(mc.player.posZ).toInt())
        currentBlockPos = startingBlockPos
        playerHotbarSlot = mc.player.inventory.currentItem
        lastHotbarSlot = -1

        playerHotbarSlot = mc.player.inventory.currentItem
        buildDirectionCoordinate = if (buildDirectionSaved == 0 || buildDirectionSaved == 4) { startingBlockPos.getX() }
        else if (buildDirectionSaved == 2 || buildDirectionSaved == 6) { startingBlockPos.getZ() }
        else { 0 }

        blockQueue.clear()
        doneQueue.clear()
        updateTasks()
        printEnable()
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
        printDisable()
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0
        totalBlocksDistance = 0
    }

    override fun onUpdate() {
        if (mc.playerController == null) return

        if (!isDone()) {
            if (!BaritoneAPI.getProvider().primaryBaritone.pathingBehavior.isPathing) {
                pathing = false
            }
            if (!doTask()) {
                if (!pathing && !ModuleManager.getModuleT(LagNotifier::class.java)!!.paused) {
                    stuckDetector += 1
                    blockQueue = LinkedList<BlockTask>(blockQueue.shuffled())
                    if (stuckDetector > 20) {
                        doneQueue.clear()
                        blockQueue.clear()
                        updateTasks()
                    }
                } else {
                    doneQueue.clear()
                    blockQueue.clear()
                    updateTasks()
                }
            } else {
                stuckDetector = 0
            }
        } else {
            if (checkTasks()) {
                currentBlockPos = getNextBlock()
                totalBlocksDistance++
                doneQueue.clear()
                updateTasks()
                pathing = true
                if (!noViewReset.value) lookInWalkDirection()
            } else {
                doneQueue.clear()
                updateTasks()
            }
        }
        //printDebug()
    }

    private fun addTask(blockPos: BlockPos, taskState: TaskState, material: Block, prio: Int) {
        blockQueue.add(BlockTask(blockPos, taskState, material, prio))
    }

    private fun checkTasks(): Boolean {
        for (bt in doneQueue) {
            val block = mc.world.getBlockState(bt.getBlockPos()).block
            var cont = false
            for (b in ignoreBlocks) {
                if (b!!::class == block::class) { cont = true }
            }
            if (cont) { continue }
            if (bt.getBlock()::class == material::class && block is BlockAir) {
                return false
            } else if (bt.getBlock()::class == BlockAir::class && block !is BlockAir) {
                return false
            }
        }
        return true
    }

    private fun doTask(): Boolean {
        BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.registerProcess(KamiMod.highwayToolsProcess)
        if (!isDone() && !pathing && !ModuleManager.getModuleT(LagNotifier::class.java)!!.paused) {
            if (waitTicks == 0) {
                val blockAction = blockQueue.peek()
                if (blockAction.getTaskState() == TaskState.BREAK) {
                    val block = mc.world.getBlockState(blockAction.getBlockPos()).block
                    for (b in ignoreBlocks) {
                        if (block::class == b!!::class) {
                            blockAction.setTaskState(TaskState.DONE)
                            blockAction.setPriority(4)
                            doTask()
                        }
                    }
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
                                for ((pos, _) in blockOffsets) {
                                    if (neighbour == pos) {
                                        insideBuild = true
                                    }
                                }
                                if (insideBuild) {
                                    addTask(neighbour, TaskState.PLACE, fillerMat, 1)
                                } else {
                                    addTask(neighbour, TaskState.PLACE, material, 1)
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
                            blockAction.setPriority(3)
                            doTask()
                        }
                        else -> {
                            mineBlock(blockAction.getBlockPos(), true)
                            blockAction.setTaskState(TaskState.BREAKING)
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
                        if (blockAction.getBlock()::class == material::class) {
                            blockAction.setTaskState(TaskState.PLACE)
                            blockAction.setPriority(3)
                        } else {
                            blockAction.setTaskState(TaskState.DONE)
                            blockAction.setPriority(4)
                        }
                        doTask()
                    } else {
                        blockAction.setTaskState(TaskState.BREAK)
                    }
                } else if (blockAction.getTaskState() == TaskState.PLACE) {
                    val block = mc.world.getBlockState(blockAction.getBlockPos()).block
                    if (blockAction.getBlock() is BlockAir && block !is BlockLiquid) {
                        blockQueue.poll()
                        return true
                    }
                    if (block is BlockLiquid) {
                        blockAction.setBlock(fillerMat)
                    }
                    if (placeBlock(blockAction.getBlockPos(), blockAction.getBlock())) {
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
                    if (blockAction.getBlock()::class == material::class) {
                        val block = mc.world.getBlockState(blockAction.getBlockPos()).block
                        if (block !is BlockAir) {
                            blockAction.setTaskState(TaskState.DONE)
                            blockAction.setPriority(4)
                        } else {
                            blockAction.setTaskState(TaskState.PLACE)
                        }
                    } else {
                        blockAction.setTaskState(TaskState.BREAK)
                        blockAction.setPriority(2)
                    }
                    doTask()
                } else if (blockAction.getTaskState() == TaskState.DONE) {
                    blockQueue.poll()
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
            if (!b && block in ignoreBlocks) { addTask(a, TaskState.DONE, getBlockById(0), 4) }
            else if (b && block is BlockAir) { addTask(a, TaskState.PLACE, material, 3) }
            else if (b && block !is BlockAir && block::class != material::class) { addTask(a, TaskState.BREAK, material, 2) }
            else if (!b && block !is BlockAir) { addTask(a, TaskState.BREAK, getBlockById(0), 2) }
            else if (b && block::class == material::class) { addTask(a, TaskState.DONE, material, 4) }
            else if (!b && block is BlockAir) { addTask(a, TaskState.DONE, getBlockById(0), 4) }
        }
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

    private fun placeBlock(pos: BlockPos, mat: Block): Boolean
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

        //Swap to material in Hotbar or get from inventory
        if (InventoryUtils.getSlotsHotbar(getIdFromBlock(mat)) == null && InventoryUtils.getSlotsNoHotbar(getIdFromBlock(mat)) != null) {
            //InventoryUtils.moveToHotbar(getIdFromBlock(mat), 130, (tickDelay.value * 16).toLong())
            for (x in InventoryUtils.getSlotsNoHotbar(getIdFromBlock(mat))!!) {
                InventoryUtils.quickMoveSlot(x, (tickDelay.value * 16).toLong())
            }
            //InventoryUtils.quickMoveSlot(1, (tickDelay.value * 16).toLong())
        } else if (InventoryUtils.getSlots(0, 35, getIdFromBlock(mat)) == null) {
            MessageSendHelper.sendChatMessage("$chatName No ${mat.localizedName} was found in inventory")
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            return false
        }
        InventoryUtils.swapSlotToItem(getIdFromBlock(mat))

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

        val noBreakAnimation = ModuleManager.getModuleT(NoBreakAnimation::class.java)!!
        if (noBreakAnimation.isEnabled) {
            noBreakAnimation.resetMining()
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
        val vec3d = Vec3d(pos).add(0.5, 0.0, 0.5)
        val lookAt = RotationUtils.getRotationTo(vec3d, true)
        mc.player.rotationYaw = lookAt.x.toFloat()
        mc.player.rotationPitch = lookAt.y.toFloat()
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

    private fun updateRenderer(renderer: ESPRenderer): ESPRenderer {
        val side = GeometryMasks.Quad.ALL
        for (bt in blockQueue) {
            if (bt.getTaskState() != TaskState.DONE) { renderer.add(bt.getBlockPos(), bt.getTaskState().color, side) }
        }
        for (bt in doneQueue) {
            if (bt.getBlock()::class != BlockAir::class) { renderer.add(bt.getBlockPos(), bt.getTaskState().color, side) }
        }
        return renderer
    }

    override fun onWorldRender(event: RenderEvent) {
        if (mc.player == null) return
        val renderer = ESPRenderer()
        renderer.aFilled = if (filled.value) aFilled.value else 0
        renderer.aOutline = if (outline.value) aOutline.value else 0
        updateRenderer(renderer)
        renderer.render(true)
    }

    private fun printDebug() {
        MessageSendHelper.sendChatMessage("")
        MessageSendHelper.sendChatMessage("")
        MessageSendHelper.sendChatMessage("-------------------- QUEUE -------------------")
        for (bt in blockQueue) {
            MessageSendHelper.sendChatMessage(bt.getBlock().localizedName + "@(" + bt.getBlockPos().asString() + ") State: " + bt.getTaskState().toString() + " Prio: " + bt.getPriority())
        }
        MessageSendHelper.sendChatMessage("")
        MessageSendHelper.sendChatMessage("-------------------- DONE --------------------")
        for (bt in doneQueue) {
            MessageSendHelper.sendChatMessage(bt.getBlock().localizedName + "@(" + bt.getBlockPos().asString() + ") State: " + bt.getTaskState().toString() + " Prio: " + bt.getPriority())
        }
    }

    fun printSettings() {
        var message = "$chatName Settings" +
                "\n    §9> §rMaterial: §7${material.localizedName}" +
                "\n    §9> §rBaritone: §7${baritoneMode.value}" +
                "\n    §9> §rIgnored Blocks:"
        for (b in ignoreBlocks) {
            message += "\n        §9> §7${b!!.localizedName}"
        }
        MessageSendHelper.sendChatMessage(message)
    }

    private fun printEnable() {
        if (stats.value) {
            if (buildDirectionSaved == 1 || buildDirectionSaved == 3 || buildDirectionSaved == 5 || buildDirectionSaved == 5 || buildDirectionSaved == 7) {
                MessageSendHelper.sendChatMessage("$chatName Module started." +
                        "\n    §9> §7Direction: §a" + directions[buildDirectionSaved] + "§r" +
                        "\n    §9> §7Coordinates: §a" + mc.player.positionVector.x.roundToInt() + ", " + mc.player.positionVector.z.roundToInt() + "§r" +
                        "\n    §9> §7Baritone mode: §a" + baritoneMode.value + "§r")
            } else {
                MessageSendHelper.sendChatMessage("$chatName Module started." +
                        "\n    §9> §7Direction: §a" + directions[buildDirectionSaved] + "§r" +
                        "\n    §9> §7Coordinate: §a" + buildDirectionCoordinate + "§r" +
                        "\n    §9> §7Baritone mode: §a" + baritoneMode.value + "§r")
            }
        } else {
            MessageSendHelper.sendChatMessage("$chatName Module started.")
        }
    }

    private fun printDisable() {
        if (stats.value) {
            MessageSendHelper.sendChatMessage("$chatName Module stopped." +
                    "\n    §9> §7Placed blocks: §a" + totalBlocksPlaced + "§r" +
                    "\n    §9> §7Destroyed blocks: §a" + totalBlocksDestroyed + "§r" +
                    "\n    §9> §7Distance: §a" + totalBlocksDistance + "§r")
        } else {
            MessageSendHelper.sendChatMessage("$chatName Module stopped.")
        }
    }

    fun getNextBlock(): BlockPos {
        return when (buildDirectionSaved) {
            0 -> { currentBlockPos.north() }
            1 -> { currentBlockPos.north().east() }
            2 -> { currentBlockPos.east() }
            3 -> { currentBlockPos.south().east() }
            4 -> { currentBlockPos.south() }
            5 -> { currentBlockPos.south().west() }
            6 -> { currentBlockPos.west() }
            else -> { currentBlockPos.north().west() }
        }
    }

    private fun lookInWalkDirection() {
        // set head rotation to get max walking speed
        when (buildDirectionSaved) {
            0 -> { mc.player.rotationYaw = -180F }
            1 -> { mc.player.rotationYaw = -135F }
            2 -> { mc.player.rotationYaw = -90F }
            3 -> { mc.player.rotationYaw = -45F }
            4 -> { mc.player.rotationYaw = 0F }
            5 -> { mc.player.rotationYaw = 45F }
            6 -> { mc.player.rotationYaw = 90F }
            else -> { mc.player.rotationYaw = 135F }
        }
        mc.player.rotationPitch = 0F
    }

    private fun relativeDirection(curs: BlockPos, steps: Int, turn: Int): BlockPos {
        var c = curs
        var d = (buildDirectionSaved + turn).rem(8)
        if (d < 0) d += 8
        when (d) {
            0 -> c = c.north(steps)
            1 -> c = c.north(steps).east(steps)
            2 -> c = c.east(steps)
            3 -> c = c.south(steps).east(steps)
            4 -> c = c.south(steps)
            5 -> c = c.south(steps).west(steps)
            6 -> c = c.west(steps)
            7 -> c = c.north(steps).west(steps)
        }
        return c
    }

    private fun isDiagonal(): Boolean {
        return when(buildDirectionSaved) {
            1 -> true
            3 -> true
            5 -> true
            7 -> true
            else -> false
        }
    }

    private fun updateBlockArray() {
        blockOffsets.clear()
        val b = currentBlockPos

        when(mode.value) {
            Mode.HIGHWAY -> {
                var cursor = b
                cursor = cursor.down()

                cursor = relativeDirection(cursor, 1, 0)
                blockOffsets.add(Pair(cursor, true))
                cursor = relativeDirection(cursor, 1, 0)

                var flip = false
                for (x in range(1, buildWidth.value + 1)) {
                    val alterDirection = if (flip) { -1 } else { 1 }
                    if (isDiagonal()) {
                        if (x != 1) {
                            blockOffsets.add(Pair(relativeDirection(relativeDirection(cursor, x / 2 - 1, 1 * alterDirection), x / 2, 3 * alterDirection), true))
                        }
                        blockOffsets.add(Pair(relativeDirection(cursor, x / 2, 2 * alterDirection), true))
                        if (rims.value && x == buildWidth.value) {
                            var c = cursor
                            for (y in range(1, rimHeight.value + 1)) {
                                c = c.up()
                                if (buildWidth.value % 2 != 0) {
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), true))
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection * (-1)), true))
                                } else {
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), true))
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2 - 1, 2 * alterDirection * (-1)), true))
                                }
                            }
                        }
                        if (clearSpace.value) {
                            var c = cursor
                            for (y in range(1, clearHeight.value)) {
                                c = c.up()
                                if (rims.value) {
                                    if (!((x == buildWidth.value || x == buildWidth.value - 1) && y <= rimHeight.value)) {
                                        blockOffsets.add(Pair(relativeDirection(relativeDirection(c, x / 2 - 1, 1 * alterDirection), x / 2, 3 * alterDirection), false))
                                        blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), false))
                                    } else {
                                        blockOffsets.add(Pair(relativeDirection(relativeDirection(c, x / 2 - 1, 1 * alterDirection), x / 2, 3 * alterDirection), false))
                                    }
                                } else {
                                    if (x != 1) {
                                        blockOffsets.add(Pair(relativeDirection(relativeDirection(c, x / 2 - 1, 1 * alterDirection), x / 2, 3 * alterDirection), false))
                                    }
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), false))
                                }
                            }
                        }
                    } else {
                        if (cornerBlock.value) {
                            blockOffsets.add(Pair(relativeDirection(cursor, x / 2, 2 * alterDirection), true))
                        } else {
                            if (!(x == buildWidth.value || x == buildWidth.value - 1)) {
                                blockOffsets.add(Pair(relativeDirection(cursor, x / 2, 2 * alterDirection), true))
                            }
                        }
                        if (rims.value && x == buildWidth.value) {
                            var c = cursor
                            for (y in range(1, rimHeight.value + 1)) {
                                c = c.up()
                                if (buildWidth.value % 2 != 0) {
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), true))
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection * (-1)), true))
                                } else {
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), true))
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2 - 1, 2 * alterDirection * (-1)), true))
                                }
                            }
                        }
                        if (clearSpace.value) {
                            var c = cursor
                            for (y in range(1, clearHeight.value)) {
                                c = c.up()
                                if (rims.value) {
                                    if (!((x == buildWidth.value || x == buildWidth.value - 1) && y <= rimHeight.value)) {
                                        blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), false))
                                    }
                                } else {
                                    blockOffsets.add(Pair(relativeDirection(c, x / 2, 2 * alterDirection), false))
                                }
                            }
                        }
                    }
                    flip = !flip
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

    private enum class Mode {
        FLAT, HIGHWAY
    }

    private enum class Page {
        MAIN, BUILD
    }

    class BlockTask(private val bp: BlockPos, private var tt: TaskState, private var bb: Block, private var prio: Int) {
        fun getBlockPos(): BlockPos { return bp }
        fun getTaskState(): TaskState { return tt }
        fun setTaskState(tts: TaskState) { tt = tts }
        fun getBlock(): Block { return bb }
        fun setBlock(b: Block) { bb = b }
        fun getPriority(): Int { return prio }
        fun setPriority(p: Int) { prio = p }
    }

    private class TaskComparator: Comparator<BlockTask>{
        override fun compare(o1: BlockTask?, o2: BlockTask?): Int {
            if (o1 == null || o2 == null) {
                return 0
            }
            return o1.getPriority().compareTo(o2.getPriority())
        }
    }

    enum class TaskState(val color: ColorHolder) {
        BREAK(ColorHolder(222, 0, 0)),
        BREAKING(ColorHolder(240, 222, 60)),
        BROKE(ColorHolder(240, 77, 60)),
        PLACE(ColorHolder(35, 188, 254)),
        PLACED(ColorHolder(53, 222, 66)),
        DONE(ColorHolder(50, 50, 50))
    }
}

