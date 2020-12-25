package me.zeroeightsix.kami.module.modules.misc

import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.event.events.SafeTickEvent
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.player.AutoEat
import me.zeroeightsix.kami.module.modules.player.InventoryManager
import me.zeroeightsix.kami.module.modules.player.NoBreakAnimation
import me.zeroeightsix.kami.process.HighwayToolsProcess
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.BaritoneUtils
import me.zeroeightsix.kami.util.BlockUtils
import me.zeroeightsix.kami.util.InventoryUtils
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.ESPRenderer
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString
import me.zeroeightsix.kami.util.math.Direction
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.math.Vec2d
import me.zeroeightsix.kami.util.math.VectorUtils.getBlockPositionsInArea
import me.zeroeightsix.kami.util.math.VectorUtils.getDistance
import me.zeroeightsix.kami.util.math.VectorUtils.multiply
import me.zeroeightsix.kami.util.math.VectorUtils.toBlockPos
import me.zeroeightsix.kami.util.math.VectorUtils.toVec3d
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import net.minecraft.block.Block
import net.minecraft.block.Block.getIdFromBlock
import net.minecraft.block.Block.registerBlocks
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketPlayer
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.*
import net.minecraftforge.client.event.ColorHandlerEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.event.listener.listener
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * @author Avanatiker
 * @since 20/08/2020
 */

@Module.Info(
        name = "HighwayTools",
        description = "Be the grief a step a head.",
        category = Module.Category.MISC,
        modulePriority = 10
)
object HighwayTools : Module() {

    private val mode = register(Settings.e<Mode>("Mode", Mode.HIGHWAY))
    private val page = register(Settings.e<Page>("Page", Page.BUILD))

    // build settings
    private val clearSpace = register(Settings.booleanBuilder("ClearSpace").withValue(true).withVisibility { page.value == Page.BUILD && mode.value == Mode.HIGHWAY })
    private val clearHeight = register(Settings.integerBuilder("Height").withValue(4).withRange(1, 6).withStep(1).withVisibility { page.value == Page.BUILD && clearSpace.value })
    private val buildWidth = register(Settings.integerBuilder("Width").withValue(5).withRange(1, 9).withStep(1).withVisibility { page.value == Page.BUILD })
    private val railing = register(Settings.booleanBuilder("Railing").withValue(true).withVisibility { page.value == Page.BUILD && mode.value == Mode.HIGHWAY })
    private val railingHeight = register(Settings.integerBuilder("RailingHeight").withValue(1).withRange(0, 4).withStep(1).withMaximum(clearHeight.value).withVisibility { railing.value && page.value == Page.BUILD && mode.value == Mode.HIGHWAY })
    private val cornerBlock = register(Settings.booleanBuilder("CornerBlock").withValue(false).withVisibility { page.value == Page.BUILD && (mode.value == Mode.HIGHWAY || mode.value == Mode.TUNNEL) })

    // behavior settings
    val baritoneMode = register(Settings.booleanBuilder("AutoMode").withValue(true).withVisibility { page.value == Page.BEHAVIOR })
    private val blocksPerTick = register(Settings.integerBuilder("BlocksPerTick").withValue(1).withRange(1, 10).withStep(1).withVisibility { page.value == Page.BEHAVIOR })
    private val tickDelayPlace = register(Settings.integerBuilder("TickDelayPlace").withValue(3).withRange(0, 16).withStep(1).withVisibility { page.value == Page.BEHAVIOR })
    private val tickDelayBreak = register(Settings.integerBuilder("TickDelayBreak").withValue(1).withRange(0, 16).withStep(1).withVisibility { page.value == Page.BEHAVIOR })
    private val interacting = register(Settings.enumBuilder(InteractMode::class.java, "InteractMode").withValue(InteractMode.SPOOF).withVisibility { page.value == Page.BEHAVIOR })
    private val illegalPlacements = register(Settings.booleanBuilder("IllegalPlacements").withValue(false).withVisibility { page.value == Page.BEHAVIOR })
    private val maxReach = register(Settings.floatBuilder("MaxReach").withValue(4.5F).withRange(1.0f, 6.0f).withStep(0.1f).withVisibility { page.value == Page.BEHAVIOR })
    private val toggleInventoryManager = register(Settings.booleanBuilder("ToggleInvManager").withValue(true).withVisibility { page.value == Page.BEHAVIOR })
    private val toggleAutoObsidian = register(Settings.booleanBuilder("ToggleAutoObsidian").withValue(true).withVisibility { page.value == Page.BEHAVIOR })

    // config
    private val info = register(Settings.booleanBuilder("ShowInfo").withValue(true).withVisibility { page.value == Page.CONFIG })
    private val printDebug = register(Settings.booleanBuilder("ShowQueue").withValue(false).withVisibility { page.value == Page.CONFIG })
    private val debugMessages = register(Settings.enumBuilder(DebugMessages::class.java, "Debug").withValue(DebugMessages.IMPORTANT).withVisibility { page.value == Page.CONFIG })
    private val goalRender = register(Settings.booleanBuilder("GoalRender").withValue(false).withVisibility { page.value == Page.CONFIG })
    private val filled = register(Settings.booleanBuilder("Filled").withValue(true).withVisibility { page.value == Page.CONFIG })
    private val outline = register(Settings.booleanBuilder("Outline").withValue(true).withVisibility { page.value == Page.CONFIG })
    private val aFilled = register(Settings.integerBuilder("FilledAlpha").withValue(26).withRange(0, 255).withStep(1).withVisibility { filled.value && page.value == Page.CONFIG })
    private val aOutline = register(Settings.integerBuilder("OutlineAlpha").withValue(91).withRange(0, 255).withStep(1).withVisibility { outline.value && page.value == Page.CONFIG })

    // internal settings
    val ignoreBlocks = hashSetOf(
            Blocks.STANDING_SIGN,
            Blocks.STANDING_BANNER,
            Blocks.WALL_BANNER,
            Blocks.WALL_SIGN,
            Blocks.BEDROCK,
            Blocks.END_PORTAL,
            Blocks.END_PORTAL_FRAME,
            Blocks.PORTAL
    )
    var material: Block = Blocks.OBSIDIAN
    var fillerMat: Block = Blocks.NETHERRACK
    private var playerHotbarSlot = -1
    private var lastHotbarSlot = -1
    private var buildDirectionSaved = Direction.NORTH
    private var baritoneSettingAllowPlace = false
    private var baritoneSettingRenderGoal = false

    // runtime vars
    val pendingTasks = PriorityQueue(BlockTaskComparator)
    private val doneTasks = ArrayList<BlockTask>()
    private val blueprint = ArrayList<Pair<BlockPos, Block>>()
    private var waitTicks = 0
    private var blocksPlaced = 0
    var pathing = false
    private var currentBlockPos = BlockPos(0, -1, 0)
    private var startingBlockPos = BlockPos(0, -1, 0)
    private val stuckManager = StuckManagement(StuckLevel.NONE, 0)
    private val renderer = ESPRenderer()
    private var active = false

