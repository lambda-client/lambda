package me.zeroeightsix.kami.module.modules.misc

import baritone.api.pathing.goals.GoalNear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import me.zeroeightsix.kami.event.SafeClientEvent
import me.zeroeightsix.kami.event.events.PacketEvent
import me.zeroeightsix.kami.event.events.RenderWorldEvent
import me.zeroeightsix.kami.manager.managers.PlayerPacketManager
import me.zeroeightsix.kami.module.Category
import me.zeroeightsix.kami.module.Module
import me.zeroeightsix.kami.module.modules.player.AutoEat
import me.zeroeightsix.kami.module.modules.player.InventoryManager
import me.zeroeightsix.kami.process.HighwayToolsProcess
import me.zeroeightsix.kami.util.*
import me.zeroeightsix.kami.util.EntityUtils.flooredPosition
import me.zeroeightsix.kami.util.WorldUtils.blackList
import me.zeroeightsix.kami.util.WorldUtils.getMiningSide
import me.zeroeightsix.kami.util.WorldUtils.getNeighbour
import me.zeroeightsix.kami.util.WorldUtils.isPlaceable
import me.zeroeightsix.kami.util.color.ColorHolder
import me.zeroeightsix.kami.util.graphics.*
import me.zeroeightsix.kami.util.items.*
import me.zeroeightsix.kami.util.math.*
import me.zeroeightsix.kami.util.math.CoordinateConverter.asString
import me.zeroeightsix.kami.util.math.RotationUtils.getRotationTo
import me.zeroeightsix.kami.util.math.VectorUtils.distanceTo
import me.zeroeightsix.kami.util.math.VectorUtils.multiply
import me.zeroeightsix.kami.util.text.MessageSendHelper
import me.zeroeightsix.kami.util.threads.*
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.SoundEvents
import net.minecraft.item.ItemBlock
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.commons.extension.ceilToInt
import org.kamiblue.commons.extension.floorToInt
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.LinkedHashMap

/**
 * @author Avanatiker
 * @since 20/08/2020
 */
