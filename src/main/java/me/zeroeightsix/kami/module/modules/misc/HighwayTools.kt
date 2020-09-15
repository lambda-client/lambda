package me.zeroeightsix.kami.module.modules.misc

import baritone.api.BaritoneAPI
import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.ModuleManager
import me.zeroeightsix.kami.module.modules.player.LagNotifier
import me.zeroeightsix.kami.module.modules.player.NoBreakAnimation
import me.zeroeightsix.kami.process.HighwayToolsProcess
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.BlockUtils
import me.zeroeightsix.kami.util.InventoryUtils
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.ESPRenderer
import me.zeroeightsix.kami.util.graphics.GeometryMasks
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString
import me.zeroeightsix.kami.util.math.MathUtils.Cardinal
import me.zeroeightsix.kami.util.math.MathUtils.getPlayerCardinal
import me.zeroeightsix.kami.util.math.MathUtils.mcPlayerPosFloored
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.math.VectorUtils
import me.zeroeightsix.kami.util.text.MessageSendHelper
import net.minecraft.block.Block
import net.minecraft.block.Block.getBlockById
import net.minecraft.block.Block.getIdFromBlock
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.entity.item.EntityItem
import net.minecraft.entity.item.EntityXPOrb
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.*
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.util.*
import java.util.stream.IntStream.range

/**
 * @author Avanatiker
 * @since 20/08/2020
 */

@Module.Info(
        name = "HighwayTools",
        description = "Be the grief a step a head.",
        category = Module.Category.MISC
)
class HighwayTools : Module() {

    private val mode = register(Settings.e<Mode>("Mode", Mode.HIGHWAY))
    private val debugMessages = register(Settings.e<DebugMessages>("DebugMessages", DebugMessages.IMPORTANT))
    private val page = register(Settings.e<Page>("Page", Page.MAIN))