    // stats
    private var totalBlocksPlaced = 0
    private var totalBlocksDestroyed = 0
    private var startTime = 0L
    private var prevFood = 0
    private var foodLoss = 1
    private var materialLeft = 0
    private var fillerMatLeft = 0

    override fun onEnable() {
        if (mc.player == null) {
            disable()
            return
        }

        /* Turn on inventory manager if the users wants us to control it */
        if(toggleInventoryManager.value && InventoryManager.isDisabled) InventoryManager.enable()

        /* Turn on Auto Obsidian if the user wants us to control it. */
        if(toggleAutoObsidian.value && AutoObsidian.isDisabled) {
            /* If we have no obsidian, immediately turn on Auto Obsidian */
            if(InventoryUtils.countItemAll(49) == 0) {
                AutoObsidian.enable()
            }
            else {
                Thread {
                    /* Wait 1 second because turning both on simultaneously is buggy */
                    Thread.sleep(1000L)
                    AutoObsidian.enable()
                }.start()
            }
        }

        startingBlockPos = mc.player.positionVector.toBlockPos()
        currentBlockPos = startingBlockPos
        playerHotbarSlot = mc.player.inventory.currentItem
        lastHotbarSlot = -1
        buildDirectionSaved = Direction.fromEntity(mc.player)
        startTime = System.currentTimeMillis()
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0

        if (baritoneMode.value) {
            baritoneSettingAllowPlace = BaritoneUtils.settings?.allowPlace?.value ?: true
            BaritoneUtils.settings?.allowPlace?.value = false

            if (!goalRender.value) {
                baritoneSettingRenderGoal = BaritoneUtils.settings?.renderGoal?.value ?: true
                BaritoneUtils.settings?.renderGoal?.value = false
            }
        }

        playerHotbarSlot = mc.player.inventory.currentItem

        refreshData()
        printEnable()
    }

    override fun onDisable() {
        if (mc.player == null) return

        active = false

        // load initial player hand
        if (lastHotbarSlot != playerHotbarSlot && playerHotbarSlot != -1) {
            mc.player.inventory.currentItem = playerHotbarSlot
        }
        playerHotbarSlot = -1
        lastHotbarSlot = -1

        if (baritoneMode.value) {
            BaritoneUtils.settings?.allowPlace?.value = baritoneSettingAllowPlace
            if (!goalRender.value) BaritoneUtils.settings?.renderGoal?.value = baritoneSettingRenderGoal
            val process = BaritoneUtils.primary?.pathingControlManager?.mostRecentInControl()

            if (process != null && process.isPresent && process.get() == HighwayToolsProcess) process.get().onLostControl()
        }

        /* Turn off inventory manager if the users wants us to control it */
        if(toggleInventoryManager.value && InventoryManager.isEnabled) {
            InventoryManager.disable()
        }

        /* Turn off auto obsidian if the user wants us to control it */
        if(toggleAutoObsidian.value && AutoObsidian.isEnabled) {
            AutoObsidian.disable()
        }

        printDisable()
    }

    fun isDone(): Boolean {
        return pendingTasks.size == 0
    }

    init {
        listener<SafeTickEvent> {
            if (it.phase != TickEvent.Phase.END) {
                if (mc.playerController == null) return@listener

                updateRenderer()

                if (!active) {
                    active = true
                    BaritoneUtils.primary?.pathingControlManager?.registerProcess(HighwayToolsProcess)
                }

                if (baritoneMode.value) {
                    pathing = BaritoneUtils.isPathing
                    var taskDistance = BlockPos(0, -1, 0)
                    (pendingTasks.firstOrNull() ?: doneTasks.firstOrNull())?.let { element ->
                        taskDistance = element.blockPos
                    }
                    if (getDistance(mc.player.positionVector, taskDistance.toVec3d()) < maxReach.value ) {
                        if (!isDone()) {
                            if(canDoTask()) {
                                if (!pathing) adjustPlayerPosition(false)
                                val currentFood = mc.player.foodStats.foodLevel
                                if (currentFood != prevFood) {
                                    if (currentFood < prevFood) foodLoss++
                                    prevFood = currentFood
                                }
                                doTask()
                            }
                        } else {
                            if (checkTasks() && !pathing) {
                                currentBlockPos = getNextBlock(getNextBlock())
                                doneTasks.clear()
                                updateTasks(currentBlockPos)
                            } else {
                                refreshData()
                            }
                        }
                    } else {
                        refreshData()
                    }
                } else {
                    if (currentBlockPos == mc.player.positionVector.toBlockPos()) {
                        doTask()
                    } else {
                        currentBlockPos = mc.player.positionVector.toBlockPos()
                        if (abs((buildDirectionSaved.ordinal - Direction.fromEntity(mc.player).ordinal) % 8) == 4) buildDirectionSaved = Direction.fromEntity(mc.player)
                        refreshData()
                    }
                }
            } else {
                return@listener
            }
        }

        listener<RenderWorldEvent> {
            if (mc.player == null) return@listener
            renderer.render(false)
        }
    }

    private fun updateRenderer() {
        renderer.clear()
        renderer.aFilled = if (filled.value) aFilled.value else 0
        renderer.aOutline = if (outline.value) aOutline.value else 0
//        renderer.add(currentBlockPos, ColorHolder(255, 255, 255))
//        renderer.add(getNextWalkableBlock(), ColorHolder(0, 0, 0))
        for (blockTask in pendingTasks) {
            if (blockTask.taskState == TaskState.DONE) continue
            renderer.add(mc.world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(mc.world, blockTask.blockPos), blockTask.taskState.color)
        }
        for (blockTask in doneTasks) {
            if (blockTask.block == Blocks.AIR) continue
            renderer.add(mc.world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(mc.world, blockTask.blockPos), blockTask.taskState.color)
        }
    }

    private fun addTask(blockPos: BlockPos, taskState: TaskState, material: Block) {
        pendingTasks.add(BlockTask(blockPos, taskState, material))
    }

    private fun addTask(blockPos: BlockPos, material: Block) {
        doneTasks.add(BlockTask(blockPos, TaskState.DONE, material))
    }

    private fun updateTask(blockTask: BlockTask, taskState: TaskState) {
        pendingTasks.poll()
        blockTask.taskState = taskState
        if (taskState == TaskState.DONE) {
            doneTasks.add(blockTask)
        } else {
            pendingTasks.add(blockTask)
        }
    }

    private fun updateTask(blockTask: BlockTask, material: Block) {
        pendingTasks.poll()
        blockTask.block = material
        doneTasks.add(blockTask)
    }

    /* Returns true if we can do a task, else returns false */
    private fun canDoTask(): Boolean {
        return !AutoObsidian.isActive() && !AutoEat.eating
    }