internal object HighwayTools : Module(
    name = "HighwayTools",
    description = "Be the grief a step a head.",
    category = Category.MISC,
    modulePriority = 10
) {
    private val mode by setting("Mode", Mode.HIGHWAY)
    private val page by setting("Page", Page.BUILD)

    // build settings
    private val clearSpace by setting("Clear Space", true, { page == Page.BUILD && mode == Mode.HIGHWAY })
    private val height by setting("Height", 4, 1..6, 1, { page == Page.BUILD && clearSpace })
    private val width by setting("Width", 5, 1..9, 1, { page == Page.BUILD })
    private val railing by setting("Railing", true, { page == Page.BUILD && mode == Mode.HIGHWAY })
    private val railingHeight by setting("Railing Height", 1, 1..4, 1, { railing && page == Page.BUILD && mode == Mode.HIGHWAY })
    private val cornerBlock by setting("Corner Block", false, { page == Page.BUILD && (mode == Mode.HIGHWAY || mode == Mode.TUNNEL) })

    // behavior settings
    private val interacting by setting("Rotation Mode", RotationMode.SPOOF, { page == Page.BEHAVIOR })
    private val fastBreak by setting("Fast Break", false, { page == Page.BEHAVIOR })
    private val illegalPlacements by setting("Illegal Placements", false, { page == Page.BEHAVIOR })
    private val toggleInventoryManager by setting("Toggle InvManager", true, { page == Page.BEHAVIOR })
    private val toggleAutoObsidian by setting("Toggle AutoObsidian", true, { page == Page.BEHAVIOR })
    private val placeDelay by setting("Place Delay", 2, 1..20, 1, { page == Page.BEHAVIOR })
    private val breakDelay by setting("Break Delay", 1, 1..20, 1, { page == Page.BEHAVIOR })
    private val maxBreaks by setting("Multi Break", 1, 1..8, 1, { page == Page.BEHAVIOR })
    private val taskTimeout by setting("Task Timeout", 5, 0..20, 1, { page == Page.BEHAVIOR })
    private val rubberbandTimeout by setting("Rubberband Timeout", 50, 5..100, 5, { page == Page.BEHAVIOR })
    private val maxReach by setting("MaxReach", 4.5f, 1.0f..6.0f, 0.1f, { page == Page.BEHAVIOR })

    // config
    private val fakeSounds by setting("Fake Sounds", true, { page == Page.CONFIG })
    private val info by setting("Show Info", true, { page == Page.CONFIG })
    private val printDebug by setting("Show Queue", false, { page == Page.CONFIG })
    private val debugMessages by setting("Debug Messages", DebugMessages.IMPORTANT, { page == Page.CONFIG })
    private val goalRender by setting("Goal Render", false, { page == Page.CONFIG })
    private val filled by setting("Filled", true, { page == Page.CONFIG })
    private val outline by setting("Outline", true, { page == Page.CONFIG })
    private val aFilled by setting("FilledAlpha", 26, 0..255, 1, { filled && page == Page.CONFIG })
    private val aOutline by setting("OutlineAlpha", 91, 0..255, 1, { outline && page == Page.CONFIG })

    private enum class Mode {
        HIGHWAY, FLAT, TUNNEL
    }

    private enum class Page {
        BUILD, BEHAVIOR, CONFIG
    }

    @Suppress("UNUSED")
    private enum class RotationMode {
        OFF, SPOOF, VIEW_LOCK
    }

    private enum class DebugMessages {
        OFF, IMPORTANT, ALL
    }

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
    private val blueprint = LinkedHashMap<BlockPos, Block>()

    // State
    private val rubberbandTimer = TickTimer(TimeUnit.TICKS)
    private var active = false
    private var waitTicks = 0

    // Rotation
    private var lastHitVec = Vec3d.ZERO
    private val rotateTimer = TickTimer(TimeUnit.TICKS)

    // Pathing
    var goal: GoalNear? = null; private set

    // Tasks
    private val pendingTasks = LinkedHashMap<BlockPos, BlockTask>()
    private val doneTasks = LinkedHashMap<BlockPos, BlockTask>()
    private var sortedTasks: List<BlockTask> = emptyList()
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

    init {
        onEnable {
            runSafeR {
                /* Turn on inventory manager if the users wants us to control it */
                if (toggleInventoryManager && InventoryManager.isDisabled) {
                    InventoryManager.enable()
                }

                /* Turn on Auto Obsidian if the user wants us to control it. */
                if (toggleAutoObsidian && AutoObsidian.isDisabled && mode != Mode.TUNNEL) {
                    AutoObsidian.enable()
                }

                startingBlockPos = Companion.mc.player.flooredPosition
                currentBlockPos = startingBlockPos
                startingDirection = Direction.fromEntity(Companion.mc.player)

                startTime = System.currentTimeMillis()
                totalBlocksPlaced = 0
                totalBlocksDestroyed = 0

                baritoneSettingAllowPlace = BaritoneUtils.settings?.allowPlace?.value ?: true
                BaritoneUtils.settings?.allowPlace?.value = false

                if (!goalRender) {
                    baritoneSettingRenderGoal = BaritoneUtils.settings?.renderGoal?.value ?: true
                    BaritoneUtils.settings?.renderGoal?.value = false
                }

                refreshData()
                printEnable()
            } ?: disable()
        }

        onDisable {
            runSafe {
                /* Turn off inventory manager if the users wants us to control it */
                if (toggleInventoryManager && InventoryManager.isEnabled) {
                    InventoryManager.disable()
                }

                /* Turn off auto obsidian if the user wants us to control it */
                if (toggleAutoObsidian && AutoObsidian.isEnabled) {
                    AutoObsidian.disable()
                }

                BaritoneUtils.settings?.allowPlace?.value = baritoneSettingAllowPlace
                if (!goalRender) BaritoneUtils.settings?.renderGoal?.value = baritoneSettingRenderGoal

                active = false
                goal = null
                lastTask = null

                printDisable()
            }
        }
    }

    private fun printEnable() {
        if (info) {
            MessageSendHelper.sendRawChatMessage("    §9> §7Direction: §a${startingDirection.displayName}§r")

            if (startingDirection.isDiagonal) {
                MessageSendHelper.sendRawChatMessage("    §9> §7Coordinates: §a${startingBlockPos.x} ${startingBlockPos.z}§r")
            } else {
                if (startingDirection == Direction.NORTH || startingDirection == Direction.SOUTH) {
                    MessageSendHelper.sendRawChatMessage("    §9> §7Coordinate: §a${startingBlockPos.x}§r")
                } else {
                    MessageSendHelper.sendRawChatMessage("    §9> §7Coordinate: §a${startingBlockPos.z}§r")
                }
            }

            if (startingBlockPos.y in 117..119 && mode != Mode.TUNNEL) {
                MessageSendHelper.sendRawChatMessage("    §9> §cCheck coordinate Y / altitude and make sure to move around Y 120 for the correct height")
            }
        }
    }

    private fun printDisable() {
        if (info) {
            MessageSendHelper.sendRawChatMessage("    §9> §7Placed blocks: §a$totalBlocksPlaced§r")
            MessageSendHelper.sendRawChatMessage("    §9> §7Destroyed blocks: §a$totalBlocksDestroyed§r")
            MessageSendHelper.sendRawChatMessage("    §9> §7Distance: §a${startingBlockPos.distanceTo(currentBlockPos).toInt()}§r")
        }
    }

    init {
        safeListener<PacketEvent.Receive> {
            when (it.packet) {
                is SPacketBlockChange -> {
                    val pos = it.packet.blockPosition
                    if (!isInsideBlueprint(pos)) return@safeListener

                    val prev = world.getBlockState(pos)
                    val new = it.packet.getBlockState()

                    if (prev.block != new.block) {
                        val task = pendingTasks[pos] ?: return@safeListener

                        when {
                            task.taskState == TaskState.PENDING_BROKEN &&
                                prev.block != Blocks.AIR &&
                                new.block == Blocks.AIR -> {
                                task.updateState(TaskState.BROKEN)
                            }
                            task.taskState == TaskState.PENDING_PLACED &&
                                (task.block == material || task.block == fillerMat)
                                && task.block == new.block -> {
                                task.updateState(TaskState.PLACED)
                            }
                        }
                    }
                }
                is SPacketPlayerPosLook -> {
                    rubberbandTimer.reset()
                }
            }
        }

        safeListener<RenderWorldEvent> {
            renderer.render(false)
        }

        safeListener<TickEvent.ClientTickEvent> { event ->
            if (event.phase != TickEvent.Phase.START) return@safeListener

            updateRenderer()
            updateFood()

            if (!rubberbandTimer.tick(rubberbandTimeout.toLong(), false)) {
                return@safeListener
            }

            if (player.flooredPosition.distanceTo(currentBlockPos) > 2 || AutoObsidian.isActive() || AutoEat.eating) {
                return@safeListener
            }

            if (!active) {
                active = true
                BaritoneUtils.primary?.pathingControlManager?.registerProcess(HighwayToolsProcess)
            }

            doPathing()
            runTasks()

            doRotation()
        }
    }

    private fun SafeClientEvent.updateRenderer() {
        renderer.clear()
        renderer.aFilled = if (filled) aFilled else 0
        renderer.aOutline = if (outline) aOutline else 0

        for (blockTask in pendingTasks.values) {
            if (blockTask.taskState == TaskState.DONE) continue
            renderer.add(world.getBlockState(blockTask.blockPos).getSelectedBoundingBox(world, blockTask.blockPos), blockTask.taskState.color)
        }

        for (blockTask in doneTasks.values) {
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
        val rotation = lastHitVec?.let { getRotationTo(it) } ?: return

        when (interacting) {
            RotationMode.SPOOF -> {
                val packet = PlayerPacketManager.PlayerPacket(rotating = true, rotation = rotation)
                PlayerPacketManager.addPacket(this@HighwayTools, packet)
            }
            RotationMode.VIEW_LOCK -> {
                player.rotationYaw = rotation.x
                player.rotationPitch = rotation.y
            }
            else -> {
                // RotationMode.OFF
            }
        }
    }

    private fun SafeClientEvent.refreshData(originPos: BlockPos = currentBlockPos) {
        doneTasks.clear()
        pendingTasks.clear()
        lastTask = null

        blueprint.clear()
        generateBluePrint(originPos)

        for ((pos, block) in blueprint) {
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
                for (x in 1..maxReach.floorToInt()) {
                    blueprint[basePos.add(zDirection.directionVec.multiply(x))] = fillerMat
                }
            }

            pickTasksInRange()
        } else {
            generateFlat(basePos)
        }
    }

    private fun SafeClientEvent.pickTasksInRange() {
        val eyePos = player.getPositionEyes(1f)

        blueprint.keys.removeIf {
            eyePos.distanceTo(it) > maxReach - 0.7
        }
    }

    private fun generateClear(basePos: BlockPos, xDirection: Direction) {
        if (!clearSpace) return

        for (w in 0 until width) {
            for (h in 0 until height) {
                val x = w - width / 2
                val pos = basePos.add(xDirection.directionVec.multiply(x)).up(h)

                if (mode == Mode.HIGHWAY && h == 0 && isRail(w)) {
                    continue
                }

                if (mode == Mode.HIGHWAY) {
                    blueprint[pos] = Blocks.AIR
                } else {
                    if (!(isRail(w) && h == 0 && !cornerBlock)) blueprint[pos.up()] = Blocks.AIR
                }
            }
        }
    }

    private fun generateBase(basePos: BlockPos, xDirection: Direction) {
        for (w in 0 until width) {
            val x = w - width / 2
            val pos = basePos.add(xDirection.directionVec.multiply(x))

            if (mode == Mode.HIGHWAY && isRail(w)) {
                val startHeight = if (cornerBlock) 0 else 1
                for (y in startHeight..railingHeight) {
                    blueprint[pos.up(y)] = material
                }
            } else {
                blueprint[pos] = material
            }
        }
    }

    private fun isRail(w: Int) = railing && w !in 1 until width - 1

    private fun generateFlat(basePos: BlockPos) {
        // Base
        for (w1 in 0 until width) {
            for (w2 in 0 until width) {
                val x = w1 - width / 2
                val z = w2 - width / 2
                val pos = basePos.add(x, 0, z)

                blueprint[pos] = material
            }
        }

        // Clear
        if (!clearSpace) return
        for (w1 in -width..width) {
            for (w2 in -width..width) {
                for (y in 1 until height) {
                    val x = w1 - width / 2
                    val z = w2 - width / 2
                    val pos = basePos.add(x, y, z)

                    blueprint[pos] = Blocks.AIR
                }
            }
        }
    }

    private fun addTaskToPending(blockPos: BlockPos, taskState: TaskState, material: Block) {
        pendingTasks[blockPos] = (BlockTask(blockPos, taskState, material))
    }

    private fun addTaskToDone(blockPos: BlockPos, material: Block) {
        doneTasks[blockPos] = (BlockTask(blockPos, TaskState.DONE, material))
    }

    private fun SafeClientEvent.doPathing() {
        val nextPos = getNextPos()

        if (player.flooredPosition.distanceTo(nextPos) < 2.0) {
            currentBlockPos = nextPos
        }

        goal = GoalNear(nextPos, 0)
    }

    private fun SafeClientEvent.getNextPos(): BlockPos {
        var nextPos = currentBlockPos

        val possiblePos = currentBlockPos.add(startingDirection.directionVec)

        if (!isTaskDoneOrNull(possiblePos) ||
            !isTaskDoneOrNull(possiblePos.up()) ||
            !isTaskDoneOrNull(possiblePos.down())) return nextPos

        if (checkTasks(possiblePos.up())) nextPos = possiblePos

        if (currentBlockPos != nextPos) refreshData()

        return nextPos
    }

    private fun isTaskDoneOrNull(pos: BlockPos) =
        (pendingTasks[pos] ?: doneTasks[pos])?.let {
            it.taskState == TaskState.DONE
        } ?: true

    private fun checkTasks(pos: BlockPos): Boolean {
        return pendingTasks.values.all {
            it.taskState == TaskState.DONE || pos.distanceTo(it.blockPos) < maxReach - 1.0
        }
    }

    private fun SafeClientEvent.runTasks() {
        if (pendingTasks.isEmpty()) {
            if (checkDoneTasks()) {
                doneTasks.clear()
                refreshData(currentBlockPos.add(startingDirection.directionVec))
            } else {
                refreshData()
            }
        } else {
            waitTicks--
            for (task in pendingTasks.values) {
                doTask(task, true)
            }
            sortTasks()

            for (task in sortedTasks) {
                if (!checkStuckTimeout(task)) return
                if (task.taskState != TaskState.DONE && waitTicks > 0) return

                doTask(task, false)

                when (task.taskState) {
                    TaskState.DONE, TaskState.BROKEN, TaskState.PLACED -> {
                        continue
                    }
                    else -> {
                        break
                    }
                }
            }
        }
    }

    private fun SafeClientEvent.checkDoneTasks(): Boolean {
        for (blockTask in doneTasks.values) {
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

    private fun SafeClientEvent.sortTasks() {
        val eyePos = mc.player.getPositionEyes(1.0f)

        sortedTasks = pendingTasks.values.sortedWith(
            compareBy<BlockTask> {
                it.taskState.ordinal
            }.thenBy {
                it.stuckTicks / 2
            }.thenBy {
                eyePos.distanceTo(it.blockPos)
            }.thenBy {
                lastHitVec?.distanceTo(it.blockPos)
            }
        )
    }

    private fun SafeClientEvent.checkStuckTimeout(blockTask: BlockTask): Boolean {
        val timeout = blockTask.taskState.stuckTimeout

        if (blockTask.stuckTicks > timeout) {
            when (blockTask.taskState) {
                TaskState.PENDING_BROKEN -> {
                    blockTask.updateState(TaskState.BREAK)
                }
                TaskState.PENDING_PLACED -> {
                    blockTask.updateState(TaskState.PLACE)
                }
                else -> {
                    if (debugMessages != DebugMessages.OFF) {
                        MessageSendHelper.sendChatMessage("Stuck while ${blockTask.taskState}@(${blockTask.blockPos.asString()}) for more then $timeout ticks (${blockTask.stuckTicks}), refreshing data.")
                    }
                    refreshData()
                    return false
                }
            }
        }

        return true
    }

    private fun SafeClientEvent.doTask(blockTask: BlockTask, update: Boolean) {
        if (!update) blockTask.onTick()

        when (blockTask.taskState) {
            TaskState.DONE -> {
                doDone(blockTask, update)
            }
            TaskState.BREAKING -> {
                doBreaking(blockTask, update)
            }
            TaskState.BROKEN -> {
                doBroken(blockTask, update)
            }
            TaskState.PLACED -> {
                doPlaced(blockTask, update)
            }
            TaskState.BREAK -> {
                doBreak(blockTask, update)
            }
            TaskState.PLACE, TaskState.LIQUID_SOURCE, TaskState.LIQUID_FLOW -> {
                doPlace(blockTask, update)
            }
            TaskState.PENDING_BROKEN, TaskState.PENDING_PLACED -> {
                if (!update && debugMessages == DebugMessages.ALL) {
                    MessageSendHelper.sendChatMessage("$chatName Currently waiting for blockState updates...")
                }
                blockTask.onStuck()
            }
        }
    }

    private fun doDone(blockTask: BlockTask, update: Boolean) {
        if (update) {
            pendingTasks[blockTask.blockPos]
            doneTasks[blockTask.blockPos] = blockTask
        }
    }

    private fun SafeClientEvent.doBreaking(blockTask: BlockTask, update: Boolean) {
        if (update) {
            when (world.getBlockState(blockTask.blockPos).block) {
                Blocks.AIR -> {
                    waitTicks = breakDelay
                    blockTask.updateState(TaskState.BROKEN)
                }
                is BlockLiquid -> {
                    val filler = if (fillerMatLeft == 0 || isInsideBlueprintBuild(blockTask.blockPos)) {
                        material
                    } else {
                        fillerMat
                    }

                    if (world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                        blockTask.updateState(TaskState.LIQUID_FLOW)
                        blockTask.updateMaterial(filler)
                    } else {
                        blockTask.updateState(TaskState.LIQUID_SOURCE)
                        blockTask.updateMaterial(filler)
                    }
                }
            }
        } else {
            mineBlock(blockTask)
        }
    }

    private fun SafeClientEvent.doBroken(blockTask: BlockTask, update: Boolean) {
        if (update) {
            when (world.getBlockState(blockTask.blockPos).block) {
                Blocks.AIR -> {
                    totalBlocksDestroyed++

                    if (blockTask.block == Blocks.AIR) {
                        if (fakeSounds) {
                            val soundType = blockTask.block.getSoundType(world.getBlockState(blockTask.blockPos), world, blockTask.blockPos, player)
                            world.playSound(player, blockTask.blockPos, soundType.breakSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
                        }
                        blockTask.updateState(TaskState.DONE)
                    } else {
                        blockTask.updateState(TaskState.PLACE)
                    }
                }
                else -> {
                    blockTask.updateState(TaskState.BREAK)
                }
            }
        }
    }

    private fun SafeClientEvent.doPlaced(blockTask: BlockTask, update: Boolean) {
        if (update) {
            val currentBlock = world.getBlockState(blockTask.blockPos).block

            when {
                blockTask.block == currentBlock && currentBlock != Blocks.AIR -> {
                    blockTask.updateState(TaskState.DONE)
                    if (fakeSounds) {
                        val soundType = currentBlock.getSoundType(world.getBlockState(blockTask.blockPos), world, blockTask.blockPos, player)
                        world.playSound(player, blockTask.blockPos, soundType.placeSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
                    }
                    totalBlocksPlaced++
                }
                blockTask.block == currentBlock && currentBlock == Blocks.AIR -> {
                    blockTask.updateState(TaskState.BREAK)
                }
                blockTask.block == Blocks.AIR && currentBlock != Blocks.AIR -> {
                    blockTask.updateState(TaskState.BREAK)
                }
                else -> {
                    blockTask.updateState(TaskState.PLACE)
                }
            }
        }
    }

    private fun SafeClientEvent.doBreak(blockTask: BlockTask, update: Boolean) {
        // ignore blocks
        if (blockTask.block != Blocks.AIR && ignoreBlocks.contains(blockTask.block)) {
            blockTask.updateState(TaskState.DONE)
        }

        if (update) {
            when (world.getBlockState(blockTask.blockPos).block) {
                Blocks.AIR -> {
                    if (blockTask.block == Blocks.AIR) {
                        blockTask.updateState(TaskState.BROKEN)
                    } else {
                        blockTask.updateState(TaskState.PLACE)
                    }
                }
                is BlockLiquid -> {
                    val filler = if (fillerMatLeft == 0 || isInsideBlueprintBuild(blockTask.blockPos)) material
                    else fillerMat

                    if (world.getBlockState(blockTask.blockPos).getValue(BlockLiquid.LEVEL) != 0) {
                        blockTask.updateState(TaskState.LIQUID_FLOW)
                        blockTask.updateMaterial(filler)
                    } else {
                        blockTask.updateState(TaskState.LIQUID_SOURCE)
                        blockTask.updateMaterial(filler)
                    }
                }
            }
        } else {
            if (handleLiquid(blockTask)) return
            swapOrMoveBestTool(blockTask)
            mineBlock(blockTask)
        }
    }

    private fun SafeClientEvent.doPlace(blockTask: BlockTask, update: Boolean) {
        if (update) {
            val currentBlock = world.getBlockState(blockTask.blockPos).block

            when(blockTask.block) {
                material -> {
                    if (currentBlock == material) {
                        blockTask.updateState(TaskState.PLACED)
                    } else if (currentBlock != Blocks.AIR) {
                        blockTask.updateState(TaskState.BREAK)
                    }
                }
                fillerMat -> {
                    if (currentBlock != Blocks.AIR && currentBlock !is BlockLiquid) {
                        blockTask.updateState(TaskState.PLACED)
                    }
                }
                Blocks.AIR -> {
                    if (currentBlock != Blocks.AIR) {
                        blockTask.updateState(TaskState.BREAK)
                    } else {
                        blockTask.updateState(TaskState.BROKEN)
                    }
                }
            }

        } else {
            if (!isPlaceable(blockTask.blockPos)) {
                if (debugMessages != DebugMessages.OFF) MessageSendHelper.sendChatMessage("Invalid place position: ${blockTask.blockPos}. Removing task")
                pendingTasks.remove(blockTask.blockPos)
                return
            }

            if (!swapOrMoveBlock(blockTask)) {
                blockTask.onStuck()
                return
            }

            placeBlock(blockTask)
        }
    }

    private fun SafeClientEvent.swapOrMoveBlock(blockTask: BlockTask): Boolean {
        val success = swapToBlockOrMove(blockTask.block, predicateSlot = {
            it.item is ItemBlock
        })

        return if (!success) {
            MessageSendHelper.sendChatMessage("$chatName No ${blockTask.block.localizedName} was found in inventory")
            mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            false
        } else {
            true
        }
    }

    private fun SafeClientEvent.placeBlock(blockTask: BlockTask) {
        val pair = getNeighbour(blockTask.blockPos, 1, maxReach, true)
            ?: run {
                if (illegalPlacements) {
                    if (debugMessages == DebugMessages.ALL) {
                        MessageSendHelper.sendChatMessage("Trying to place through wall ${blockTask.blockPos}")
                    }
                    getNeighbour(blockTask.blockPos, 1, maxReach) ?: return
                } else {
                    blockTask.onStuck()
                    return
                }
            }

        lastHitVec = WorldUtils.getHitVec(pair.second, pair.first)
        rotateTimer.reset()

        placeBlockNormal(blockTask, pair)
    }

    private fun SafeClientEvent.placeBlockNormal(blockTask: BlockTask, pair: Pair<EnumFacing, BlockPos>) {
        val hitVecOffset = WorldUtils.getHitVecOffset(pair.first)
        val currentBlock = world.getBlockState(pair.second).block

        waitTicks = placeDelay
        blockTask.updateState(TaskState.PENDING_PLACED)

        if (currentBlock in blackList) {
            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
        }

        defaultScope.launch {
            delay(20L)
            onMainThreadSafe {
                val placePacket = CPacketPlayerTryUseItemOnBlock(pair.second, pair.first, EnumHand.MAIN_HAND, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat())
                connection.sendPacket(placePacket)
                player.swingArm(EnumHand.MAIN_HAND)
            }

            if (currentBlock in blackList) {
                delay(20L)
                onMainThreadSafe {
                    connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.STOP_SNEAKING))
                }
            }

            delay(50L * taskTimeout)
            if (blockTask.taskState == TaskState.PENDING_PLACED) {
                blockTask.updateState(TaskState.PLACE)
            }
        }
    }

    private fun SafeClientEvent.swapOrMoveBestTool(blockTask: BlockTask): Boolean {
        val slotFrom = player.inventorySlots.asReversed().maxByOrNull {
            val stack = it.stack
            if (stack.isEmpty) {
                0.0f
            } else {
                var speed = stack.getDestroySpeed(world.getBlockState(blockTask.blockPos))

                if (speed > 1.0f) {
                    val efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack)
                    if (efficiency > 0) {
                        speed += efficiency * efficiency + 1.0f
                    }
                }

                speed
            }
        }

        return if (slotFrom != null) {
            slotFrom.toHotbarSlotOrNull()?.let {
                swapToSlot(it)
            } ?: run {
                val slotTo = player.hotbarSlots.firstEmpty()?.hotbarSlot ?: 0
                moveToHotbar(slotFrom.slotNumber, slotTo)
            }
            true
        } else {
            false
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
                val filler = if (isInsideBlueprintBuild(neighbour)) material else fillerMat

                for (task in pendingTasks.values) {
                    if (task.blockPos == neighbour) {
                        when (isFlowing) {
                            false -> found.add(Triple(task, TaskState.LIQUID_SOURCE, filler))
                            true -> found.add(Triple(task, TaskState.LIQUID_FLOW, filler))
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
        /* ToDo: This will not work if the top of the block which the fire is on is not visible */
        if (blockTask.block == Blocks.FIRE) {
            val blockBelowFire = blockTask.blockPos.down()
            playerController.clickBlock(blockBelowFire, EnumFacing.UP)
            player.swingArm(EnumHand.MAIN_HAND)
            blockTask.updateState(TaskState.BREAKING)
            return
        }

        val side = getMiningSide(blockTask.blockPos) ?: run {
            blockTask.onStuck()
            return
        }

        lastHitVec = WorldUtils.getHitVec(blockTask.blockPos, side)
        rotateTimer.reset()

        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.NETHERRACK -> mineBlockInstant(blockTask, side)
            else -> mineBlockNormal(blockTask, side)
        }
    }

    private fun mineBlockInstant(blockTask: BlockTask, side: EnumFacing) {
        waitTicks = breakDelay
        blockTask.updateState(TaskState.PENDING_BROKEN)

        defaultScope.launch {
            delay(20L)
            sendMiningPackets(blockTask.blockPos, side)

            if (maxBreaks > 1) {
                tryMultiBreak(blockTask)
            }

            delay(50L * taskTimeout)
            if (blockTask.taskState == TaskState.PENDING_BROKEN) {
                blockTask.updateState(TaskState.BREAK)
            }
        }
    }

    private fun tryMultiBreak(blockTask: BlockTask) {
        runSafe {
            val eyePos = player.getPositionEyes(1.0f)
            val viewVec = lastHitVec.subtract(eyePos).normalize()
            var breakCount = 1

            for (task in sortedTasks) {
                if (breakCount >= maxBreaks) break

                if (task == blockTask) continue
                if (task.taskState != TaskState.BREAK) continue
                if (world.getBlockState(task.blockPos).block != Blocks.NETHERRACK) continue

                val box = AxisAlignedBB(task.blockPos)
                val rayTraceResult = box.isInSight(eyePos, viewVec) ?: continue
                breakCount++

                defaultScope.launch {
                    sendMiningPackets(task.blockPos, rayTraceResult.sideHit)

                    delay(50L * taskTimeout)
                    if (blockTask.taskState == TaskState.PENDING_BROKEN) {
                        blockTask.updateState(TaskState.BREAK)
                    }
                }
            }
        }
    }

    /* Dispatches a thread to mine any non-netherrack blocks generically */
    private fun mineBlockNormal(blockTask: BlockTask, side: EnumFacing) {
        if (blockTask.taskState == TaskState.BREAK) {
            blockTask.updateState(TaskState.BREAKING)
        }

        defaultScope.launch {
            delay(20L)
            sendMiningPackets(blockTask.blockPos, side)
        }
    }

    private suspend fun sendMiningPackets(pos: BlockPos, side: EnumFacing) {
        onMainThreadSafe {
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, side))
            if (fastBreak) connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, side))

            player.swingArm(EnumHand.MAIN_HAND)
        }

        if (!fastBreak) {
            delay(20L)
            onMainThreadSafe {
                connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, side))
                player.swingArm(EnumHand.MAIN_HAND)
            }
        }
    }

    private fun isInsideBlueprint(pos: BlockPos): Boolean {
        return blueprint.containsKey(pos)
    }

    private fun isInsideBlueprintBuild(pos: BlockPos): Boolean {
        return blueprint[pos]?.let { it != Blocks.AIR } ?: false
    }

    fun printSettings() {
        StringBuilder(ignoreBlocks.size + 1).run {
            append("$chatName Settings" +
                "\n    §9> §rMain material: §7${material.localizedName}" +
                "\n    §9> §rFiller material: §7${fillerMat.localizedName}" +
                "\n    §9> §rIgnored Blocks:")

            for (b in ignoreBlocks) append("\n        §9> §7${b.registryName}")

            MessageSendHelper.sendChatMessage(toString())
        }
    }

    // ToDo: Update to new dynamic blueprint
    private fun getBlueprintStats(): Pair<Int, Int> {
        var materialUsed = 0
        var fillerMatUsed = 0

        for ((_, b) in blueprint) {
            when (b) {
                material -> materialUsed++
                fillerMat -> fillerMatUsed++
            }
        }

        return Pair(materialUsed / 2, fillerMatUsed / 2)
    }

    fun SafeClientEvent.gatherStatistics(): List<String> {
        val currentTask = if (sortedTasks.isNotEmpty()) {
            sortedTasks[0]
        } else {
            null
        }

        materialLeft = player.allSlots.countBlock(material)
        fillerMatLeft = player.allSlots.countBlock(fillerMat)
        val indirectMaterialLeft = 8 * player.allSlots.countBlock(Blocks.ENDER_CHEST)

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
            "    §7Stuck ticks: §9${currentTask?.stuckTicks.toString()}",
//            "    §7Pathing: §9$pathing",
            "§rEstimations",
            "    §7${material.localizedName} (main material): §9$materialLeft + ($indirectMaterialLeft)",
            "    §7${fillerMat.localizedName} (filler material): §9$fillerMatLeft",
//            "    §7Paving distance left: §9$pavingLeftAll",
//            "    §7Estimated destination: §9(${currentBlockPos.add(startingDirection.directionVec.multiply(pavingLeft))})",
//            "    §7ETA: §9$hoursLeft:$minutesLeft:$secondsLeft"
        )

        if (printDebug) {
            statistics.addAll(getQueue())
        }

        return statistics
    }

    private fun getQueue(): List<String> {
        val message = ArrayList<String>()
        message.add("Pending Tasks:")
        addTaskToMessageList(message, sortedTasks)
        message.add("Done Tasks:")
        addTaskToMessageList(message, doneTasks.values)
        return message
    }

    private fun addTaskToMessageList(list: ArrayList<String>, tasks: Collection<BlockTask>) {
        for (blockTask in tasks) list.add("    ${blockTask.block.localizedName}@(${blockTask.blockPos.asString()}) State: ${blockTask.taskState} Timings: (Threshold: ${blockTask.taskState.stuckThreshold}, Timeout: ${blockTask.taskState.stuckTimeout}) Priority: ${blockTask.taskState.ordinal} Stuck: ${blockTask.stuckTicks}")
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
            if (state == TaskState.DONE || state == TaskState.PLACED || state == TaskState.BROKEN) {
                onUpdate()
            }
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

    enum class TaskState(val stuckThreshold: Int, val stuckTimeout: Int, val color: ColorHolder) {
        DONE(69420, 0x22, ColorHolder(50, 50, 50)),
        BROKEN(1000, 1000, ColorHolder(111, 0, 0)),
        PLACED(1000, 1000, ColorHolder(53, 222, 66)),
        LIQUID_SOURCE(100, 100, ColorHolder(120, 41, 240)),
        LIQUID_FLOW(80, 80, ColorHolder(120, 41, 240)),
        BREAKING(100, 100, ColorHolder(240, 222, 60)),
        BREAK(20, 20, ColorHolder(222, 0, 0)),
        PLACE(20, 10, ColorHolder(35, 188, 254)),
        PENDING_BROKEN(100, 100, ColorHolder(0, 0, 0)),
        PENDING_PLACED(100, 100, ColorHolder(0, 0, 0))
    }

}

