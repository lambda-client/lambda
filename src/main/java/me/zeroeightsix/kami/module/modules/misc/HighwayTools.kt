package me.zeroeightsix.kami.module.modules.misc

import baritone.api.pathing.goals.GoalNear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zeroeightsix.kami.event.Phase
import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.OnUpdateWalkingPlayerEvent
import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.module.Module
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
import me.zeroeightsix.kami.util.math.VectorUtils.multiply
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import me.zeroeightsix.kami.util.threads.*
import net.minecraft.block.Block
import net.minecraft.block.Block.getIdFromBlock
import net.minecraft.block.BlockAir
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
import kotlin.collections.HashSet
import kotlin.collections.LinkedHashMap

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
    private val railingHeight = register(Settings.integerBuilder("RailingHeight").withValue(1).withRange(1, 4).withStep(1).withVisibility { railing.value && page.value == Page.BUILD && mode.value == Mode.HIGHWAY })
    private val cornerBlock = register(Settings.booleanBuilder("CornerBlock").withValue(false).withVisibility { page.value == Page.BUILD && (mode.value == Mode.HIGHWAY || mode.value == Mode.TUNNEL) })

    // behavior settings
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
    private var baritoneSettingAllowPlace = false
    private var baritoneSettingRenderGoal = false

    // Blue print
    private var startingDirection = Direction.NORTH
    private var currentBlockPos = BlockPos(0, -1, 0)
    private var startingBlockPos = BlockPos(0, -1, 0)
    private val blueprint = ArrayList<Pair<BlockPos, Block>>()
    private val blueprintNew = LinkedHashMap<BlockPos, Block>()

    // State
    private var active = false
    private var waitTicks = 0

    // Rotation
    private var lastHitVec: Vec3d? = null
    private val rotateTimer = TickTimer(TimeUnit.TICKS)

    // Pathing
    var goal: GoalNear? = null; private set

    // Tasks
    private val pendingTasks = HashSet<BlockTask>()
    private val doneTasks = ArrayList<BlockTask>()
    var lastTask: BlockTask? = null; private set

    // Stats
    private var totalBlocksPlaced = 0
    private var totalBlocksDestroyed = 0
    private var startTime = 0L
    private var prevFood = 0
    private var foodLoss = 1
    private var materialLeft = 0
    private var fillerMatLeft = 0

    private val renderer = ESPRenderer()

    override fun isActive(): Boolean {
        return isEnabled && active
    }

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
        startingDirection = Direction.fromEntity(mc.player)

        startTime = System.currentTimeMillis()
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0

        baritoneSettingAllowPlace = BaritoneUtils.settings?.allowPlace?.value ?: true
        BaritoneUtils.settings?.allowPlace?.value = false

        if (!goalRender.value) {
            baritoneSettingRenderGoal = BaritoneUtils.settings?.renderGoal?.value ?: true
            BaritoneUtils.settings?.renderGoal?.value = false
        }

        runSafe {
            refreshData()
            printEnable()
        }
    }

    override fun onDisable() {
        if (mc.player == null) return

        active = false

        BaritoneUtils.settings?.allowPlace?.value = baritoneSettingAllowPlace
        if (!goalRender.value) BaritoneUtils.settings?.renderGoal?.value = baritoneSettingRenderGoal

        /* Turn off inventory manager if the users wants us to control it */
        if (toggleInventoryManager.value && InventoryManager.isEnabled) InventoryManager.disable()

        /* Turn off auto obsidian if the user wants us to control it */
        if (toggleAutoObsidian.value && AutoObsidian.isEnabled) {
            AutoObsidian.disable()
        }

        lastTask = null

        printDisable()
    }

    init {
        safeListener<RenderWorldEvent> {
            renderer.render(false)
        }

        safeListener<OnUpdateWalkingPlayerEvent> { event ->
            if (event.phase != Phase.PRE) return@safeListener

            if (!active) {
                active = true
                BaritoneUtils.primary?.pathingControlManager?.registerProcess(HighwayToolsProcess)
            }

            updateRenderer()
            updateFood()

            if (BaritoneUtils.paused || AutoObsidian.isActive() || AutoEat.eating) return@safeListener

            doPathing()
            runTasks()

            doRotation()
        }
    }

    private fun SafeClientEvent.updateRenderer() {
        renderer.clear()
        renderer.aFilled = if (filled.value) aFilled.value else 0
        renderer.aOutline = if (outline.value) aOutline.value else 0
        for (blockTask in pendingTasks) {
            if (blockTask.taskState == TaskState.DONE) continue
            renderer.add(world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(world, blockTask.blockPos), blockTask.taskState.color)
        }
        for (blockTask in doneTasks) {
            if (blockTask.block == Blocks.AIR) continue
            renderer.add(world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(world, blockTask.blockPos), blockTask.taskState.color)
        }
    }

    private fun SafeClientEvent.updateFood() {
        val currentFood = player.foodStats.foodLevel
        if (currentFood != prevFood) {
            if (currentFood < prevFood) foodLoss++
            prevFood = currentFood
        }
    }

    private fun SafeClientEvent.doRotation() {
        if (rotateTimer.tick(20L, false)) return
        val rotation = lastHitVec?.let { RotationUtils.getRotationTo(it) } ?: return

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

    private fun SafeClientEvent.refreshData() {
        doneTasks.clear()
        pendingTasks.clear()
        lastTask = null
        updateTasks(currentBlockPos)
    }

    private fun SafeClientEvent.updateTasks(originPos: BlockPos) {
        blueprintNew.clear()
        generateBluePrint(originPos)

        for ((pos, block) in blueprintNew) {
            if (block == Blocks.AIR) {
                addTaskClear(pos)
            } else {
                addTaskBuild(pos, block)
            }
        }
    }

    private fun SafeClientEvent.addTaskBuild(pos: BlockPos, block: Block) {
        val blockState = world.getBlockState(pos)

        when {
            blockState.block == block -> {
                addTaskToDone(pos, block)
            }
            blockState.material.isReplaceable -> {
                addTaskToPending(pos, TaskState.PLACE, block)
            }
            else -> {
                addTaskToPending(pos, TaskState.BREAK, block)
            }
        }
    }

    private fun SafeClientEvent.addTaskClear(pos: BlockPos) {
        if (world.isAirBlock(pos)) {
            addTaskToDone(pos, Blocks.AIR)
        } else {
            addTaskToPending(pos, TaskState.BREAK, Blocks.AIR)
        }
    }

    private fun generateBluePrint(feetPos: BlockPos) {
        val basePos = feetPos.down()

        if (mode.value != Mode.FLAT) {
            val zDirection = startingDirection
            val xDirection = zDirection.clockwise(2)
            val nextPos = basePos.add(zDirection.directionVec)

            generateClear(basePos, xDirection)
            generateClear(nextPos, xDirection)

            generateBase(basePos, xDirection)
            generateBase(nextPos, xDirection)
        } else {
            generateFlat(basePos)
        }
    }

    private fun generateClear(basePos: BlockPos, xDirection: Direction) {
        if (!clearSpace.value) return

        for (w in 0 until buildWidth.value) {
            for (h in 0 until clearHeight.value) {
                val x = w - buildWidth.value / 2
                val pos = basePos.add(xDirection.directionVec.multiply(x)).up(h)

                if (mode.value == Mode.HIGHWAY && h == 0 && isRail(w)) {
                    continue
                }

                blueprintNew[pos] = Blocks.AIR
            }
        }
    }

    private fun generateBase(basePos: BlockPos, xDirection: Direction) {
        val baseMaterial = if (mode.value == Mode.TUNNEL) fillerMat else material

        for (w in 0 until buildWidth.value) {
            val x = w - buildWidth.value / 2
            val pos = basePos.add(xDirection.directionVec.multiply(x))

            if (mode.value == Mode.HIGHWAY) {
                if (isRail(w)) {
                    for (y in 1..railingHeight.value) {
                        blueprintNew[pos.up(y)] = baseMaterial
                    }
                } else {
                    blueprintNew[pos] = baseMaterial
                }
            } else {
                blueprintNew[pos.down()] = baseMaterial
            }
        }
    }

    private fun isRail(w: Int) = railing.value && w !in 1 until buildWidth.value - 1

    private fun generateFlat(basePos: BlockPos) {
        // Base
        for (x in -buildWidth.value..buildWidth.value) {
            for (z in -buildWidth.value..buildWidth.value) {
                val pos = basePos.add(x, 0, z)

                blueprintNew[pos] = material
            }
        }

        // Clear
        if (!clearSpace.value) return
        for (x in -buildWidth.value..buildWidth.value) {
            for (z in -buildWidth.value..buildWidth.value) {
                for (y in 1 until clearHeight.value) {
                    val pos = basePos.add(x, y, z)
                    blueprintNew[pos] = Blocks.AIR
                }
            }
        }
    }

    private fun addTaskToPending(blockPos: BlockPos, taskState: TaskState, material: Block) {
        pendingTasks.add(BlockTask(blockPos, taskState, material))
    }

    private fun addTaskToDone(blockPos: BlockPos, material: Block) {
        doneTasks.add(BlockTask(blockPos, TaskState.DONE, material))
    }

    private fun SafeClientEvent.doPathing() {
        val nextPos = getNextPos()

        if (nextPos == mc.player.flooredPosition) {
            currentBlockPos = nextPos
            goal = null
        } else {
            goal = GoalNear(nextPos, 0)
        }
    }

    private fun SafeClientEvent.getNextPos() : BlockPos {
        val baseMaterial = if (mode.value == Mode.TUNNEL) fillerMat else material
        var lastPos = currentBlockPos

        for (step in 1..2) {
            val pos = currentBlockPos.add(startingDirection.directionVec.multiply(step))

            if (!blueprintNew.containsKey(pos.down())) break

            val block = world.getBlockState(pos).block
            val blockBelow = world.getBlockState(pos.down()).block

            if (block is BlockLiquid || blockBelow is BlockLiquid) break
            if (block !is BlockAir || blockBelow != baseMaterial) break

            lastPos = pos
        }

        return lastPos
    }

    private fun SafeClientEvent.runTasks() {
        if (pendingTasks.isNotEmpty()) {
            val sortedTasks = pendingTasks.sortedBy {
                it.taskState.ordinal * 10 +
                    player.getPositionEyes(1f).distanceTo(it.blockPos) * 2 +
                    it.stuckTicks * 2 +
                    it.ranTicks
            }

            (lastTask?: sortedTasks.firstOrNull())?.let {
                val dist = player.getPositionEyes(1f).distanceTo(it.blockPos) - 0.7
                if (dist > maxReach.value) {
                    refreshData()
                } else {
                    doNextTask(sortedTasks)
                }
            }
        } else {
            if (checkDoneTasks()) {
                doneTasks.clear()
                updateTasks(currentBlockPos.add(startingDirection.directionVec))
            } else {
                refreshData()
            }
        }
    }

    private fun SafeClientEvent.doNextTask(sortedTasks: List<BlockTask>) {
        if (goal != null) return

        if (waitTicks > 0) {
            waitTicks--
            return
        }

        lastTask?.let {
            doTask(it)

            if (it.taskState != TaskState.DONE) {
                if (it.stuckTicks > 20) refreshData()
                return
            }
        }

        for (task in sortedTasks) {
            doTask(task)
            if (task.taskState != TaskState.DONE) break
            lastTask = task
        }
    }

    private fun SafeClientEvent.doTask(blockTask: BlockTask) {
        blockTask.onTick()

        when (blockTask.taskState) {
            TaskState.DONE -> {
                doDone(blockTask)
            }
            TaskState.BREAKING -> {
                doBreaking(blockTask)
            }
            TaskState.BROKEN -> {
                doBroken(blockTask)
            }
            TaskState.PLACED -> {
                doPlaced(blockTask)
            }
            TaskState.EMERGENCY_BREAK, TaskState.BREAK -> {
                doBreak(blockTask)
            }
            TaskState.PLACE, TaskState.LIQUID_SOURCE, TaskState.LIQUID_FLOW -> {
                doPlace(blockTask)
            }
        }
    }

    private fun doDone(blockTask: BlockTask) {
        pendingTasks.remove(blockTask)
        doneTasks.add(blockTask)
    }

    private fun SafeClientEvent.doBreaking(blockTask: BlockTask) {
        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                totalBlocksDestroyed++
                waitTicks = tickDelayBreak.value
                if (blockTask.block == material || blockTask.block == fillerMat) {
                    blockTask.updateState(TaskState.PLACE)
                } else {
                    blockTask.updateState(TaskState.DONE)
                }
            }
            is BlockLiquid -> {
                var filler = fillerMat
                if (isInsideBuild(blockTask.blockPos) || fillerMatLeft == 0) filler = material
                if (world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                    blockTask.updateState(TaskState.LIQUID_FLOW)
                    blockTask.updateMaterial(filler)
                } else {
                    blockTask.updateState(TaskState.LIQUID_SOURCE)
                    blockTask.updateMaterial(filler)
                }
            }
            else -> {
                mineBlock(blockTask)
            }
        }
    }

    private fun SafeClientEvent.doBroken(blockTask: BlockTask) {
        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                totalBlocksDestroyed++
                if (blockTask.block == material || blockTask.block == fillerMat) {
                    blockTask.updateState(TaskState.PLACE)
                } else {
                    blockTask.updateState(TaskState.DONE)
                }
            }
            else -> {
                blockTask.updateState(TaskState.BREAK)
            }
        }
    }

    private fun SafeClientEvent.doPlaced(blockTask: BlockTask) {
        val block = world.getBlockState(blockTask.blockPos).block

        when {
            blockTask.block == block && block != Blocks.AIR -> blockTask.updateState(TaskState.DONE)
            blockTask.block == Blocks.AIR && block != Blocks.AIR -> blockTask.updateState(TaskState.BREAK)
            blockTask.block == block && block == Blocks.AIR -> blockTask.updateState(TaskState.BREAK)
            else -> blockTask.updateState(TaskState.PLACE)
        }
    }

    private fun SafeClientEvent.doBreak(blockTask: BlockTask) {

        // ignore blocks
        if (blockTask.taskState != TaskState.EMERGENCY_BREAK
            && blockTask.block != Blocks.AIR
            && ignoreBlocks.contains(blockTask.block)) {
                blockTask.updateState(TaskState.DONE)
        }

        // last check before breaking
        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                if (blockTask.block == Blocks.AIR) {
                    blockTask.updateState(TaskState.DONE)
                } else {
                    blockTask.updateState(TaskState.PLACE)
                }
            }
            is BlockLiquid -> {
                var filler = fillerMat
                if (isInsideBuild(blockTask.blockPos) || fillerMatLeft == 0) filler = material
                if (world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                    blockTask.updateState(TaskState.LIQUID_FLOW)
                    blockTask.updateMaterial(filler)
                } else {
                    blockTask.updateState(TaskState.LIQUID_SOURCE)
                    blockTask.updateMaterial(filler)
                }
            }
            else -> {
                // liquid search around the breaking block
                if (blockTask.taskState != TaskState.EMERGENCY_BREAK) {
                    if (handleLiquid(blockTask)) {
                        blockTask.updateState(TaskState.EMERGENCY_BREAK)
                        return
                    }
                }

                inventoryProcessor(blockTask)

                mineBlock(blockTask)
            }
        }
    }

    private fun SafeClientEvent.doPlace(blockTask: BlockTask) {
        val block = world.getBlockState(blockTask.blockPos).block

        when {
            block == material && block == blockTask.block -> {
                blockTask.updateState(TaskState.PLACED)
            }
            block == fillerMat && block == blockTask.block -> {
                blockTask.updateState(TaskState.PLACED)
            }
            else -> {
                if (!WorldUtils.isPlaceable(blockTask.blockPos)) {
                    if (debugMessages.value != DebugMessages.OFF) sendChatMessage("Invalid place position: " + blockTask.blockPos)
                    refreshData()
                    return
                }

                inventoryProcessor(blockTask)

                placeBlock(blockTask)

                if (blockTask.taskState != TaskState.PLACE && isInsideSelection(blockTask.blockPos)) {
                    blockTask.updateMaterial(Blocks.AIR)
                }

                blockTask.updateState(TaskState.PLACED)

                waitTicks = tickDelayPlace.value
                totalBlocksPlaced++
            }
        }
    }

    private fun SafeClientEvent.checkDoneTasks(): Boolean {
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

    private fun SafeClientEvent.inventoryProcessor(blockTask: BlockTask) {
        when (blockTask.taskState) {
            TaskState.BREAK, TaskState.EMERGENCY_BREAK -> {
                AutoTool.equipBestTool(world.getBlockState(blockTask.blockPos))
            }
            TaskState.PLACE, TaskState.LIQUID_FLOW, TaskState.LIQUID_SOURCE -> {
                val blockID = getIdFromBlock(blockTask.block)
                val noHotbar = InventoryUtils.getSlotsNoHotbar(blockID)

                if (InventoryUtils.getSlotsHotbar(blockID) == null && noHotbar != null) {
                    when (blockTask.block) {
                        fillerMat -> InventoryUtils.moveToSlot(noHotbar[0], 37)
                        material -> InventoryUtils.moveToSlot(noHotbar[0], 38)
                    }
                } else if (InventoryUtils.getSlots(0, 35, blockID) == null) {
                    sendChatMessage("$chatName No ${blockTask.block.localizedName} was found in inventory")
                    mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
                    disable()
                    blockTask.onFailed()
                }

                InventoryUtils.swapSlotToItem(blockID)
            }
            else -> {
                blockTask.onFailed()
            }
        }
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
                        blockTask.updateState(triple.second)
                        blockTask.updateMaterial(triple.third)
                    }
                }
            }
        }
        return foundLiquid
    }

    private fun SafeClientEvent.mineBlock(blockTask: BlockTask) {
        if (blockTask.blockPos == player.flooredPosition.down()) {
            blockTask.updateState(TaskState.DONE)
            return
        }

        /* For fire, we just need to mine the top of the block below the fire */
        /* TODO: This will not work if the top of the block which the fire is on is not visible */
        if (blockTask.block == Blocks.FIRE) {
            val blockBelowFire = blockTask.blockPos.down()
            playerController.clickBlock(blockBelowFire, EnumFacing.UP)
            player.swingArm(EnumHand.MAIN_HAND)
            blockTask.updateState(TaskState.BREAKING)
            return
        }

        val rayTraceResult = rayTraceHitVec(blockTask.blockPos)

        if (rayTraceResult == null) {
            blockTask.onFailed()
            return
        }

        val side = rayTraceResult.sideHit
        lastHitVec = rayTraceResult.hitVec
        rotateTimer.reset()

        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.NETHERRACK -> {
                waitTicks = tickDelayBreak.value
                defaultScope.launch {
                    delay(20L)
                    onMainThreadSafe {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockTask.blockPos, side))
                        player.swingArm(EnumHand.MAIN_HAND)
                    }
                    delay(20L)
                    onMainThreadSafe {
                        connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockTask.blockPos, side))
                        player.swingArm(EnumHand.MAIN_HAND)
                        blockTask.updateState(TaskState.BROKEN)
                    }
                }
            }
            else -> dispatchGenericMineThread(blockTask, side)
        }
    }

    /* Dispatches a thread to mine any non-netherrack blocks generically */
    private fun dispatchGenericMineThread(blockTask: BlockTask, facing: EnumFacing) {
        val action = if (blockTask.taskState == TaskState.BREAKING) {
            CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK
        } else {
            CPacketPlayerDigging.Action.START_DESTROY_BLOCK
        }

        if (blockTask.taskState == TaskState.BREAK || blockTask.taskState == TaskState.EMERGENCY_BREAK) {
            blockTask.updateState(TaskState.BREAKING)
        }

        defaultScope.launch {
            delay(20L)
            onMainThreadSafe {
                connection.sendPacket(CPacketPlayerDigging(action, blockTask.blockPos, facing))
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
    }

    private fun SafeClientEvent.placeBlock(blockTask: BlockTask) {
        val pair = WorldUtils.getNeighbour(blockTask.blockPos, 1, 6.5f)
            ?: run {
                sendChatMessage("Can't find neighbour block")
                blockTask.onFailed()
                return
            }

        if (!isVisible(blockTask.blockPos)) {
            if (illegalPlacements.value) {
                if (debugMessages.value == DebugMessages.ALL) {
                    sendChatMessage("Trying to place through wall ${blockTask.blockPos}")
                }
            } else {
                blockTask.onFailed()
            }
        }

        lastHitVec = WorldUtils.getHitVec(pair.second, pair.first)
        rotateTimer.reset()

        connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))

        defaultScope.launch {
            delay(20L)
            onMainThreadSafe {
                placeBlock(pair.second, pair.first)
            }

            delay(10L)
            onMainThreadSafe {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }
        }
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

    fun printSettings() {
        StringBuilder(ignoreBlocks.size + 1).run {
            append("$chatName Settings" +
                "\n    §9> §rMain material: §7${material.localizedName}" +
                "\n    §9> §rFiller material: §7${fillerMat.localizedName}" +
                "\n    §9> §rIgnored Blocks:")

            for (b in ignoreBlocks) append("\n        §9> §7${b!!.registryName}")

            sendChatMessage(toString())
        }
    }

    private fun printEnable() {
        if (info.value) {
            StringBuilder(2).run {
                append("$chatName Module started." +
                    "\n    §9> §7Direction: §a${startingDirection.displayName}§r")

                if (startingDirection.isDiagonal) {
                    append("\n    §9> §7Coordinates: §a${startingBlockPos.x} ${startingBlockPos.z}§r")
                } else {
                    if (startingDirection == Direction.NORTH || startingDirection == Direction.SOUTH) {
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
                append("\n    §9> §7Distance: §a${startingBlockPos.distanceTo(currentBlockPos).toInt()}§r")

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

    fun gatherStatistics(): List<String> {
        val currentTask = lastTask

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
            "    §7Direction: §9${startingDirection.displayName}",
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
            //"    §7Pathing: §9$pathing",
            "§rEstimations",
            "    §7${material.localizedName} (main material): §9$materialLeft + ($indirectMaterialLeft)",
            "    §7${fillerMat.localizedName} (filler material): §9$fillerMatLeft",
            "    §7Paving distance left: §9$pavingLeftAll",
            //"    §7Estimated destination: §9(${relativeDirection(currentBlockPos, pavingLeft, 0).asString()})",
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
    object StuckManager {
        override fun toString(): String {
            return " "
        }
    }

    class BlockTask(
        val blockPos: BlockPos,
        var taskState: TaskState,
        var block: Block
    ) {
        var ranTicks = 0; private set
        var stuckTicks = 0; private set

        fun updateState(state: TaskState) {
            taskState = state
            onUpdate()
        }

        fun updateMaterial(material: Block) {
            block = material
            onUpdate()
        }

        fun onTick() {
            ranTicks++
        }

        fun onFailed() {
            stuckTicks++
        }

        fun onUpdate() {
            if (taskState == TaskState.DONE) {
                stuckTicks = 0
                ranTicks = 0
            }
        }

        override fun toString(): String {
            return "Block: ${block.localizedName} @ Position: (${blockPos.asString()}) State: ${taskState.name}"
        }

        override fun equals(other: Any?) = this === other
            || (other is BlockTask
            && blockPos == other.blockPos)

        override fun hashCode() =  blockPos.hashCode()
    }

    enum class TaskState(val color: ColorHolder) {
        DONE(ColorHolder(50, 50, 50)),
        LIQUID_SOURCE(ColorHolder(120, 41, 240)),
        LIQUID_FLOW(ColorHolder(120, 41, 240)),
        BROKEN(ColorHolder(111, 0, 0)),
        BREAKING(ColorHolder(240, 222, 60)),
        EMERGENCY_BREAK(ColorHolder(220, 41, 140)),
        BREAK(ColorHolder(222, 0, 0)),
        PLACED(ColorHolder(53, 222, 66)),
        PLACE(ColorHolder(35, 188, 254))
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