    private fun doTask() {
        if (!isDone() && canDoTask()) {
            if (waitTicks == 0) {
                val blockTask = pendingTasks.peek()

                when (blockTask.taskState) {
                    TaskState.DONE -> doDone(blockTask)
                    TaskState.BREAKING -> if(!doBreaking(blockTask)) {
                        stuckManager.increase(blockTask)
                        return
                    }
                    TaskState.BROKEN -> doBroken(blockTask)
                    TaskState.PLACED -> doPlaced(blockTask)
                    TaskState.EMERGENCY_BREAK -> if(!doBreak(blockTask)) {
                        stuckManager.increase(blockTask)
                        return
                    }
                    TaskState.BREAK -> if(!doBreak(blockTask)) {
                        stuckManager.increase(blockTask)
                        return
                    }
                    TaskState.PLACE, TaskState.LIQUID_SOURCE, TaskState.LIQUID_FLOW -> if(!doPlace(blockTask)) {
                        stuckManager.increase(blockTask)
                        return
                    }
                }

                if (blockTask.taskState != TaskState.BREAKING) stuckManager.reset()
            } else {
                waitTicks--
            }
        }
    }

    private fun doDone(blockTask: BlockTask) {
        pendingTasks.poll()
        doneTasks.add(blockTask)
        doTask()
    }

