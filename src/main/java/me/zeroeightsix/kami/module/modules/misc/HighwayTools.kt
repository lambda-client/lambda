package me.zeroeightsix.kami.module.modules.misc

import baritone.api.pathing.goals.GoalNear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zeroeightsix.kami.event.Phase
import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.OnUpdateWalkingPlayerEvent
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.event.events.RenderOverlayEvent
import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.player.AutoEat
import me.zeroeightsix.kami.module.modules.player.InventoryManager
import me.zeroeightsix.kami.process.HighwayToolsProcess
import me.zeroeightsix.kami.setting.ModuleConfig.setting
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.EntityUtils.flooredPosition
import me.zeroeightsix.kami.util.WorldUtils.rayTraceBreakVec
import me.zeroeightsix.kami.util.WorldUtils.rayTracePlaceVec
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.*
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString
import me.zeroeightsix.kami.util.math.Direction
import me.zeroeightsix.kami.util.math.RotationUtils
import me.zeroeightsix.kami.util.math.Vec2d
import me.zeroeightsix.kami.util.math.VectorUtils.distanceTo
import me.zeroeightsix.kami.util.math.VectorUtils.multiply
import me.zeroeightsix.kami.util.math.VectorUtils.toVec3d
import me.zeroeightsix.kami.util.math.corners
import me.zeroeightsix.kami.util.text.MessageSendHelper.sendChatMessage
import me.zeroeightsix.kami.util.threads.*
import net.minecraft.block.Block
import net.minecraft.block.Block.getIdFromBlock
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.init.Blocks
import net.minecraft.init.SoundEvents
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.RayTraceResult
import net.minecraft.util.math.Vec3d
import org.kamiblue.commons.extension.ceilToInt
import org.kamiblue.commons.extension.floorToInt
import org.kamiblue.event.listener.listener
import org.lwjgl.opengl.GL11
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

    private val mode by setting("Mode", Mode.HIGHWAY)
    private val page by setting("Page", Page.BUILD)

    // build settings
    private val clearSpace by setting("ClearSpace", true, { page == Page.BUILD && mode == Mode.HIGHWAY })
    private val clearHeight by setting("Height", 4, 1..6, 1, { page == Page.BUILD && clearSpace })
    private val buildWidth by setting("Width", 5, 1..9, 1, { page == Page.BUILD })
    private val railing by setting("Railing", true, { page == Page.BUILD && mode == Mode.HIGHWAY })
    private val railingHeight by setting("RailingHeight", 1, 1..4, 1, { railing && page == Page.BUILD && mode == Mode.HIGHWAY })
    private val cornerBlock by setting("CornerBlock", false, { page == Page.BUILD && (mode == Mode.HIGHWAY || mode == Mode.TUNNEL) })

    // behavior settings
    private val tickDelayPlace by setting("TickDelayPlace", 3, 0..16, 1, { page == Page.BEHAVIOR })
    private val tickDelayBreak by setting("TickDelayBreak", 1, 0..16, 1, { page == Page.BEHAVIOR })
    private val interacting by setting("InteractMode", InteractMode.SPOOF, { page == Page.BEHAVIOR })
    private val illegalPlacements by setting("IllegalPlacements", false, { page == Page.BEHAVIOR })
    private val maxReach by setting("MaxReach", 4.5f, 1.0f..6.0f, 0.1f, { page == Page.BEHAVIOR })
    private val toggleInventoryManager by setting("ToggleInvManager", true, { page == Page.BEHAVIOR })
    private val toggleAutoObsidian by setting("ToggleAutoObsidian", true, { page == Page.BEHAVIOR })

    // config
    private val fakeSounds by setting("Sounds", true, { page == Page.CONFIG })
    private val info by setting("ShowInfo", true, { page == Page.CONFIG })
    private val printDebug by setting("ShowQueue", false, { page == Page.CONFIG })
    private val debugMessages by setting("Debug", DebugMessages.IMPORTANT, { page == Page.CONFIG })
    private val goalRender by setting("GoalRender", false, { page == Page.CONFIG })
    private val filled by setting("Filled", true, { page == Page.CONFIG })
    private val outline by setting("Outline", true, { page == Page.CONFIG })
    private val aFilled by setting("FilledAlpha", 26, 0..255, 1, { filled && page == Page.CONFIG })
    private val aOutline by setting("OutlineAlpha", 91, 0..255, 1, { outline && page == Page.CONFIG })

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
        if (toggleInventoryManager && InventoryManager.isDisabled) InventoryManager.enable()

        /* Turn on Auto Obsidian if the user wants us to control it. */
        if (toggleAutoObsidian && AutoObsidian.isDisabled && mode != Mode.TUNNEL) {
            AutoObsidian.enable()
        }

        startingBlockPos = mc.player.flooredPosition
        currentBlockPos = startingBlockPos
        startingDirection = Direction.fromEntity(mc.player)

        startTime = System.currentTimeMillis()
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0

        baritoneSettingAllowPlace = BaritoneUtils.settings?.allowPlace?.value ?: true
        BaritoneUtils.settings?.allowPlace?.value = false

        if (!goalRender) {
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
        if (!goalRender) BaritoneUtils.settings?.renderGoal?.value = baritoneSettingRenderGoal

        /* Turn off inventory manager if the users wants us to control it */
        if (toggleInventoryManager && InventoryManager.isEnabled) InventoryManager.disable()

        /* Turn off auto obsidian if the user wants us to control it */
        if (toggleAutoObsidian && AutoObsidian.isEnabled) {
            AutoObsidian.disable()
        }

        lastTask = null

        printDisable()
    }

    init {
        safeListener<PacketEvent.Receive> {
            if (it.packet !is SPacketBlockChange) return@safeListener

            val pos = it.packet.blockPosition
            if (!isInsideSelection(pos)) return@safeListener

            val prev = world.getBlockState(pos)
            val new = it.packet.getBlockState()

            if (prev.block != new.block) {
                when {
                    new.block == Blocks.AIR -> {
                        totalBlocksDestroyed++
                        if (fakeSounds) {
                            val soundType = new.block.getSoundType(new, world, pos, player)
                            onMainThread {
                                world.playSound(player, pos, soundType.breakSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
                            }
                        }
                    }
                    prev.block == Blocks.AIR -> {
                        totalBlocksPlaced++
                        if (fakeSounds) {
                            val soundType = prev.block.getSoundType(prev, world, pos, player)
                            onMainThread {
                                world.playSound(player, pos, soundType.placeSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
                            }
                        }
                    }
                }
            }
        }

        safeListener<RenderWorldEvent> {
            renderer.render(false)
        }

        safeListener<OnUpdateWalkingPlayerEvent> {
            if (it.phase != Phase.PRE) return@safeListener

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

        safeListener<RenderOverlayEvent> {
            val taskPos = lastTask?.blockPos ?: currentBlockPos
            val bb = world.getBlockState(taskPos).getSelectedBoundingBox(world, taskPos)
            val vertices = mutableListOf<Vec2d>()
            for (vec in bb.corners(0.90)) {
                vertices.add(Vec2d(ProjectionUtils.toScaledScreenPos(vec)))
            }
            val vertexHelper = VertexHelper(GlStateUtils.useVbo())
            GL11.glDisable(GL11.GL_TEXTURE_2D)
            for (vec in vertices) {
                RenderUtils2D.drawLine(vertexHelper, Vec2d(ProjectionUtils.toScaledScreenPos(player.getPositionEyes(1f))), vec, 2F, ColorHolder(55, 255, 55))
            }
            val hitVec = lastHitVec ?: return@safeListener
            RenderUtils2D.drawLine(vertexHelper, Vec2d(ProjectionUtils.toScaledScreenPos(player.getPositionEyes(1f))), Vec2d(ProjectionUtils.toScaledScreenPos(hitVec)),3F, ColorHolder(255, 55, 55))
            GL11.glEnable(GL11.GL_TEXTURE_2D)
        }
    }

    private fun SafeClientEvent.updateRenderer() {
        renderer.clear()
        renderer.aFilled = if (filled) aFilled else 0
        renderer.aOutline = if (outline) aOutline else 0
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

        when (interacting) {
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

    private fun SafeClientEvent.refreshData(originPos: BlockPos = currentBlockPos) {
        doneTasks.clear()
        pendingTasks.clear()
        lastTask = null

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

    private fun SafeClientEvent.generateBluePrint(feetPos: BlockPos) {
        val basePos = feetPos.down()

        if (mode != Mode.FLAT) {
            val zDirection = startingDirection
            val xDirection = zDirection.clockwise(if (zDirection.isDiagonal) 1 else 2)

            for (x in -maxReach.floorToInt()..maxReach.ceilToInt()) {
                val thisPos = basePos.add(zDirection.directionVec.multiply(x))
                generateClear(thisPos, xDirection)
                if (mode != Mode.TUNNEL) generateBase(thisPos, xDirection)
            }
            if (mode == Mode.TUNNEL) {
                blueprintNew[basePos.add(zDirection.directionVec.multiply(1))] = fillerMat
                blueprintNew[basePos.add(zDirection.directionVec.multiply(2))] = fillerMat
            }

            pickTasksInRange()
        } else {
            generateFlat(basePos)
        }
    }

    private fun SafeClientEvent.pickTasksInRange() {
        val eyePos = player.getPositionEyes(1f)
        blueprintNew.keys.removeIf {
            eyePos.distanceTo(it) - 0.7 > maxReach
        }
    }

    private fun generateClear(basePos: BlockPos, xDirection: Direction) {
        if (!clearSpace) return

        for (w in 0 until buildWidth) {
            for (h in 0 until clearHeight) {
                val x = w - buildWidth / 2
                val pos = basePos.add(xDirection.directionVec.multiply(x)).up(h)

                if (mode == Mode.HIGHWAY && h == 0 && isRail(w)) {
                    continue
                }

                if (mode == Mode.HIGHWAY) {
                    blueprintNew[pos] = Blocks.AIR
                } else {
                    if (!(isRail(w) && h == 0 && !cornerBlock)) blueprintNew[pos.up()] = Blocks.AIR
                }
            }
        }
    }

    private fun generateBase(basePos: BlockPos, xDirection: Direction) {
        for (w in 0 until buildWidth) {
            val x = w - buildWidth / 2
            val pos = basePos.add(xDirection.directionVec.multiply(x))

            if (mode == Mode.HIGHWAY && isRail(w)) {
                val startHeight = if (cornerBlock) 0 else 1
                for (y in startHeight..railingHeight) {
                    blueprintNew[pos.up(y)] = material
                }
            } else {
                blueprintNew[pos] = material
            }
        }
    }

    private fun isRail(w: Int) = railing && w !in 1 until buildWidth - 1

    private fun generateFlat(basePos: BlockPos) {
        // Base
        for (w1 in 0 until buildWidth) {
            for (w2 in 0 until buildWidth) {
                val x = w1 - buildWidth / 2
                val z = w2 - buildWidth / 2
                val pos = basePos.add(x, 0, z)

                blueprintNew[pos] = material
            }
        }

        // Clear
        if (!clearSpace) return
        for (w1 in -buildWidth..buildWidth) {
            for (w2 in -buildWidth..buildWidth) {
                for (y in 1 until clearHeight) {
                    val x = w1 - buildWidth / 2
                    val z = w2 - buildWidth / 2
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

    private fun SafeClientEvent.getNextPos(): BlockPos {
        val baseMaterial = if (mode == Mode.TUNNEL) fillerMat else material
        var lastPos = currentBlockPos

        for (step in 1..2) {
            val pos = currentBlockPos.add(startingDirection.directionVec.multiply(step))

            if (!blueprintNew.containsKey(pos.down()) && mode != Mode.TUNNEL) break
            if (!world.isAirBlock(pos) || !world.isAirBlock(pos.up())) break

            val blockBelow = world.getBlockState(pos.down()).block
            if (blockBelow != baseMaterial && mode != Mode.TUNNEL) break

            if (checkFOMO(pos)) lastPos = pos
        }

        if (currentBlockPos != lastPos) refreshData()
        return lastPos
    }

    private fun checkFOMO(origin: BlockPos): Boolean {
        for (task in pendingTasks) {
            if (task.taskState != TaskState.DONE && origin.distanceTo(task.blockPos) > maxReach) return false
        }
        return true
    }

    private fun SafeClientEvent.runTasks() {
        if (pendingTasks.isNotEmpty()) {
            val sortedTasks = pendingTasks.sortedBy {
                it.taskState.priority +
                    player.getPositionEyes(1f).distanceTo(it.blockPos) * 2 +
                    it.stuckTicks * 10 / it.taskState.stuckTimeout
            }

            (lastTask ?: sortedTasks.firstOrNull())?.let {
                val dist = player.getPositionEyes(1f).distanceTo(it.blockPos) - 0.7
                if (dist > maxReach) {
                    refreshData()
                } else {
                    doNextTask(sortedTasks)
                }
            }
        } else {
            if (checkDoneTasks()) {
                doneTasks.clear()
                refreshData(currentBlockPos.add(startingDirection.directionVec))
            } else {
                refreshData()
            }
        }
    }

    private fun SafeClientEvent.doNextTask(sortedTasks: List<BlockTask>) {
        if (waitTicks > 0) {
            waitTicks--
            return
        }

        lastTask?.let {
            doTask(it)

            if (it.taskState != TaskState.DONE) {
                val timeout = it.taskState.stuckTimeout
                if (it.stuckTicks > timeout) {
                    if (debugMessages == DebugMessages.IMPORTANT) {
                        sendChatMessage("Stuck for more than $timeout ticks, refreshing data.")
                    }
                    refreshData()
                }
                return
            }
        }

        for (task in sortedTasks) {
            doTask(task)
            lastTask = task
            if (task.taskState != TaskState.DONE) break
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
                waitTicks = tickDelayBreak
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
                if (blockTask.block != Blocks.AIR) {
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
            blockTask.block == block && block != Blocks.AIR -> {
                blockTask.updateState(TaskState.DONE)
            }
            blockTask.block == Blocks.AIR && block != Blocks.AIR -> {
                blockTask.updateState(TaskState.BREAK)
            }
            blockTask.block == block && block == Blocks.AIR -> {
                blockTask.updateState(TaskState.BREAK)
            }
            else -> {
                blockTask.updateState(TaskState.PLACE)
            }
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
                    if (debugMessages != DebugMessages.OFF) sendChatMessage("Invalid place position: " + blockTask.blockPos)
                    refreshData()
                    return
                }

                inventoryProcessor(blockTask)

                placeBlock(blockTask)

                if (blockTask.taskState != TaskState.PLACE && isInsideSelection(blockTask.blockPos)) {
                    blockTask.updateMaterial(Blocks.AIR)
                }

                blockTask.updateState(TaskState.PLACED)

                waitTicks = tickDelayPlace
            }
        }
    }

    private fun SafeClientEvent.checkDoneTasks(): Boolean {
        for (blockTask in doneTasks) {
            val block = world.getBlockState(blockTask.blockPos).block
            if (ignoreBlocks.contains(block)) continue

            when {
                blockTask.block == material && block != material -> return false
                mode == Mode.TUNNEL && blockTask.block == fillerMat && block != fillerMat -> return false
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
                    blockTask.onStuck()
                }

                InventoryUtils.swapSlotToItem(blockID)
            }
            else -> {
                blockTask.onStuck()
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

                if (player.distanceTo(neighbour) > maxReach) continue

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

        val rayTraceResult = rayTraceBreakVec(blockTask.blockPos)

        if (rayTraceResult == null) {
            blockTask.onStuck()
            return
        }

        val side = rayTraceResult.sideHit
        lastHitVec = rayTraceResult.hitVec
        rotateTimer.reset()

        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.NETHERRACK -> dispatchInstantBreakThread(blockTask, side)
            else -> dispatchGenericMineThread(blockTask, side)
        }
    }

    private fun dispatchInstantBreakThread(blockTask: BlockTask, facing: EnumFacing) {
        waitTicks = tickDelayBreak
        defaultScope.launch {
            delay(10L)
            onMainThreadSafe {
                connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, blockTask.blockPos, facing))
                player.swingArm(EnumHand.MAIN_HAND)
            }
            delay(40L)
            onMainThreadSafe {
                connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, blockTask.blockPos, facing))
                player.swingArm(EnumHand.MAIN_HAND)
                blockTask.updateState(TaskState.BROKEN)
            }
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
            delay(10L)
            onMainThreadSafe {
                connection.sendPacket(CPacketPlayerDigging(action, blockTask.blockPos, facing))
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
    }

    private fun SafeClientEvent.placeBlock(blockTask: BlockTask) {
        val rayTraceResult = rayTracePlaceVec(blockTask.blockPos)

        if (rayTraceResult == null) {
            if (illegalPlacements) {
                if (debugMessages == DebugMessages.ALL) {
                    sendChatMessage("Trying to place through wall ${blockTask.blockPos}")
                }
            } else {
                blockTask.onStuck()
            }
            return
        }

        lastHitVec = rayTraceResult.hitVec
        rotateTimer.reset()

        connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))

        defaultScope.launch {
            delay(10L)
            onMainThreadSafe {
                val offsetHitVec = lastHitVec ?: return@onMainThreadSafe
                val placePacket = CPacketPlayerTryUseItemOnBlock(rayTraceResult.blockPos, rayTraceResult.sideHit, EnumHand.MAIN_HAND, offsetHitVec.x.toFloat(), offsetHitVec.y.toFloat(), offsetHitVec.z.toFloat())
                connection.sendPacket(placePacket)
                player.swingArm(EnumHand.MAIN_HAND)
            }

            delay(10L)
            onMainThreadSafe {
                connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
            }
        }
    }

    private fun isInsideSelection(pos: BlockPos): Boolean {
        return blueprintNew.containsKey(pos)
    }

    private fun isInsideBuild(pos: BlockPos): Boolean {
        return blueprintNew[pos]?.let { it != Blocks.AIR } ?: false
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
        if (info) {
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
                if (startingBlockPos.y in 117..119 && mode != Mode.TUNNEL) append("\n    §9> §cCheck coordinate Y / altitude and make sure to move around Y 120 for the correct height")
                sendChatMessage(toString())
            }
        }
    }

    private fun printDisable() {
        if (info) {
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

    private fun getBlueprintStats(): Pair<Int, Int> {
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
            "    §7Stuck ticks: §9${lastTask?.stuckTicks?.toString() ?: "N/A"}",
            //"    §7Pathing: §9$pathing",
            "§rEstimations",
            "    §7${material.localizedName} (main material): §9$materialLeft + ($indirectMaterialLeft)",
            "    §7${fillerMat.localizedName} (filler material): §9$fillerMatLeft",
            "    §7Paving distance left: §9$pavingLeftAll",
            //"    §7Estimated destination: §9(${relativeDirection(currentBlockPos, pavingLeft, 0).asString()})",
            "    §7ETA: §9$hoursLeft:$minutesLeft:$secondsLeft"
        )

        if (printDebug) {
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

    class BlockTask(
        val blockPos: BlockPos,
        var taskState: TaskState,
        var block: Block
    ) {
        private var ranTicks = 0
        var stuckTicks = 0; private set

        fun updateState(state: TaskState) {
            if (state == taskState) return
            taskState = state
            onUpdate()
        }

        fun updateMaterial(material: Block) {
            if (material == block) return
            block = material
            onUpdate()
        }

        fun onTick() {
            ranTicks++
            if (ranTicks > taskState.stuckThreshold) {
                stuckTicks++
            }
        }

        fun onStuck() {
            stuckTicks++
        }

        private fun onUpdate() {
            stuckTicks = 0
            ranTicks = 0
        }

        override fun toString(): String {
            return "Block: ${block.localizedName} @ Position: (${blockPos.asString()}) State: ${taskState.name}"
        }

        override fun equals(other: Any?) = this === other
            || (other is BlockTask
            && blockPos == other.blockPos)

        override fun hashCode() = blockPos.hashCode()
    }

    enum class TaskState(val priority: Int, val stuckThreshold: Int, val stuckTimeout: Int, val color: ColorHolder) {
        DONE(0, 69420, 0x22, ColorHolder(50, 50, 50)),
        LIQUID_SOURCE(1, 100, 100, ColorHolder(120, 41, 240)),
        LIQUID_FLOW(20, 80, 80, ColorHolder(120, 41, 240)),
        BROKEN(120, 20, 10, ColorHolder(111, 0, 0)),
        BREAKING(140, 100, 100, ColorHolder(240, 222, 60)),
        EMERGENCY_BREAK(160, 20, 20, ColorHolder(220, 41, 140)),
        BREAK(180, 20, 20, ColorHolder(222, 0, 0)),
        PLACED(280, 20, 5, ColorHolder(53, 222, 66)),
        PLACE(300, 20, 10, ColorHolder(35, 188, 254))
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

}