    // settings for module
    val baritoneMode = register(Settings.booleanBuilder("Baritone").withValue(true).withVisibility { false }.build())
    private val blocksPerTick = register(Settings.integerBuilder("BlocksPerTick").withMinimum(1).withValue(1).withMaximum(9).withVisibility { page.value == Page.MAIN }.build())
    private val tickDelay = register(Settings.integerBuilder("TickDelayPlace").withMinimum(0).withValue(0).withMaximum(10).withVisibility { page.value == Page.MAIN }.build())
    private val tickDelayBreak = register(Settings.integerBuilder("TickDelayBreak").withMinimum(0).withValue(0).withMaximum(10).withVisibility { page.value == Page.MAIN }.build())
    private val noViewReset = register(Settings.booleanBuilder("NoViewReset").withValue(false).withVisibility { page.value == Page.MAIN }.build())
    private val spoofRotations = register(Settings.booleanBuilder("SpoofRotations").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val spoofHotbar = register(Settings.booleanBuilder("SpoofRotations").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val info = register(Settings.booleanBuilder("ShowInfo").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val printDebug = register(Settings.booleanBuilder("ShowDebug").withValue(false).withVisibility { page.value == Page.MAIN }.build())
    private val placeAnimation = register(Settings.booleanBuilder("PlaceAnimation").withValue(false).withVisibility { page.value == Page.MAIN }.build())
    private val filled = register(Settings.booleanBuilder("Filled").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val outline = register(Settings.booleanBuilder("Outline").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val aFilled = register(Settings.integerBuilder("FilledAlpha").withMinimum(0).withValue(31).withMaximum(255).withVisibility { filled.value && page.value == Page.MAIN }.build())
    private val aOutline = register(Settings.integerBuilder("OutlineAlpha").withMinimum(0).withValue(127).withMaximum(255).withVisibility { outline.value && page.value == Page.MAIN }.build())

    // custom build settings
    val clearSpace = register(Settings.booleanBuilder("ClearSpace").withValue(true).withVisibility { page.value == Page.BUILD }.build())
    var clearHeight = register(Settings.integerBuilder("ClearHeight").withMinimum(1).withValue(4).withMaximum(6).withVisibility { page.value == Page.BUILD && clearSpace.value }.build())
    private var buildWidth = register(Settings.integerBuilder("BuildWidth").withMinimum(1).withValue(7).withMaximum(9).withVisibility { page.value == Page.BUILD }.build())
    private val rims = register(Settings.booleanBuilder("Rims").withValue(true).withVisibility { page.value == Page.BUILD }.build())
    private var rimHeight = register(Settings.integerBuilder("RimHeight").withMinimum(1).withValue(1).withMaximum(clearHeight.value).withVisibility { rims.value && page.value == Page.BUILD }.build())
    private val cornerBlock = register(Settings.booleanBuilder("CornerBlock").withValue(true).withVisibility { page.value == Page.BUILD }.build())

    var ignoreBlocks = mutableListOf(Blocks.STANDING_SIGN, Blocks.WALL_SIGN, Blocks.STANDING_BANNER, Blocks.WALL_BANNER, Blocks.BEDROCK, Blocks.PORTAL)
    var material: Block = Blocks.OBSIDIAN
    var fillerMat: Block = Blocks.NETHERRACK
    private var playerHotbarSlot = -1
    private var lastHotbarSlot = -1
    private var isSneaking = false
    var pathing = true
    private var stuckDetector = 0
    private lateinit var buildDirectionSaved: Cardinal

    // stats
    private var totalBlocksPlaced = 0
    private var totalBlocksDestroyed = 0
    private var totalBlocksDistance = 0

    var blockQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private val doneQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private var blockOffsets = mutableListOf<Pair<BlockPos, Boolean>>()
    private var waitTicks = 0
    private var blocksPlaced = 0
    private lateinit var currentBlockPos: BlockPos
    private lateinit var startingBlockPos: BlockPos

    private enum class DebugMessages {
        NONE, IMPORTANT, ALL
    }

    fun isDone(): Boolean {
        return blockQueue.size == 0
    }

    override fun onEnable() {
        if (mc.player == null) {
            disable()
            return
        }
        startingBlockPos = mcPlayerPosFloored(mc)
        currentBlockPos = startingBlockPos
        playerHotbarSlot = mc.player.inventory.currentItem
        lastHotbarSlot = -1
        buildDirectionSaved = getPlayerCardinal(mc)

        playerHotbarSlot = mc.player.inventory.currentItem

        refreshData()
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


        val baritoneProcess = BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.mostRecentInControl()
        if (baritoneProcess.isPresent && baritoneProcess.get() == HighwayToolsProcess) {
            baritoneProcess.get().onLostControl()
        }
        printDisable()
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0
        totalBlocksDistance = 0
    }

    override fun onUpdate() {
        if (mc.playerController == null) return
        pathing = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior.isPathing

        if (!isDone()) {
            if (!doTask()) {
                if (!pathing && !ModuleManager.getModuleT(LagNotifier::class.java)!!.paused && !ModuleManager.getModuleT(AutoObsidian::class.java)!!.active) {
                    stuckDetector += 1
                    blockQueue = LinkedList<BlockTask>(blockQueue.shuffled())
                    if (debugMessages.value == DebugMessages.ALL) {
                        MessageSendHelper.sendChatMessage("$chatName Shuffled tasks")
                    }
                    if (stuckDetector > 20) {
                        refreshData()
                        if (debugMessages.value == DebugMessages.IMPORTANT) {
                            MessageSendHelper.sendChatMessage("$chatName You got stuck, retry")
                        }
                    }
                } else {
                    refreshData()
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
                if (!noViewReset.value) lookInWalkDirection()
            } else {
                doneQueue.clear()
                updateTasks()
            }
        }
        if (printDebug.value) {
            printDebug()
        }
    }

    private fun addTask(blockPos: BlockPos, taskState: TaskState, material: Block, priority: Int) {
        blockQueue.add(BlockTask(blockPos, taskState, material, priority))
    }

    private fun checkTasks(): Boolean {
        for (blockTask in doneQueue) {
            val block = mc.world.getBlockState(blockTask.blockPos).block
            var cont = false
            for (b in ignoreBlocks) {
                if (b!!::class == block::class) {
                    cont = true
                }
            }
            if (cont) {
                continue
            }
            if (blockTask.block::class == material::class && block is BlockAir) {
                return false
            } else if (blockTask.block::class == BlockAir::class && block !is BlockAir) {
                return false
            }
        }
        return true
    }

    private fun doTask(): Boolean {
        if (!isDone() && !pathing && !ModuleManager.getModuleT(LagNotifier::class.java)!!.paused && !ModuleManager.getModuleT(AutoObsidian::class.java)!!.active) {
            BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.registerProcess(HighwayToolsProcess)

            if (waitTicks == 0) {
                val blockAction = blockQueue.peek()

                when (blockAction.taskState) {
                    TaskState.BREAK -> {
                        val block = mc.world.getBlockState(blockAction.blockPos).block

                        for (b in ignoreBlocks) {
                            if (block::class == b!!::class) {
                                blockAction.taskState = TaskState.DONE
                                blockAction.priority = 4
                                doTask()
                            }
                        }

                        for (side in EnumFacing.values()) {
                            val neighbour = blockAction.blockPos.offset(side)
                            var found = false

                            if (mc.world.getBlockState(neighbour).block is BlockLiquid) {

                                for (bt in blockQueue) {
                                    if (bt.blockPos == neighbour) {
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
                                blockAction.taskState = TaskState.BROKE
                                doTask()
                            }
                            is BlockLiquid -> {
                                blockAction.taskState = TaskState.PLACE
                                blockAction.priority = 3
                                doTask()
                            }
                            else -> {
                                mineBlock(blockAction.blockPos, true)
                                blockAction.taskState = TaskState.BREAKING
                            }
                        }
                    }
                    TaskState.BREAKING -> {
                        mineBlock(blockAction.blockPos, false)
                        blockAction.taskState = TaskState.BROKE
                    }
                    TaskState.BROKE -> {
                        val block = mc.world.getBlockState(blockAction.blockPos).block

                        if (block is BlockAir) {
                            totalBlocksDestroyed++
                            waitTicks = tickDelayBreak.value

                            if (blockAction.block::class == material::class) {
                                blockAction.taskState = TaskState.PLACE
                                blockAction.priority = 3
                            } else {
                                blockAction.taskState = TaskState.DONE
                                blockAction.priority = 4
                            }

                            doTask()
                        } else {
                            blockAction.taskState = TaskState.BREAK
                        }
                    }
                    TaskState.PLACE -> {
                        val block = mc.world.getBlockState(blockAction.blockPos).block

                        if (blockAction.block is BlockAir && block !is BlockLiquid) {
                            blockQueue.poll()
                            return true
                        }

                        if (block is BlockLiquid) {
                            blockAction.block = fillerMat
                        }

                        if (placeBlock(blockAction.blockPos, blockAction.block)) {
                            blockAction.taskState = TaskState.PLACED
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
                    }
                    TaskState.PLACED -> {
                        if (blockAction.block::class == material::class) {
                            val block = mc.world.getBlockState(blockAction.blockPos).block

                            if (block !is BlockAir) {
                                blockAction.taskState = TaskState.DONE
                                blockAction.priority = 4
                            } else {
                                blockAction.taskState = TaskState.PLACE
                            }
                        } else {
                            blockAction.taskState = TaskState.BREAK
                            blockAction.priority = 2
                        }
                        doTask()
                    }
                    TaskState.DONE -> {
                        blockQueue.poll()
                        doneQueue.add(blockAction)
                        doTask()
                    }
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
            when {
                (!b && block in ignoreBlocks) -> addTask(a, TaskState.DONE, getBlockById(0), 4)
                (b && block is BlockAir) -> addTask(a, TaskState.PLACE, material, 3)
                (b && block !is BlockAir && block::class != material::class) -> addTask(a, TaskState.BREAK, material, 2)
                (!b && block !is BlockAir) -> addTask(a, TaskState.BREAK, getBlockById(0), 2)
                (b && block::class == material::class) -> addTask(a, TaskState.DONE, material, 4)
                (!b && block is BlockAir) -> addTask(a, TaskState.DONE, getBlockById(0), 4)
            }
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
        if (spoofRotations.value) {
            BlockUtils.faceVectorPacketInstant(hitVec)
        }
        //PlayerPacketManager.addPacket(this, PlayerPacketManager.PlayerPacket(rotating = true, rotation = Vec2f(mc.player.rotationYaw, mc.player.rotationPitch)))
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

    private fun updateRenderer(renderer: ESPRenderer): ESPRenderer {
        val side = GeometryMasks.Quad.ALL
        for (blockTask in blockQueue) {
            if (blockTask.taskState != TaskState.DONE) {
                renderer.add(blockTask.blockPos, blockTask.taskState.color, side)
            }
        }
        for (blockTask in doneQueue) {
            if (blockTask.block::class != BlockAir::class) {
                renderer.add(blockTask.blockPos, blockTask.taskState.color, side)
            }
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
        var message = "\n\n"
        message += "-------------------- QUEUE -------------------"
        for (blockTask in blockQueue) {
            message += "\n" + blockTask.block.localizedName + "@(" + blockTask.blockPos.asString() + ") Priority: " + blockTask.priority + " State: " + blockTask.taskState.toString()
        }
        message += "\n-------------------- DONE --------------------"
        for (blockTask in doneQueue) {
            message += "\n" + blockTask.block.localizedName + "@(" + blockTask.blockPos.asString() + ") Priority: " + blockTask.priority + " State: " + blockTask.taskState.toString()
        }
        MessageSendHelper.sendChatMessage(message)
    }

    fun printSettings() {
        var message = "$chatName Settings" +
                "\n    §9> §rMaterial: §7${material.localizedName}" +
                "\n    §9> §rBaritone: §7${baritoneMode.value}" +
                "\n    §9> §rIgnored Blocks:"
        for (b in ignoreBlocks) {
            message += "\n        §9> §7${b!!.registryName}"
        }
        MessageSendHelper.sendChatMessage(message)
    }

    private fun printEnable() {
        var message = ""
        if (info.value) {
            message += "$chatName Module started." +
                    "\n    §9> §7Direction: §a" + buildDirectionSaved.cardinalName + "§r"

            message += if (buildDirectionSaved.isDiagonal) {
                "\n    §9> §7Coordinates: §a" + startingBlockPos.x + ", " + startingBlockPos.z + "§r"
            } else {
                if (buildDirectionSaved == Cardinal.NEG_Z || buildDirectionSaved == Cardinal.POS_Z) {
                    "\n    §9> §7Coordinate: §a" + startingBlockPos.z + "§r"
                } else {
                    "\n    §9> §7Coordinate: §a" + startingBlockPos.x + "§r"
                }
            }
        } else {
            message += "$chatName Module started."
        }
        MessageSendHelper.sendChatMessage(message)
    }

    private fun printDisable() {
        var message = ""
        message += if (info.value) {
            "$chatName Module stopped." +
                    "\n    §9> §7Placed blocks: §a" + totalBlocksPlaced + "§r" +
                    "\n    §9> §7Destroyed blocks: §a" + totalBlocksDestroyed + "§r" +
                    "\n    §9> §7Distance: §a" + totalBlocksDistance + "§r"
        } else {
            "$chatName Module stopped."
        }
        MessageSendHelper.sendChatMessage(message)
    }

    fun getNextBlock(): BlockPos {
        return when (buildDirectionSaved) {
            Cardinal.NEG_Z -> currentBlockPos.north()
            Cardinal.POS_X_NEG_Z -> currentBlockPos.north().east()
            Cardinal.POS_X -> currentBlockPos.east()
            Cardinal.POS_X_POS_Z -> currentBlockPos.south().east()
            Cardinal.POS_Z -> currentBlockPos.south()
            Cardinal.NEG_X_POS_Z -> currentBlockPos.south().west()
            Cardinal.NEG_X -> currentBlockPos.west()
            else -> currentBlockPos.north().west()
        }
    }

    private fun lookInWalkDirection() {
        // set head rotation to get max walking speed
        when (buildDirectionSaved) {
            Cardinal.NEG_Z -> mc.player.rotationYaw = -180F
            Cardinal.POS_X_NEG_Z -> mc.player.rotationYaw = -135F
            Cardinal.POS_X -> mc.player.rotationYaw = -90F
            Cardinal.POS_X_POS_Z -> mc.player.rotationYaw = -45F
            Cardinal.POS_Z -> mc.player.rotationYaw = 0F
            Cardinal.NEG_X_POS_Z -> mc.player.rotationYaw = 45F
            Cardinal.NEG_X -> mc.player.rotationYaw = 90F
            else -> mc.player.rotationYaw = 135F
        }
        mc.player.rotationPitch = 0F
    }

    private fun relativeDirection(curs: BlockPos, steps: Int, turn: Int): BlockPos {
        var c = curs
        var d = (buildDirectionSaved.ordinal + turn).rem(8)
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

    private fun refreshData() {
        doneQueue.clear()
        blockQueue.clear()
        updateTasks()
    }

    private fun updateBlockArray() {
        blockOffsets.clear()
        val b = currentBlockPos

        when (mode.value) {
            Mode.HIGHWAY -> {
                var cursor = b
                cursor = cursor.down()

                cursor = relativeDirection(cursor, 1, 0)
                blockOffsets.add(Pair(cursor, true))
                cursor = relativeDirection(cursor, 1, 0)
                var flip = false

                for (x in range(1, buildWidth.value + 1)) {
                    val alterDirection = if (flip) {
                        -1
                    } else {
                        1
                    }

                    if (buildDirectionSaved.isDiagonal) {
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
                for (bp in VectorUtils.getBlockPositionsInArea(b.down().north(buildWidth.value).west(buildWidth.value), b.down().south(buildWidth.value).east(buildWidth.value))) {
                    blockOffsets.add(Pair(bp, true))
                }
            }
            null -> {
                MessageSendHelper.sendChatMessage("Module logic is a lie.")
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

    data class BlockTask(val blockPos: BlockPos, var taskState: TaskState, var block: Block, var priority: Int)

    enum class TaskState(val color: ColorHolder) {
        BREAK(ColorHolder(222, 0, 0)),
        BREAKING(ColorHolder(240, 222, 60)),
        BROKE(ColorHolder(240, 77, 60)),
        PLACE(ColorHolder(35, 188, 254)),
        PLACED(ColorHolder(53, 222, 66)),
        DONE(ColorHolder(50, 50, 50))
    }
}