    private fun doBreaking(blockTask: BlockTask): Boolean {
        when (mc.world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                totalBlocksDestroyed++
                waitTicks = tickDelayBreak.value
                if (blockTask.block == material || blockTask.block == fillerMat) {
                    updateTask(blockTask, TaskState.PLACE)
                } else {
                    updateTask(blockTask, TaskState.DONE)
                    doTask()
                }
            }
            is BlockLiquid -> {
                var filler = fillerMat
                if (isInsideBuild(blockTask.blockPos) || fillerMatLeft == 0) filler = material
                if (mc.world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                    updateTask(blockTask, TaskState.LIQUID_FLOW)
                    updateTask(blockTask, filler)
                } else {
                    updateTask(blockTask, TaskState.LIQUID_SOURCE)
                    updateTask(blockTask, filler)
                }
            }
            else -> {
                mineBlock(blockTask)
            }
        }
        return true
    }

    private fun doBroken(blockTask: BlockTask) {
        when (mc.world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                totalBlocksDestroyed++
                if (blockTask.block == material || blockTask.block == fillerMat) {
                    updateTask(blockTask, TaskState.PLACE)
                } else {
                    updateTask(blockTask, TaskState.DONE)
                }
            }
            else -> {
                updateTask(blockTask, TaskState.BREAK)
            }
        }
        doTask()
    }

    private fun doPlaced(blockTask: BlockTask) {
        val block = mc.world.getBlockState(blockTask.blockPos).block

        when {
            blockTask.block == block && block != Blocks.AIR -> updateTask(blockTask, TaskState.DONE)
            blockTask.block == Blocks.AIR && block != Blocks.AIR -> updateTask(blockTask, TaskState.BREAK)
            blockTask.block == block && block == Blocks.AIR -> updateTask(blockTask, TaskState.BREAK)
            else -> updateTask(blockTask, TaskState.PLACE)
        }
        doTask()
    }

    private fun doBreak(blockTask: BlockTask): Boolean {

        // ignore blocks
        if (blockTask.taskState != TaskState.EMERGENCY_BREAK) {
            if (blockTask.block != Blocks.AIR && ignoreBlocks.contains(blockTask.block)) {
                updateTask(blockTask, TaskState.DONE)
                doTask()
            }
        }

        // last check before breaking
        when (mc.world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                if (blockTask.block == Blocks.AIR) {
                    updateTask(blockTask, TaskState.DONE)
                } else {
                    updateTask(blockTask, TaskState.PLACE)
                }
                doTask()
            }
            is BlockLiquid -> {
                var filler = fillerMat
                if (isInsideBuild(blockTask.blockPos) || fillerMatLeft == 0) filler = material
                if (mc.world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                    updateTask(blockTask, TaskState.LIQUID_FLOW)
                    updateTask(blockTask, filler)
                } else {
                    updateTask(blockTask, TaskState.LIQUID_SOURCE)
                    updateTask(blockTask, filler)
                }
            }
            else -> {
                // liquid search around the breaking block
                if (blockTask.taskState != TaskState.EMERGENCY_BREAK) {
                    if (liquidHandler(blockTask)) {
                        updateTask(blockTask, TaskState.EMERGENCY_BREAK)
                        return true
                    }
                }
                if (!inventoryProcessor(blockTask)) return false
                mineBlock(blockTask)
            }
        }
        return true
    }

    private fun doPlace(blockTask: BlockTask): Boolean {
        val block = mc.world.getBlockState(blockTask.blockPos).block

        when {
            block == material && block == blockTask.block -> updateTask(blockTask, TaskState.PLACED)
            block == fillerMat && block == blockTask.block -> updateTask(blockTask, TaskState.PLACED)
            else -> {
                if (!BlockUtils.isPlaceable(blockTask.blockPos)) {
//                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("Error: " + blockTask.blockPos + " is not a valid position to place a block, removing task.")
//                    blockQueue.remove(blockTask)
                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("Invalid place position: " + blockTask.blockPos)
                    refreshData()
                    return false
                }

                if (!inventoryProcessor(blockTask)) return false
                if (!placeBlock(blockTask)) return false
                if (blockTask.taskState != TaskState.PLACE && isInsideSelection(blockTask.blockPos)) updateTask(blockTask, Blocks.AIR)
                updateTask(blockTask, TaskState.PLACED)
                if (blocksPerTick.value > blocksPlaced + 1) {
                    blocksPlaced++
                    doTask()
                } else {
                    blocksPlaced = 0
                }

                waitTicks = tickDelayPlace.value
                totalBlocksPlaced++
            }
        }
        return true
    }


    private fun checkTasks(): Boolean {
        for (blockTask in doneTasks) {
            val block = mc.world.getBlockState(blockTask.blockPos).block
            if (ignoreBlocks.contains(block)) continue
            when {
                blockTask.block == material && block != material -> return false
                mode.value == Mode.TUNNEL && blockTask.block == fillerMat && block != fillerMat -> return false
                blockTask.block == Blocks.AIR && block != Blocks.AIR -> return false
            }
        }
        return true
    }

    private fun updateTasks(originPos: BlockPos) {
        blueprint.clear()
        updateBlockArray(originPos)
        updateBlockArray(getNextBlock(originPos))
        for ((blockPos, blockType) in blueprint) {
            val isReplaceable = mc.world.getBlockState(blockPos).material.isReplaceable
            if (blockPos == mc.player.positionVector.toBlockPos().down()) continue
            when (val block = mc.world.getBlockState(blockPos).block) {
                is BlockLiquid -> {
                    var filler = fillerMat
                    if (isInsideBuild(blockPos) || fillerMatLeft == 0) filler = material
                    when (mc.world.getBlockState(blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                        true -> addTask(blockPos, TaskState.LIQUID_FLOW, filler)
                        false -> addTask(blockPos, TaskState.LIQUID_SOURCE, filler)
                    }
                }
                else -> {
                    when (blockType) {
                        Blocks.AIR -> {
                            when {
                                block in ignoreBlocks -> addTask(blockPos, Blocks.AIR)
                                block == Blocks.AIR -> addTask(blockPos, Blocks.AIR)
                                block == Blocks.FIRE -> addTask(blockPos, TaskState.BREAK, Blocks.FIRE)
                                block != Blocks.AIR -> addTask(blockPos, TaskState.BREAK, Blocks.AIR)
                            }
                        }
                        material -> {
                            when {
                                block == material -> addTask(blockPos, material)
                                !isReplaceable && block != material -> addTask(blockPos, TaskState.BREAK, material)
                                isReplaceable -> addTask(blockPos, TaskState.PLACE, material)
                            }
                        }
                        fillerMat -> {
                            if (mode.value == Mode.HIGHWAY) {
                                if (buildDirectionSaved.isDiagonal) {
                                    val blockUp = mc.world.getBlockState(blockPos.up()).block
                                    when {
                                        getPlaceableSide(blockPos.up()) == null && blockUp != material -> addTask(blockPos, TaskState.PLACE, fillerMat)
                                        getPlaceableSide(blockPos.up()) != null -> addTask(blockPos, fillerMat)
                                    }
                                }
                            } else {
                                when {
                                    block == fillerMat -> addTask(blockPos, fillerMat)
                                    !isReplaceable && block != fillerMat -> addTask(blockPos, TaskState.BREAK, fillerMat)
                                    isReplaceable -> addTask(blockPos, TaskState.PLACE, fillerMat)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shuffleTasks() {
        val shuffled = pendingTasks.shuffled()
        pendingTasks.clear()
        pendingTasks.addAll(shuffled)
    }

    private fun inventoryProcessor(blockTask: BlockTask): Boolean {
        when (blockTask.taskState) {
            TaskState.BREAK, TaskState.EMERGENCY_BREAK -> {
                val noHotbar = InventoryUtils.getSlotsNoHotbar(278)
                if (InventoryUtils.getSlotsHotbar(278) == null && noHotbar != null) {
//                    InventoryUtils.moveToHotbar(278, 130)
                    InventoryUtils.moveToSlot(noHotbar[0], 36)
                } else if (InventoryUtils.getSlots(0, 35, 278) == null) {
                    sendChatMessage("$chatName No Pickaxe was found in inventory")
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    disable()
                    return false
                }
                InventoryUtils.swapSlotToItem(278)
            }
            TaskState.PLACE, TaskState.LIQUID_FLOW, TaskState.LIQUID_SOURCE -> {
                val blockID = getIdFromBlock(blockTask.block)
                val noHotbar = InventoryUtils.getSlotsNoHotbar(blockID)
//                fillerMatLeft = InventoryUtils.countItemAll(getIdFromBlock(fillerMat))
//                if (fillerMatLeft > overburden.value) {
//                    for (x in InventoryUtils.getSlots(0, 35, blockID)!!) InventoryUtils.throwAllInSlot(x)
//                }
                if (InventoryUtils.getSlotsHotbar(blockID) == null &&
                        noHotbar != null) {
                    when (blockTask.block) {
                        fillerMat -> InventoryUtils.moveToSlot(noHotbar[0], 37)
                        material -> InventoryUtils.moveToSlot(noHotbar[0], 38)
                    }
//                    for (x in InventoryUtils.getSlotsNoHotbar(blockID)!!) {
//                        InventoryUtils.quickMoveSlot(x)
//                    }
                } else if (InventoryUtils.getSlots(0, 35, blockID) == null) {
                    sendChatMessage("$chatName No ${blockTask.block.localizedName} was found in inventory")
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    disable()
                    return false
                }
                InventoryUtils.swapSlotToItem(blockID)
            }
            else -> return false
        }
        return true
    }

    private fun liquidHandler(blockTask: BlockTask): Boolean {
        var foundLiquid = false
        for (side in EnumFacing.values()) {
            val neighbour = blockTask.blockPos.offset(side)
            val neighbourBlock = mc.world.getBlockState(neighbour).block
            if (neighbourBlock is BlockLiquid) {
                var flowing = false
                try {
                    flowing = mc.world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0
                } catch (e: Exception) {
                }
                if (sqrt(mc.player.getDistanceSqToCenter(neighbour)) > maxReach.value) continue
                foundLiquid = true
                val found = mutableListOf<Triple<BlockTask, TaskState, Block>>()
                var filler = fillerMat
                if (isInsideBuild(neighbour)) filler = material
                for (bt in pendingTasks) {
                    if (bt.blockPos == neighbour) {
                        when (flowing) {
                            false -> found.add(Triple(bt, TaskState.LIQUID_SOURCE, filler))
                            true -> found.add(Triple(bt, TaskState.LIQUID_FLOW, filler))
                        }
                    }
                }
                if (found.isEmpty()) {
                    when (flowing) {
                        false -> addTask(neighbour, TaskState.LIQUID_SOURCE, filler)
                        true -> addTask(neighbour, TaskState.LIQUID_FLOW, filler)
                    }
                } else {
                    for (x in found) {
                        updateTask(x.first, x.second)
                        updateTask(x.first, x.third)
                    }
                }
            }
        }
        return foundLiquid
    }

    private fun mineBlock(blockTask: BlockTask) {
        if (blockTask.blockPos == mc.player.positionVector.toBlockPos().down()) {
            updateTask(blockTask, TaskState.DONE)
            return
        }

        /* For fire, we just need to mine the top of the block below the fire */
        /* TODO: This will not work if the top of the block which the fire is on is not visible */
        if (blockTask.block == Blocks.FIRE) {
            val blockBelowFire = BlockPos(blockTask.blockPos.x, blockTask.blockPos.y - 1, blockTask.blockPos.z)
            mc.playerController.clickBlock(blockBelowFire, EnumFacing.UP)
            mc.player.swingArm(EnumHand.MAIN_HAND)
            updateTask(blockTask, TaskState.BREAKING)
            return
        }

        val directHits = mutableListOf<RayTraceResult>()
        val bb = mc.world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(mc.world, blockTask.blockPos)
        val playerEyeVec = mc.player.getPositionEyes(1f)

        for (side in EnumFacing.values()) {
            loop@ for (direction in EnumFacing.values()) {
                when (side) {
                    EnumFacing.UP -> if (direction == EnumFacing.DOWN || direction == EnumFacing.UP) continue@loop
                    EnumFacing.DOWN -> if (direction == EnumFacing.UP || direction == EnumFacing.DOWN) continue@loop
                    EnumFacing.NORTH -> if (direction == EnumFacing.SOUTH || direction == EnumFacing.NORTH) continue@loop
                    EnumFacing.EAST -> if (direction == EnumFacing.WEST || direction == EnumFacing.EAST) continue@loop
                    EnumFacing.SOUTH -> if (direction == EnumFacing.NORTH || direction == EnumFacing.SOUTH) continue@loop
                    EnumFacing.WEST -> if (direction == EnumFacing.EAST || direction == EnumFacing.WEST) continue@loop
                }
                val sideVec = bb.center.add(Vec3d(side.directionVec).scale(getAABBSide(bb, side) * 0.9).add(Vec3d(direction.directionVec)).scale(getAABBSide(bb, direction) - 0.001))
                if (playerEyeVec.distanceTo(sideVec) > maxReach.value) continue
                if (mc.world.getBlockState(blockTask.blockPos.offset(side)).block != Blocks.AIR) continue
                val rt = mc.world.rayTraceBlocks(playerEyeVec, sideVec, false) ?: continue
                if (rt.blockPos == blockTask.blockPos && rt.sideHit == side) directHits.add(rt)
            }
        }

        if (directHits.size == 0) {
            stuckManager.increase(blockTask)
            refreshData()
            if (stuckManager.stuckLevel == StuckLevel.NONE) doTask()
            return
        }

        var rayTrace: RayTraceResult? = null
        var shortestRT = 999.0
        for (rt in directHits) {
            val distance = playerEyeVec.distanceTo(rt.hitVec)
            if (distance < shortestRT) {
                shortestRT = distance
                rayTrace = rt
            }
        }

        if (rayTrace == null) return

        val facing = rayTrace.sideHit
        val rotation = RotationUtils.getRotationTo(rayTrace.hitVec, true)

        setRotation(rotation)

        when (mc.world.getBlockState(blockTask.blockPos).block) {
            Blocks.NETHERRACK -> {
                updateTask(blockTask, TaskState.BROKEN)
                waitTicks = tickDelayBreak.value
                Thread {
                    Thread.sleep(16L)
                    mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockTask.blockPos, facing))
                    Thread.sleep(16L)
                    mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockTask.blockPos, facing))
                    mc.player.swingArm(EnumHand.MAIN_HAND)
                }.start()
            }
            else -> dispatchGenericMineThread(blockTask, facing)
        }
    }

    /* Dispatches a thread to mine any non-netherrack blocks generically */
    private fun dispatchGenericMineThread(blockTask: BlockTask, facing: EnumFacing) {
        val digPacket: CPacketPlayerDigging = when (blockTask.taskState) {
            TaskState.BREAK, TaskState.EMERGENCY_BREAK -> CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockTask.blockPos, facing)
            TaskState.BREAKING -> CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockTask.blockPos, facing)
            else -> CPacketPlayerDigging()
        }
        if (blockTask.taskState == TaskState.BREAK || blockTask.taskState == TaskState.EMERGENCY_BREAK) updateTask(blockTask, TaskState.BREAKING)
        Thread {
            Thread.sleep(25L)
            mc.connection!!.sendPacket(digPacket)
            mc.player.swingArm(EnumHand.MAIN_HAND)
        }.start()
    }

    // Only temporary till we found solution to avoid untraceable blocks
    private fun placeBlockWall(blockTask: BlockTask): Boolean {
        val side = getPlaceableSide(blockTask.blockPos) ?: return false
        val neighbour = blockTask.blockPos.offset(side)
        val hitVec = Vec3d(neighbour).add(0.5, 0.5, 0.5).add(Vec3d(side.opposite.directionVec).scale(0.5))

        val rotation = RotationUtils.getRotationTo(hitVec, true)
        setRotation(rotation)

        Thread {
            Thread.sleep(25L)
            val placePacket = CPacketPlayerTryUseItemOnBlock(neighbour, side.opposite, EnumHand.MAIN_HAND, hitVec.x.toFloat(), hitVec.y.toFloat(), hitVec.z.toFloat())
            mc.connection!!.sendPacket(placePacket)
            mc.player.swingArm(EnumHand.MAIN_HAND)
            if (NoBreakAnimation.isEnabled) NoBreakAnimation.resetMining()
        }.start()
        return true
    }

    private fun placeBlock(blockTask: BlockTask): Boolean {
        val directHits = mutableListOf<RayTraceResult>()
        val emergencyHits = mutableListOf<RayTraceResult>()
        for (side in EnumFacing.values()) {
            val offPos = blockTask.blockPos.offset(side)
            if (mc.world.getBlockState(offPos).material.isReplaceable) continue
            if (mc.player.getPositionEyes(1f).distanceTo(Vec3d(offPos).add(BlockUtils.getHitVecOffset(side))) > maxReach.value) continue
            val rotationVector = Vec3d(offPos).add(0.5, 0.5, 0.5).add(Vec3d(side.opposite.directionVec).scale(0.499))
            val rt = mc.world.rayTraceBlocks(mc.player.getPositionEyes(1f), rotationVector, false)?: continue
            if (rt.typeOfHit != RayTraceResult.Type.BLOCK) continue
            if (rt.blockPos == offPos && offPos.offset(rt.sideHit) == blockTask.blockPos) {
                directHits.add(rt)
            } else {
                emergencyHits.add(rt)
            }
        }
        if (directHits.size == 0) {
            if (emergencyHits.size > 0 && stuckManager.stuckLevel.ordinal > 0 && (blockTask.taskState == TaskState.LIQUID_SOURCE || blockTask.taskState == TaskState.LIQUID_FLOW)) {
                var rayTrace = emergencyHits[0]
                var shortestRT = 99.0
                for (rt in emergencyHits) {
                    if (mc.player.getPositionEyes(1f).distanceTo(Vec3d(rt.blockPos).add(BlockUtils.getHitVecOffset(rt.sideHit))) < shortestRT) {
                        shortestRT = mc.player.getPositionEyes(1f).distanceTo(Vec3d(rt.blockPos).add(BlockUtils.getHitVecOffset(rt.sideHit)))
                        rayTrace = rt
                    }
                }
                addTask(rayTrace.blockPos, TaskState.EMERGENCY_BREAK, Blocks.AIR)
                return false
            }
            return if (illegalPlacements.value) {
                if(debugMessages.value == DebugMessages.ALL) sendChatMessage("Trying to place through wall ${blockTask.blockPos}")
                placeBlockWall(blockTask)
            } else {
                false
            }
        }

        var rayTrace: RayTraceResult? = null
        var shortestRT = 99.0
        for (rt in directHits) {
            if (mc.player.getPositionEyes(1f).distanceTo(Vec3d(rt.blockPos).add(BlockUtils.getHitVecOffset(rt.sideHit))) < shortestRT) {
                shortestRT = mc.player.getPositionEyes(1f).distanceTo(Vec3d(rt.blockPos).add(BlockUtils.getHitVecOffset(rt.sideHit)))
                rayTrace = rt
            }
        }
        if (rayTrace == null) {
            sendChatMessage("Can't find any vector?")
            return false
        }

        val hitVecOffset = rayTrace.hitVec
        val rotation = RotationUtils.getRotationTo(hitVecOffset, true)
        setRotation(rotation)

        Thread {
            Thread.sleep(25L)
            val placePacket = CPacketPlayerTryUseItemOnBlock(rayTrace.blockPos, rayTrace.sideHit, EnumHand.MAIN_HAND, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat())
            mc.connection!!.sendPacket(placePacket)
            mc.player.swingArm(EnumHand.MAIN_HAND)
            if (NoBreakAnimation.isEnabled) NoBreakAnimation.resetMining()
        }.start()
        return true
    }

    private fun setRotation(rotation: Vec2d) {
        when (interacting.value) {
            InteractMode.SPOOF -> {
                val rotationPacket = CPacketPlayer.PositionRotation(mc.player.posX, mc.player.posY, mc.player.posZ, rotation.x.toFloat(), rotation.y.toFloat(), mc.player.onGround)
                mc.connection!!.sendPacket(rotationPacket)
            }
            InteractMode.VIEWLOCK -> {
                mc.player.rotationYaw = rotation.x.toFloat()
                mc.player.rotationPitch = rotation.y.toFloat()
            }
            else -> {}
        }
    }

    private fun getPlaceableSide(pos: BlockPos): EnumFacing? {
        for (side in EnumFacing.values()) {
            val neighbour = pos.offset(side)
            if (!mc.world.getBlockState(neighbour).block.canCollideCheck(mc.world.getBlockState(neighbour), false)) continue
            val blockState = mc.world.getBlockState(neighbour)
            if (!blockState.material.isReplaceable) return side
        }
        return null
    }

    private fun isInsideSelection(blockPos: BlockPos): Boolean {
        return pendingTasks.any { it.blockPos == blockPos }
    }

    private fun isInsideBuild(blockPos: BlockPos): Boolean {
        return pendingTasks.any { it.blockPos == blockPos && it.block == material }
    }

    private fun adjustPlayerPosition(bridge: Boolean) {
        var vec = Vec3d(getNextWalkableBlock()).add(0.5, 0.5, 0.5).subtract(mc.player.positionVector)
        when {
            bridge && !buildDirectionSaved.isDiagonal -> vec = vec.add(Vec3d(buildDirectionSaved.directionVec).scale(0.51))
            bridge && buildDirectionSaved.isDiagonal -> vec = vec.add(Vec3d(buildDirectionSaved.directionVec).scale(0.525))
        }
        mc.player.motionX = MathHelper.clamp(vec.x / 2.0, -0.2, 0.2)
        mc.player.motionZ = MathHelper.clamp(vec.z / 2.0, -0.2, 0.2)
    }

    private fun getQueue(): List<String> {
        val message: MutableList<String> = mutableListOf()
        message.add("QUEUE:")
        for (blockTask in pendingTasks) message.add("    " + blockTask.block.localizedName + "@(" + blockTask.blockPos.asString() + ") Priority: " + blockTask.taskState.ordinal + " State: " + blockTask.taskState.toString())
        message.add("DONE:")
        for (blockTask in doneTasks) message.add("    " + blockTask.block.localizedName + "@(" + blockTask.blockPos.asString() + ") Priority: " + blockTask.taskState.ordinal + " State: " + blockTask.taskState.toString())
        return message
    }

    fun printSettings() {
        StringBuilder(ignoreBlocks.size + 1).run {
            append("$chatName Settings" +
                "\n    §9> §rMain material: §7${material.localizedName}" +
                "\n    §9> §rFiller material: §7${fillerMat.localizedName}" +
                "\n    §9> §rBaritone: §7${baritoneMode.value}" +
                "\n    §9> §rIgnored Blocks:")
            for (b in ignoreBlocks) append("\n        §9> §7${b!!.registryName}")

            sendChatMessage(toString())
        }
    }

    private fun printEnable() {
        if (info.value) {
            StringBuilder(2).run {
                append("$chatName Module started." +
                    "\n    §9> §7Direction: §a${buildDirectionSaved.displayName}§r")

                if (buildDirectionSaved.isDiagonal) {
                    append("\n    §9> §7Coordinates: §a${startingBlockPos.x} ${startingBlockPos.z}§r")
                } else {
                    if (buildDirectionSaved == Direction.NORTH || buildDirectionSaved == Direction.SOUTH) {
                        append("\n    §9> §7Coordinate: §a${startingBlockPos.x}§r")
                    } else {
                        append("\n    §9> §7Coordinate: §a${startingBlockPos.z}§r")
                    }
                }

                sendChatMessage(toString())
            }
        }
    }

    private fun printDisable() {
        if (info.value) {
            StringBuilder(2).run {
                append(
                    "$chatName Module stopped." +
                    "\n    §9> §7Placed blocks: §a$totalBlocksPlaced§r" +
                    "\n    §9> §7Destroyed blocks: §a$totalBlocksDestroyed§r"
                )

                if (baritoneMode.value) append("\n    §9> §7Distance: §a${getDistance(startingBlockPos.toVec3d(), currentBlockPos.toVec3d()).toInt()}§r")

                sendChatMessage(toString())
            }
        }
    }

    fun getBlueprintStats(): Pair<Int, Int> {
        var materialUsed = 0
        var fillerMatUsed = 0
        for ((_, b) in blueprint) {
            when (b) {
                material -> materialUsed++
                fillerMat -> fillerMatUsed++
            }
        }
        // TODO: Make it dynamic for several depth layers
        return Pair(materialUsed / 2, fillerMatUsed / 2)
    }

    fun gatherStatistics(): MutableList<String> {
        val currentTask: BlockTask? = if (isDone()) {
            null
        } else {
            pendingTasks.peek()
        }

        materialLeft = InventoryUtils.countItemAll(getIdFromBlock(material))
        fillerMatLeft = InventoryUtils.countItemAll(getIdFromBlock(fillerMat))
        val indirectMaterialLeft = 8 * InventoryUtils.countItemAll(130)

        val blueprintStats = getBlueprintStats()

        val pavingLeft = materialLeft / (blueprintStats.first + 1)
        val pavingLeftAll = (materialLeft + indirectMaterialLeft) / (blueprintStats.first + 1)

        val runtimeSec = ((System.currentTimeMillis() - startTime) / 1000) + 0.0001
        val seconds = (runtimeSec % 60).toInt().toString().padStart(2,'0')
        val minutes = ((runtimeSec % 3600) / 60).toInt().toString().padStart(2,'0')
        val hours = (runtimeSec / 3600).toInt().toString().padStart(2,'0')

        val distanceDone = getDistance(startingBlockPos.toVec3d(), currentBlockPos.toVec3d()).toInt()

        val secLeft = runtimeSec / (distanceDone * pavingLeftAll + 0.0001)
        val secondsLeft = (secLeft % 60).toInt().toString().padStart(2,'0')
        val minutesLeft = ((secLeft % 3600) / 60).toInt().toString().padStart(2,'0')
        val hoursLeft = (secLeft / 3600).toInt().toString().padStart(2,'0')

        val statistics = mutableListOf(
            "§rPerformance",
            "    §7Runtime: §9$hours:$minutes:$seconds",
            "    §7Placements per second: §9%.2f".format(totalBlocksPlaced / runtimeSec),
            "    §7Breaks per second: §9%.2f".format(totalBlocksDestroyed / runtimeSec),
            "    §7Distance per hour: §9%.2f".format((getDistance(startingBlockPos.toVec3d(), currentBlockPos.toVec3d()).toInt() / runtimeSec) * 60 * 60),
            "    §7One food loss per §9${totalBlocksDestroyed / foodLoss}§7 blocks mined",
            "§rEnvironment",
            "    §7Starting coordinates: §9(${startingBlockPos.asString()})",
            "    §7Direction: §9${buildDirectionSaved.displayName}",
            "    §7Blocks destroyed: §9$totalBlocksDestroyed".padStart(6,'0'),
            "    §7Blocks placed: §9$totalBlocksPlaced".padStart(6,'0'),
            "    §7Material: §9${material.localizedName}",
            "    §7Filler: §9${fillerMat.localizedName}",
            "§rTask",
            "    §7Status: §9${currentTask?.taskState}",
            "    §7Target state: §9${currentTask?.block?.localizedName}",
            "    §7Position: §9(${currentTask?.blockPos?.asString()})",
            "§rDebug",
            "    §7Stuck manager: §9${stuckManager}",
            "    §7Pathing: §9$pathing",
            "§rEstimations",
            "    §7${material.localizedName} (main material): §9$materialLeft + ($indirectMaterialLeft)",
            "    §7${fillerMat.localizedName} (filler material): §9$fillerMatLeft",
            "    §7Paving distance left: §9$pavingLeftAll",
            "    §7Estimated destination: §9(${relativeDirection(currentBlockPos, pavingLeft, 0).asString()})",
            "    §7ETA: §9$hoursLeft:$minutesLeft:$secondsLeft"
        )

        if (printDebug.value) {
            // for (x in getQueue()) sendChatMessage(x)
            statistics.addAll(getQueue())
        }

        return statistics
    }

    fun getNextWalkableBlock(): BlockPos {
        var lastWalkable = getNextBlock()

        when (mode.value) {
            Mode.HIGHWAY -> {
                for (step in 1..3) {
                    val pos = relativeDirection(currentBlockPos, step, 0)
                    if (mc.world.getBlockState(pos.down()).block == material &&
                            mc.world.getBlockState(pos).block == Blocks.AIR &&
                            mc.world.getBlockState(pos.up()).block == Blocks.AIR) lastWalkable = pos
                    else break
                }
            }
            Mode.TUNNEL -> {
                for (step in 1..3) {
                    val pos = relativeDirection(currentBlockPos, step, 0)
                    if (mc.world.getBlockState(pos.down()).block == fillerMat &&
                            mc.world.getBlockState(pos).block == Blocks.AIR &&
                            mc.world.getBlockState(pos.up()).block == Blocks.AIR) lastWalkable = pos
                    else break
                }
            }
            else -> {}
        }

        return lastWalkable
    }

    private fun getNextBlock(): BlockPos {
        return getNextBlock(currentBlockPos)
    }

    private fun getNextBlock(blockPos: BlockPos): BlockPos {
        return relativeDirection(blockPos, 1, 0)
    }

    private fun relativeDirection(current: BlockPos, steps: Int, turn: Int): BlockPos {
        val index = buildDirectionSaved.ordinal + turn
        val direction = Direction.values()[Math.floorMod(index, 8)]
        return current.add(direction.directionVec.multiply(steps))
    }

    private fun getAABBSide(bb: AxisAlignedBB, side: EnumFacing): Double {
        return when (side) {
            EnumFacing.UP -> bb.maxY - bb.center.y
            EnumFacing.NORTH -> bb.center.z - bb.minZ
            EnumFacing.EAST -> bb.maxX - bb.center.x
            EnumFacing.SOUTH -> bb.maxZ - bb.center.z
            EnumFacing.WEST -> bb.center.x - bb.minX
            EnumFacing.DOWN -> bb.center.y - bb.minY
        }
    }

    private fun addOffset(cursor: BlockPos, height: Int, width: Int, mat: Block, turn: Boolean) {
        var turnValue = 1
        if (turn) turnValue = -1
        if (mat != fillerMat) {
            if (height > 1) {
                blueprint.add(Pair(relativeDirection(relativeDirection(cursor, 1, 3 * turnValue), width - 1, 2 * turnValue), Blocks.AIR))
            } else {
                blueprint.add(Pair(relativeDirection(relativeDirection(cursor, 1, 3 * turnValue), width - 1, 2 * turnValue), mat))
            }
        } else {
            blueprint.add(Pair(relativeDirection(relativeDirection(cursor, 1, 3 * turnValue), width - 1, 2 * turnValue), material))
        }
    }

    private fun genOffset(cursor: BlockPos, height: Int, width: Int, mat: Block, isOdd: Boolean) {
        blueprint.add(Pair(relativeDirection(cursor, width, -2), mat))
        if (buildDirectionSaved.isDiagonal) {
            addOffset(cursor, height, width, mat, true)
        }
        when {
            isOdd -> {
                blueprint.add(Pair(relativeDirection(cursor, width, 2), mat))
                if (buildDirectionSaved.isDiagonal) {
                    addOffset(cursor, height, width, mat, false)
                }
            }
            else -> {
                val evenCursor = relativeDirection(cursor, 1, 2)
                if (buildDirectionSaved.isDiagonal) {
                    blueprint.add(Pair(relativeDirection(evenCursor, width, 2), mat))
                    addOffset(cursor, height, width, mat, false)
                    addOffset(evenCursor, height, width, mat, false)
                } else {
                    blueprint.add(Pair(relativeDirection(evenCursor, width, 2), mat))
                }
            }
        }
    }

    private fun refreshData() {
        doneTasks.clear()
        pendingTasks.clear()
        updateTasks(currentBlockPos)
        shuffleTasks()
    }

    private fun updateBlockArray(blockPos: BlockPos) {
        var cursor = blockPos.down()

        when (mode.value) {
            Mode.HIGHWAY -> {
                if (baritoneMode.value) {
                    cursor = relativeDirection(cursor, 1, 0)
                    blueprint.add(Pair(cursor, material))
                }
                cursor = relativeDirection(cursor, 1, 0)
                blueprint.add(Pair(cursor, material))
                var buildIterationsWidth = buildWidth.value / 2
                var evenCursor = relativeDirection(cursor, 1, 2)
                var isOdd = false
                if (buildWidth.value % 2 == 1) {
                    isOdd = true
                    buildIterationsWidth++
                } else {
                    blueprint.add(Pair(evenCursor, material))
                }
                for (i in 1 until clearHeight.value + 1) {
                    for (j in 1 until buildIterationsWidth) {
                        if (i == 1) {
                            if (j == buildIterationsWidth - 1 && !cornerBlock.value) {
                                genOffset(cursor, i, j, fillerMat, isOdd)
                            } else {
                                genOffset(cursor, i, j, material, isOdd)
                            }
                        } else {
                            if (i <= railingHeight.value + 1 && j == buildIterationsWidth - 1) {
                                genOffset(cursor, i, j, material, isOdd)
                            } else {
                                if (clearSpace.value) {
                                    genOffset(cursor, i, j, Blocks.AIR, isOdd)
                                }
                            }
                        }
                    }
                    cursor = cursor.up()
                    evenCursor = evenCursor.up()
                    if (clearSpace.value && i < clearHeight.value) {
                        blueprint.add(Pair(cursor, Blocks.AIR))
                        if (!isOdd) blueprint.add(Pair(evenCursor, Blocks.AIR))
                    }
                }
            }
            Mode.TUNNEL -> {
                if (baritoneMode.value) {
                    cursor = relativeDirection(cursor, 1, 0)
                    blueprint.add(Pair(cursor, fillerMat))
                }
                cursor = relativeDirection(cursor, 1, 0)
                blueprint.add(Pair(cursor, fillerMat))
                var buildIterationsWidth = buildWidth.value / 2
                var evenCursor = relativeDirection(cursor, 1, 2)
                var isOdd = false
                if (buildWidth.value % 2 == 1) {
                    isOdd = true
                    buildIterationsWidth++
                } else {
                    blueprint.add(Pair(evenCursor, fillerMat))
                }
                for (i in 1 until clearHeight.value + 2) {
                    for (j in 1 until buildIterationsWidth) {
                        if (i > 1) {
                            if (cornerBlock.value && i == 2 && j == buildIterationsWidth - 1) continue
                            blueprint.add(Pair(relativeDirection(cursor, j, -2), Blocks.AIR))
                            if (isOdd) blueprint.add(Pair(relativeDirection(cursor, j, 2), Blocks.AIR))
                            else blueprint.add(Pair(relativeDirection(evenCursor, j, 2), Blocks.AIR))
                            if (buildDirectionSaved.isDiagonal) {
                                blueprint.add(Pair(relativeDirection(cursor, j, -3), Blocks.AIR))
                                if (isOdd) blueprint.add(Pair(relativeDirection(cursor, j, 3), Blocks.AIR))
                                else blueprint.add(Pair(relativeDirection(evenCursor, j, 3), Blocks.AIR))
                            }
                        }
                    }
                    cursor = cursor.up()
                    evenCursor = evenCursor.up()
                    if (clearSpace.value && i < clearHeight.value + 1) {
                        blueprint.add(Pair(cursor, Blocks.AIR))
                        if (!isOdd) blueprint.add(Pair(evenCursor, Blocks.AIR))
                    }
                }
            }
            Mode.FLAT -> {
                for (bp in getBlockPositionsInArea(cursor.north(buildWidth.value).west(buildWidth.value), cursor.south(buildWidth.value).east(buildWidth.value))) {
                    blueprint.add(Pair(bp, material))
                }
            }
            null -> {
                sendChatMessage("Module logic is a lie.")
                disable()
            }
        }
    }

    data class StuckManagement(
            var stuckLevel: StuckLevel,
            var stuckValue: Int
    )  {
        fun increase(blockTask: BlockTask) {

            when (blockTask.taskState) {
                TaskState.BREAK -> stuckValue += 30
                TaskState.EMERGENCY_BREAK -> stuckValue += 30
                TaskState.PLACE, TaskState.LIQUID_SOURCE, TaskState.LIQUID_FLOW -> stuckValue += 10
                else -> stuckValue++
            }
            when {
                stuckValue < 100 -> {
//                    if (!pathing && blockTask.taskState == TaskState.PLACE && !buildDirectionSaved.isDiagonal) adjustPlayerPosition(true)
                    if (blockTask.taskState != TaskState.BREAKING) {
                        shuffleTasks()
                        if (debugMessages.value == DebugMessages.ALL) sendChatMessage("$chatName Shuffled tasks $stuckValue")
                    }
                }
                stuckValue in 100..200  -> {
                    stuckLevel = StuckLevel.MINOR
                    if (!pathing && blockTask.taskState == TaskState.PLACE && !buildDirectionSaved.isDiagonal) adjustPlayerPosition(true)
                    if (blockTask.taskState != TaskState.BREAKING) {
                        shuffleTasks()
                        if (debugMessages.value == DebugMessages.ALL) sendChatMessage("$chatName Shuffled tasks $stuckValue")
                    }
                    // Jump etc?
                }
                stuckValue in 200..500 -> {
                    stuckLevel = StuckLevel.MODERATE
                    if (!pathing && blockTask.taskState == TaskState.PLACE && !buildDirectionSaved.isDiagonal) adjustPlayerPosition(true)
                    refreshData()
                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("$chatName Refreshing data")
                }
                stuckValue > 500 -> {
                    stuckLevel = StuckLevel.MAYOR
                    if (!pathing && blockTask.taskState == TaskState.PLACE && !buildDirectionSaved.isDiagonal) adjustPlayerPosition(true)
                    refreshData()
                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("$chatName Refreshing data")
//                    reset()
//                    disable()
//                    enable()

                    // Scaffold
                }
            }
        }

        fun reset() {
            stuckLevel = StuckLevel.NONE
            stuckValue = 0
            active = false
        }

        override fun toString(): String {
            return "Level: $stuckLevel Value: $stuckValue"
        }
    }

    data class BlockTask(
            val blockPos: BlockPos,
            var taskState: TaskState,
            var block: Block
    ) {
        override fun toString(): String {
            return "Block: " + block.localizedName + " @ Position: (" + blockPos.asString() + ") Priority: " + taskState.ordinal + " State: " + taskState.toString()
        }
    }

    enum class TaskState(val color: ColorHolder) {
        DONE(ColorHolder(50, 50, 50)),
        BREAKING(ColorHolder(240, 222, 60)),
        EMERGENCY_BREAK(ColorHolder(220, 41, 140)),
        LIQUID_SOURCE(ColorHolder(120, 41, 240)),
        LIQUID_FLOW(ColorHolder(120, 41, 240)),
        BREAK(ColorHolder(222, 0, 0)),
        BROKEN(ColorHolder(111, 0, 0)),
        PLACE(ColorHolder(35, 188, 254)),
        PLACED(ColorHolder(53, 222, 66))
    }

    class BlockTaskComparator {
        companion object : Comparator<BlockTask> {
            override fun compare(a: BlockTask, b: BlockTask): Int = when {
                a.taskState.ordinal != b.taskState.ordinal -> a.taskState.ordinal - b.taskState.ordinal
                a.taskState.ordinal == b.taskState.ordinal && stuckManager.stuckLevel != StuckLevel.NONE -> a.taskState.ordinal - b.taskState.ordinal
                else -> getDistance(mc.player.positionVector, a.blockPos.toVec3d()).toInt() - getDistance(mc.player.positionVector, b.blockPos.toVec3d()).toInt()
            }
        }
    }

    private enum class DebugMessages {
        OFF,
        IMPORTANT,
        ALL
    }

    private enum class Mode {
        HIGHWAY,
        FLAT,
        TUNNEL
    }

    private enum class Page {
        BUILD,
        BEHAVIOR,
        CONFIG
    }

    @Suppress("UNUSED")
    private enum class InteractMode {
        OFF,
        SPOOF,
        VIEWLOCK
    }

    enum class StuckLevel {
        NONE,
        MINOR,
        MODERATE,
        MAYOR
    }
}

