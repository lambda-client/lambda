package me.zeroeightsix.kami.module.modules.misc

import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zeroeightsix.kami.event.Phase
import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.OnUpdateWalkingPlayerEvent
import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.gui.kami.DisplayGuiScreen
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.misc.HighwayTools.StuckManager.increase
import me.zeroeightsix.kami.module.modules.player.AutoEat
import me.zeroeightsix.kami.module.modules.player.InventoryManager
import me.zeroeightsix.kami.process.HighwayToolsProcess
import me.zeroeightsix.kami.setting.Settings
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.EntityUtils.flooredPosition
import me.zeroeightsix.kami.util.WorldUtils.placeBlock
import me.zeroeightsix.kami.util.WorldUtils.rayTraceHitVec
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.ESPRenderer
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString
import me.zeroeightsix.kami.util.math.Direction
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.math.VectorUtils.distanceTo
import me.zeroeightsix.kami.util.math.VectorUtils.getBlockPositionsInArea
import me.zeroeightsix.kami.util.math.VectorUtils.multiply
import me.zeroeightsix.kami.util.math.VectorUtils.toVec3dCenter
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import me.zeroeightsix.kami.util.threads.defaultScope
import me.zeroeightsix.kami.util.threads.onMainThreadSafe
import me.zeroeightsix.kami.util.threads.runSafe
import me.zeroeightsix.kami.util.threads.safeListener
import net.minecraft.block.Block
import net.minecraft.block.Block.getIdFromBlock
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import java.util.*
import kotlin.collections.ArrayList
import kotlin.math.abs

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
        Blocks.WALL_SIGN,
        Blocks.STANDING_BANNER,
        Blocks.WALL_BANNER,
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
    val pendingTasks = PriorityQueue<BlockTask>()
    private val doneTasks = ArrayList<BlockTask>()
    private val blueprint = ArrayList<Pair<BlockPos, Block>>()
    private var waitTicks = 0
    private var blocksPlaced = 0
    var pathing = false
    private var currentBlockPos = BlockPos(0, -1, 0)
    private var startingBlockPos = BlockPos(0, -1, 0)
    private val renderer = ESPRenderer()
    private var active = false
    private var lastHitVec: Vec3d? = null
    private val rotateTimer = TickTimer(TimeUnit.TICKS)

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
        if (toggleInventoryManager.value && InventoryManager.isDisabled) InventoryManager.enable()

        /* Turn on Auto Obsidian if the user wants us to control it. */
        if (toggleAutoObsidian.value && AutoObsidian.isDisabled && mode.value != Mode.TUNNEL) {
            /* If we have no obsidian, immediately turn on Auto Obsidian */
            if (InventoryUtils.countItemAll(49) == 0) {
                AutoObsidian.enable()
            } else {
                Thread {
                    /* Wait 1 second because turning both on simultaneously is buggy */
                    Thread.sleep(1000L)
                    AutoObsidian.enable()
                }.start()
            }
        }

        startingBlockPos = mc.player.flooredPosition
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

        runSafe {
            refreshData()
            printEnable()
        }
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
        if (toggleInventoryManager.value && InventoryManager.isEnabled) InventoryManager.disable()

        /* Turn off auto obsidian if the user wants us to control it */
        if (toggleAutoObsidian.value && AutoObsidian.isEnabled) {
            AutoObsidian.disable()
        }

        printDisable()
    }

    fun isDone(): Boolean = pendingTasks.size == 0

    init {
        safeListener<RenderWorldEvent> {
            renderer.render(false)
        }

        safeListener<OnUpdateWalkingPlayerEvent> { event ->
            if (event.phase != Phase.PRE) return@safeListener

            updateRenderer()

            if (!active) {
                active = true
                BaritoneUtils.primary?.pathingControlManager?.registerProcess(HighwayToolsProcess)
            }

            if (baritoneMode.value) {
                pathing = BaritoneUtils.isPathing
                val taskPos = (pendingTasks.firstOrNull() ?: doneTasks.firstOrNull())?.blockPos
                    ?: BlockPos(0, -1, 0)

                if (player.positionVector.distanceTo(taskPos) < maxReach.value) {
                    if (!isDone()) {
                        if (canDoTask()) {
                            if (!pathing) adjustPlayerPosition(false)
                            val currentFood = player.foodStats.foodLevel
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
                if (currentBlockPos == player.flooredPosition) {
                    doTask()
                } else {
                    currentBlockPos = player.flooredPosition
                    if (abs((buildDirectionSaved.ordinal - Direction.fromEntity(player).ordinal) % 8) == 4) buildDirectionSaved = Direction.fromEntity(player)
                    refreshData()
                }
            }

            if (rotateTimer.tick(20L, false)) return@safeListener
            val rotation = lastHitVec?.let { RotationUtils.getRotationTo(it) } ?: return@safeListener

            when (interacting.value) {
                InteractMode.SPOOF -> {
                    val packet = PlayerPacketManager.PlayerPacket(rotating = true, rotation = rotation)
                    PlayerPacketManager.addPacket(this@HighwayTools, packet)
                }
                InteractMode.VIEW_LOCK -> {
                    player.rotationYaw = rotation.x
                    player.rotationPitch = rotation.y
                }
                else -> {

                }
            }
        }
    }

    private fun SafeClientEvent.updateRenderer() {
        renderer.clear()
        renderer.aFilled = if (filled.value) aFilled.value else 0
        renderer.aOutline = if (outline.value) aOutline.value else 0
//        renderer.add(currentBlockPos, ColorHolder(255, 255, 255))
//        renderer.add(getNextWalkableBlock(), ColorHolder(0, 0, 0))
        for (blockTask in pendingTasks) {
            if (blockTask.taskState == TaskState.DONE) continue
            renderer.add(world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(world, blockTask.blockPos), blockTask.taskState.color)
        }
        for (blockTask in doneTasks) {
            if (blockTask.block == Blocks.AIR) continue
            renderer.add(world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(world, blockTask.blockPos), blockTask.taskState.color)
        }
    }

    private fun addTaskToPending(blockPos: BlockPos, taskState: TaskState, material: Block) {
        pendingTasks.add(BlockTask(blockPos, taskState, material))
    }

    private fun addTaskToDone(blockPos: BlockPos, material: Block) {
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
        return !BaritoneUtils.paused && !AutoObsidian.isActive() && !AutoEat.eating
    }

    private fun SafeClientEvent.doTask() {
        if (!isDone() && canDoTask()) {
            if (waitTicks == 0) {
                val blockTask = pendingTasks.peek()

                when (blockTask.taskState) {
                    TaskState.DONE -> {
                        doDone(blockTask)
                    }
                    TaskState.BREAKING -> {
                        if (!doBreaking(blockTask)) {
                            increase(blockTask)
                            return
                        }
                    }
                    TaskState.BROKEN -> {
                        doBroken(blockTask)
                    }
                    TaskState.PLACED -> {
                        doPlaced(blockTask)
                    }
                    TaskState.EMERGENCY_BREAK -> {
                        if (!doBreak(blockTask)) {
                            increase(blockTask)
                            return
                        }
                    }
                    TaskState.BREAK -> if (!doBreak(blockTask)) {
                        increase(blockTask)
                        return
                    }
                    TaskState.PLACE, TaskState.LIQUID_SOURCE, TaskState.LIQUID_FLOW -> {
                        if (!doPlace(blockTask)) {
                            increase(blockTask)
                            return
                        }
                    }
                }

                if (blockTask.taskState != TaskState.BREAKING) StuckManager.reset()
            } else {
                waitTicks--
            }
        }
    }

    private fun SafeClientEvent.doDone(blockTask: BlockTask) {
        pendingTasks.poll()
        doneTasks.add(blockTask)
        doTask()
    }

    private fun SafeClientEvent.doBreaking(blockTask: BlockTask): Boolean {
        when (world.getBlockState(blockTask.blockPos).block) {
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
                if (world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
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

    private fun SafeClientEvent.doBroken(blockTask: BlockTask) {
        when (world.getBlockState(blockTask.blockPos).block) {
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

    private fun SafeClientEvent.doPlaced(blockTask: BlockTask) {
        val block = world.getBlockState(blockTask.blockPos).block

        when {
            blockTask.block == block && block != Blocks.AIR -> updateTask(blockTask, TaskState.DONE)
            blockTask.block == Blocks.AIR && block != Blocks.AIR -> updateTask(blockTask, TaskState.BREAK)
            blockTask.block == block && block == Blocks.AIR -> updateTask(blockTask, TaskState.BREAK)
            else -> updateTask(blockTask, TaskState.PLACE)
        }
        doTask()
    }

    private fun SafeClientEvent.doBreak(blockTask: BlockTask): Boolean {

        // ignore blocks
        if (blockTask.taskState != TaskState.EMERGENCY_BREAK) {
            if (blockTask.block != Blocks.AIR && ignoreBlocks.contains(blockTask.block)) {
                updateTask(blockTask, TaskState.DONE)
                doTask()
            }
        }

        // last check before breaking
        when (world.getBlockState(blockTask.blockPos).block) {
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
                if (world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
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
                    if (handleLiquid(blockTask)) {
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

    private fun SafeClientEvent.doPlace(blockTask: BlockTask): Boolean {
        val block = world.getBlockState(blockTask.blockPos).block

        when {
            block == material && block == blockTask.block -> {
                updateTask(blockTask, TaskState.PLACED)
            }
            block == fillerMat && block == blockTask.block -> {
                updateTask(blockTask, TaskState.PLACED)
            }
            else -> {
                if (!WorldUtils.isPlaceable(blockTask.blockPos)) {
//                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("Error: " + blockTask.blockPos + " is not a valid position to place a block, removing task.")
//                    blockQueue.remove(blockTask)
                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("Invalid place position: " + blockTask.blockPos)
                    refreshData()
                    return false
                }

                if (!inventoryProcessor(blockTask)) {
                    return false
                }
                if (!placeBlock(blockTask)) {
                    return false
                }
                if (blockTask.taskState != TaskState.PLACE && isInsideSelection(blockTask.blockPos)) {
                    updateTask(blockTask, Blocks.AIR)
                }

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

    private fun SafeClientEvent.checkTasks(): Boolean {
        for (blockTask in doneTasks) {
            val block = world.getBlockState(blockTask.blockPos).block
            if (ignoreBlocks.contains(block)) continue
            when {
                blockTask.block == material && block != material -> return false
                mode.value == Mode.TUNNEL && blockTask.block == fillerMat && block != fillerMat -> return false
                blockTask.block == Blocks.AIR && block != Blocks.AIR -> return false
            }
        }
        return true
    }

    private fun SafeClientEvent.updateTasks(originPos: BlockPos) {
        blueprint.clear()
        updateBlockArray(originPos)
        updateBlockArray(getNextBlock(originPos))
        for ((blockPos, blockType) in blueprint) {
            val isReplaceable = world.getBlockState(blockPos).material.isReplaceable
            if (blockPos == player.flooredPosition.down()) continue
            when (val block = world.getBlockState(blockPos).block) {
                is BlockLiquid -> {
                    var filler = fillerMat
                    if (isInsideBuild(blockPos) || fillerMatLeft == 0) filler = material
                    when (world.getBlockState(blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                        true -> addTaskToPending(blockPos, TaskState.LIQUID_FLOW, filler)
                        false -> addTaskToPending(blockPos, TaskState.LIQUID_SOURCE, filler)
                    }
                }
                else -> {
                    when (blockType) {
                        Blocks.AIR -> {
                            when {
                                block in ignoreBlocks -> addTaskToDone(blockPos, Blocks.AIR)
                                block == Blocks.AIR -> addTaskToDone(blockPos, Blocks.AIR)
                                block == Blocks.FIRE -> addTaskToPending(blockPos, TaskState.BREAK, Blocks.FIRE)
                                block != Blocks.AIR -> addTaskToPending(blockPos, TaskState.BREAK, Blocks.AIR)
                            }
                        }
                        material -> {
                            when {
                                block == material -> addTaskToDone(blockPos, material)
                                !isReplaceable && block != material -> addTaskToPending(blockPos, TaskState.BREAK, material)
                                isReplaceable -> addTaskToPending(blockPos, TaskState.PLACE, material)
                            }
                        }
                        fillerMat -> {
                            if (mode.value == Mode.HIGHWAY) {
                                if (buildDirectionSaved.isDiagonal) {
                                    val blockUp = world.getBlockState(blockPos.up()).block
                                    when {
                                        WorldUtils.getNeighbour(blockPos.up(), 1) == null && blockUp != material -> addTaskToPending(blockPos, TaskState.PLACE, fillerMat)
                                        WorldUtils.getNeighbour(blockPos.up(), 1) != null -> addTaskToDone(blockPos, fillerMat)
                                    }
                                }
                            } else {
                                when {
                                    block == fillerMat -> addTaskToDone(blockPos, fillerMat)
                                    !isReplaceable && block != fillerMat -> addTaskToPending(blockPos, TaskState.BREAK, fillerMat)
                                    isReplaceable -> addTaskToPending(blockPos, TaskState.PLACE, fillerMat)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun updateBlockArray(blockPos: BlockPos) {
        var cursor = blockPos.down()

        when (mode.value) {
            Mode.HIGHWAY, Mode.TUNNEL -> {
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
                if (mode.value == Mode.HIGHWAY) {
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
                } else {
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

    private fun SafeClientEvent.inventoryProcessor(blockTask: BlockTask): Boolean {
        when (blockTask.taskState) {
            TaskState.BREAK, TaskState.EMERGENCY_BREAK -> {
                AutoTool.equipBestTool(world.getBlockState(blockTask.blockPos))
//                val noHotbar = InventoryUtils.getSlotsNoHotbar(278)
//                if (InventoryUtils.getSlotsHotbar(278) == null && noHotbar != null) {
////                    InventoryUtils.moveToHotbar(278, 130)
//                    InventoryUtils.moveToSlot(noHotbar[0], 36)
//                } else if (InventoryUtils.getSlots(0, 35, 278) == null) {
//                    sendChatMessage("$chatName No Pickaxe was found in inventory")
//                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
//                    disable()
//                    return false
//                }
//                InventoryUtils.swapSlotToItem(278)
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

    private fun SafeClientEvent.handleLiquid(blockTask: BlockTask): Boolean {
        var foundLiquid = false
        for (side in EnumFacing.values()) {
            val neighbour = blockTask.blockPos.offset(side)
            val neighbourBlock = world.getBlockState(neighbour).block

            if (neighbourBlock is BlockLiquid) {
                val isFlowing = world.getBlockState(blockTask.blockPos).let {
                    it.block is BlockLiquid && it.getValue(BlockLiquid.LEVEL) != 0
                }

                if (player.distanceTo(neighbour) > maxReach.value) continue

                foundLiquid = true
                val found = ArrayList<Triple<BlockTask, TaskState, Block>>()
                val filler = if (isInsideBuild(neighbour)) material else fillerMat

                for (bt in pendingTasks) {
                    if (bt.blockPos == neighbour) {
                        when (isFlowing) {
                            false -> found.add(Triple(bt, TaskState.LIQUID_SOURCE, filler))
                            true -> found.add(Triple(bt, TaskState.LIQUID_FLOW, filler))
                        }
                    }
                }

                if (found.isEmpty()) {
                    when (isFlowing) {
                        false -> addTaskToPending(neighbour, TaskState.LIQUID_SOURCE, filler)
                        true -> addTaskToPending(neighbour, TaskState.LIQUID_FLOW, filler)
                    }
                } else {
                    for (triple in found) {
                        updateTask(triple.first, triple.second)
                        updateTask(triple.first, triple.third)
                    }
                }
            }
        }
        return foundLiquid
    }

    private fun SafeClientEvent.mineBlock(blockTask: BlockTask) {
        if (blockTask.blockPos == player.flooredPosition.down()) {
            updateTask(blockTask, TaskState.DONE)
            return
        }

        /* For fire, we just need to mine the top of the block below the fire */
        /* TODO: This will not work if the top of the block which the fire is on is not visible */
        if (blockTask.block == Blocks.FIRE) {
            val blockBelowFire = blockTask.blockPos.down()
            playerController.clickBlock(blockBelowFire, EnumFacing.UP)
            player.swingArm(EnumHand.MAIN_HAND)
            updateTask(blockTask, TaskState.BREAKING)
            return
        }

        val rayTraceResult = rayTraceHitVec(blockTask.blockPos)

        if (rayTraceResult == null) {
            increase(blockTask)
            refreshData()
            if (StuckManager.stuckLevel == StuckLevel.NONE) doTask()
            return
        }

        val side = rayTraceResult.sideHit
        lastHitVec = rayTraceResult.hitVec
        rotateTimer.reset()

        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.NETHERRACK -> {
                updateTask(blockTask, TaskState.BROKEN)
                waitTicks = tickDelayBreak.value
                defaultScope.launch {
                    delay(5L)
                    onMainThreadSafe {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockTask.blockPos, side))
                        player.swingArm(EnumHand.MAIN_HAND)
                    }
                    delay(10L)
                    onMainThreadSafe {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockTask.blockPos, side))
                        player.swingArm(EnumHand.MAIN_HAND)
                    }
                }
            }
            else -> dispatchGenericMineThread(blockTask, side)
        }
    }

    /* Dispatches a thread to mine any non-netherrack blocks generically */
    private fun dispatchGenericMineThread(blockTask: BlockTask, facing: EnumFacing) {
        val digPacket = when (blockTask.taskState) {
            TaskState.BREAK, TaskState.EMERGENCY_BREAK -> CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockTask.blockPos, facing)
            TaskState.BREAKING -> CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockTask.blockPos, facing)
            else -> CPacketPlayerDigging()
        }
        if (blockTask.taskState == TaskState.BREAK || blockTask.taskState == TaskState.EMERGENCY_BREAK) updateTask(blockTask, TaskState.BREAKING)
        defaultScope.launch {
            delay(5L)
            onMainThreadSafe {
                connection.sendPacket(digPacket)
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
    }

    private fun SafeClientEvent.placeBlock(blockTask: BlockTask): Boolean {
        val pair = WorldUtils.getNeighbour(blockTask.blockPos, 1, 6.5f)
            ?: run {
                sendChatMessage("Can't find neighbour block")
                return false
            }

        if (!isVisible(blockTask.blockPos)) {
            if (illegalPlacements.value) {
                if (debugMessages.value == DebugMessages.ALL) {
                    sendChatMessage("Trying to place through wall ${blockTask.blockPos}")
                }
            } else {
                return false
            }
        }

        lastHitVec = WorldUtils.getHitVec(pair.second, pair.first)
        rotateTimer.reset()

        connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))

        defaultScope.launch {
            delay(5L)
            onMainThreadSafe {
                placeBlock(pair.second, pair.first)
            }

            delay(10L)
            onMainThreadSafe {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }
        }
        return true
    }

    private fun SafeClientEvent.isVisible(pos: BlockPos): Boolean {
        val eyePos = player.getPositionEyes(1f)

        for (side in EnumFacing.values()) {
            val blockState = world.getBlockState(pos.offset(side))
            if (blockState.isFullBlock) continue
            val rayTraceResult = world.rayTraceBlocks(eyePos, WorldUtils.getHitVec(pos, side), false, false, true)
                ?: return true
            if (rayTraceResult.typeOfHit == RayTraceResult.Type.BLOCK && rayTraceResult.hitVec.distanceTo(pos) > 1.0) continue
            return true
        }

        return false
    }

    private fun isInsideSelection(blockPos: BlockPos): Boolean {
        return pendingTasks.any { it.blockPos == blockPos }
    }

    private fun isInsideBuild(blockPos: BlockPos): Boolean {
        return pendingTasks.any { it.blockPos == blockPos && it.block == material }
    }

    private fun SafeClientEvent.adjustPlayerPosition(bridge: Boolean) {
        var vec = getNextWalkableBlock().toVec3dCenter().subtract(player.positionVector)
        when {
            bridge && !buildDirectionSaved.isDiagonal -> vec = vec.add(Vec3d(buildDirectionSaved.directionVec).scale(0.525))
            bridge && buildDirectionSaved.isDiagonal -> vec = vec.add(Vec3d(buildDirectionSaved.directionVec).scale(0.525))
        }
        player.motionX = (vec.x / 2.0).coerceIn(-0.2, 0.2)
        player.motionZ = (vec.z / 2.0).coerceIn(-0.2, 0.2)
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
                if (startingBlockPos.y in 117..119 && mode.value != Mode.TUNNEL) append("\n    §9> §cCheck coordinate Y / altitude and make sure to move around Y 120 for the correct height")
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

                if (baritoneMode.value) append("\n    §9> §7Distance: §a${startingBlockPos.distanceTo(currentBlockPos).toInt()}§r")

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
        val seconds = (runtimeSec % 60).toInt().toString().padStart(2, '0')
        val minutes = ((runtimeSec % 3600) / 60).toInt().toString().padStart(2, '0')
        val hours = (runtimeSec / 3600).toInt().toString().padStart(2, '0')

        val distanceDone = startingBlockPos.distanceTo(currentBlockPos).toInt()

        val secLeft = runtimeSec / (distanceDone * pavingLeftAll + 0.0001)
        val secondsLeft = (secLeft % 60).toInt().toString().padStart(2, '0')
        val minutesLeft = ((secLeft % 3600) / 60).toInt().toString().padStart(2, '0')
        val hoursLeft = (secLeft / 3600).toInt().toString().padStart(2, '0')

        val statistics = mutableListOf(
            "§rPerformance",
            "    §7Runtime: §9$hours:$minutes:$seconds",
            "    §7Placements per second: §9%.2f".format(totalBlocksPlaced / runtimeSec),
            "    §7Breaks per second: §9%.2f".format(totalBlocksDestroyed / runtimeSec),
            "    §7Distance per hour: §9%.2f".format((startingBlockPos.distanceTo(currentBlockPos).toInt() / runtimeSec) * 60 * 60),
            "    §7One food loss per §9${totalBlocksDestroyed / foodLoss}§7 blocks mined",
            "§rEnvironment",
            "    §7Starting coordinates: §9(${startingBlockPos.asString()})",
            "    §7Direction: §9${buildDirectionSaved.displayName}",
            "    §7Blocks destroyed: §9$totalBlocksDestroyed".padStart(6, '0'),
            "    §7Blocks placed: §9$totalBlocksPlaced".padStart(6, '0'),
            "    §7Material: §9${material.localizedName}",
            "    §7Filler: §9${fillerMat.localizedName}",
            "§rTask",
            "    §7Status: §9${currentTask?.taskState}",
            "    §7Target state: §9${currentTask?.block?.localizedName}",
            "    §7Position: §9(${currentTask?.blockPos?.asString()})",
            "§rDebug",
            "    §7Stuck manager: §9${StuckManager}",
            "    §7Pathing: §9$pathing",
            "§rEstimations",
            "    §7${material.localizedName} (main material): §9$materialLeft + ($indirectMaterialLeft)",
            "    §7${fillerMat.localizedName} (filler material): §9$fillerMatLeft",
            "    §7Paving distance left: §9$pavingLeftAll",
            "    §7Estimated destination: §9(${relativeDirection(currentBlockPos, pavingLeft, 0).asString()})",
            "    §7ETA: §9$hoursLeft:$minutesLeft:$secondsLeft"
        )

        if (printDebug.value) {
            statistics.addAll(getQueue())
        }

        return statistics
    }

    private fun getQueue(): List<String> {
        val message = ArrayList<String>()
        message.add("QUEUE:")
        addTaskToMessageList(message, pendingTasks)
        message.add("DONE:")
        addTaskToMessageList(message, doneTasks)
        return message
    }

    private fun addTaskToMessageList(list: ArrayList<String>, tasks: Collection<BlockTask>) {
        for (blockTask in tasks) list.add("    " + blockTask.block.localizedName + "@(" + blockTask.blockPos.asString() + ") Priority: " + blockTask.taskState.ordinal + " State: " + blockTask.taskState.toString())
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
            else -> {
            }
        }

        return lastWalkable
    }

    private fun getNextBlock(blockPos: BlockPos = currentBlockPos): BlockPos {
        return relativeDirection(blockPos, 1, 0)
    }

    private fun relativeDirection(current: BlockPos, steps: Int, turn: Int): BlockPos {
        val index = buildDirectionSaved.ordinal + turn
        val direction = Direction.values()[Math.floorMod(index, 8)]
        return current.add(direction.directionVec.multiply(steps))
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

    private fun SafeClientEvent.refreshData() {
        doneTasks.clear()
        pendingTasks.clear()
        updateTasks(currentBlockPos)
        shuffleTasks()
    }

    object StuckManager {
        var stuckLevel = StuckLevel.NONE
        var stuckValue = 0

        fun SafeClientEvent.increase(blockTask: BlockTask) {
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
                stuckValue in 100..200 -> {
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
                    if (debugMessages.value != DebugMessages.OFF && (mc.currentScreen !is DisplayGuiScreen || mc.currentServerData != null)) sendChatMessage("$chatName Refreshing data")
                }
                stuckValue > 500 -> {
                    stuckLevel = StuckLevel.MAYOR
                    if (!pathing && blockTask.taskState == TaskState.PLACE && !buildDirectionSaved.isDiagonal) adjustPlayerPosition(true)
                    refreshData()
                    if (debugMessages.value != DebugMessages.OFF && (mc.currentScreen !is DisplayGuiScreen || mc.currentServerData != null)) sendChatMessage("$chatName Refreshing data")
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

    private fun shuffleTasks() {
        val shuffled = pendingTasks.shuffled()
        pendingTasks.clear()
        pendingTasks.addAll(shuffled)
    }

    class BlockTask(
        val blockPos: BlockPos,
        var taskState: TaskState,
        var block: Block
    ) : Comparable<BlockTask> {
        override fun compareTo(other: BlockTask) = when {
            taskState.ordinal != other.taskState.ordinal -> taskState.ordinal - other.taskState.ordinal
            taskState.ordinal == other.taskState.ordinal && StuckManager.stuckLevel != StuckLevel.NONE -> 0
            else -> (mc.player.distanceTo(blockPos) - mc.player.distanceTo(other.blockPos)).toInt()
        }

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
        VIEW_LOCK
    }

    enum class StuckLevel {
        NONE,
        MINOR,
        MODERATE,
        MAYOR
    }
}

