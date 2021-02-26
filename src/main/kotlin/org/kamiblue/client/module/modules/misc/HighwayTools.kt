package org.kamiblue.client.module.modules.misc

import baritone.api.pathing.goals.GoalNear
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.client.audio.PositionedSoundRecord
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Blocks
import net.minecraft.init.Enchantments
import net.minecraft.init.Items
import net.minecraft.init.SoundEvents
import net.minecraft.inventory.Slot
import net.minecraft.item.ItemBlock
import net.minecraft.item.ItemPickaxe
import net.minecraft.network.play.client.CPacketClientStatus
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.network.play.server.SPacketPlayerPosLook
import net.minecraft.network.play.server.SPacketSetSlot
import net.minecraft.stats.StatList
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.EnumDifficulty
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.kamiblue.client.event.SafeClientEvent
import org.kamiblue.client.event.events.PacketEvent
import org.kamiblue.client.event.events.RenderWorldEvent
import org.kamiblue.client.manager.managers.PlayerPacketManager
import org.kamiblue.client.module.Category
import org.kamiblue.client.module.Module
import org.kamiblue.client.module.modules.client.Hud.primaryColor
import org.kamiblue.client.module.modules.client.Hud.secondaryColor
import org.kamiblue.client.module.modules.movement.AntiHunger
import org.kamiblue.client.module.modules.movement.Velocity
import org.kamiblue.client.module.modules.player.InventoryManager
import org.kamiblue.client.module.modules.player.LagNotifier
import org.kamiblue.client.process.HighwayToolsProcess
import org.kamiblue.client.process.PauseProcess
import org.kamiblue.client.setting.settings.impl.collection.CollectionSetting
import org.kamiblue.client.util.*
import org.kamiblue.client.util.EntityUtils.flooredPosition
import org.kamiblue.client.util.WorldUtils.blackList
import org.kamiblue.client.util.WorldUtils.getBetterNeighbour
import org.kamiblue.client.util.WorldUtils.getMiningSide
import org.kamiblue.client.util.WorldUtils.isLiquid
import org.kamiblue.client.util.WorldUtils.isPlaceable
import org.kamiblue.client.util.WorldUtils.shulkerList
import org.kamiblue.client.util.color.ColorHolder
import org.kamiblue.client.util.graphics.ESPRenderer
import org.kamiblue.client.util.graphics.font.TextComponent
import org.kamiblue.client.util.items.*
import org.kamiblue.client.util.math.CoordinateConverter.asString
import org.kamiblue.client.util.math.Direction
import org.kamiblue.client.util.math.RotationUtils.getRotationTo
import org.kamiblue.client.util.math.VectorUtils.distanceTo
import org.kamiblue.client.util.math.VectorUtils.multiply
import org.kamiblue.client.util.math.VectorUtils.toVec3dCenter
import org.kamiblue.client.util.math.isInSight
import org.kamiblue.client.util.text.MessageSendHelper
import org.kamiblue.client.util.threads.*
import org.kamiblue.commons.extension.ceilToInt
import org.kamiblue.commons.extension.floorToInt
import kotlin.math.abs
import kotlin.random.Random.Default.nextInt

/**
 * @author Avanatiker
 * @since 20/08/2020
 *
 */
internal object HighwayTools : Module(
    name = "HighwayTools",
    description = "Be the grief a step a head.",
    category = Category.MISC,
    alias = arrayOf("HT", "HWT"),
    modulePriority = 10
) {
    private val page by setting("Page", Page.BUILD, description = "Switch between the setting pages")

    private val defaultIgnoreBlocks = linkedSetOf(
        "minecraft:standing_sign",
        "minecraft:wall_sign",
        "minecraft:standing_banner",
        "minecraft:wall_banner",
        "minecraft:bedrock",
        "minecraft:end_portal",
        "minecraft:end_portal_frame",
        "minecraft:portal"
    )

    // build settings
    private val mode by setting("Mode", Mode.HIGHWAY, { page == Page.BUILD }, description = "Choose the structure")
    private val clearSpace by setting("Clear Space", true, { page == Page.BUILD && mode == Mode.HIGHWAY }, description = "Clears out the tunnel if necessary")
    private val cleanFloor by setting("Clean Floor", false, { page == Page.BUILD && mode == Mode.TUNNEL }, description = "Cleans up the tunnels floor")
    private val cleanWalls by setting("Clean Walls", false, { page == Page.BUILD && mode == Mode.TUNNEL }, description = "Cleans up the tunnels walls")
    private val cleanRoof by setting("Clean Roof", false, { page == Page.BUILD && mode == Mode.TUNNEL }, description = "Cleans up the tunnels roof")
    private val cleanCorner by setting("Clean Corner", false, { page == Page.BUILD && mode == Mode.TUNNEL && !cornerBlock }, description = "Cleans up the tunnels corner")
    private val cornerBlock by setting("Corner Block", false, { page == Page.BUILD && (mode == Mode.HIGHWAY || mode == Mode.TUNNEL) }, description = "If activated will break the corner in tunnel or place a corner while paving")
    private val width by setting("Width", 6, 1..11, 1, { page == Page.BUILD }, description = "Sets the width of blueprint")
    private val height by setting("Height", 4, 1..6, 1, { page == Page.BUILD && clearSpace }, description = "Sets height of blueprint")
    private val railing by setting("Railing", true, { page == Page.BUILD && mode == Mode.HIGHWAY }, description = "Adds a railing / rim / border to the highway")
    private val railingHeight by setting("Railing Height", 1, 1..4, 1, { railing && page == Page.BUILD && mode == Mode.HIGHWAY }, description = "Sets height of railing")
    private val materialSaved = setting("Material", "minecraft:obsidian", { false })
    private val fillerMatSaved = setting("FillerMat", "minecraft:netherrack", { false })
    val ignoreBlocks = setting(CollectionSetting("IgnoreList", defaultIgnoreBlocks, { false }))

    // behavior settings
    private val interacting by setting("Rotation Mode", RotationMode.SPOOF, { page == Page.BEHAVIOR }, description = "Force view client side, only server side or no interaction at all")
    private val dynamicDelay by setting("Dynamic Place Delay", true, { page == Page.BEHAVIOR }, description = "Slows down on failed placement attempts")
    private val placeDelay by setting("Place Delay", 3, 1..20, 1, { page == Page.BEHAVIOR }, description = "Sets the delay ticks between placement tasks")
    private val breakDelay by setting("Break Delay", 1, 1..20, 1, { page == Page.BEHAVIOR }, description = "Sets the delay ticks between break tasks")
    private val illegalPlacements by setting("Illegal Placements", false, { page == Page.BEHAVIOR }, description = "Do not use on 2b2t. Tries to interact with invisible surfaces")
    private val bridging by setting("Bridging", true, { page == Page.BEHAVIOR }, description = "Tries to bridge / scaffold when stuck placing")
    private val multiBuilding by setting("Shuffle Tasks", false, { page == Page.BEHAVIOR }, description = "Only activate when working with several players")
    private val toggleInventoryManager by setting("Toggle InvManager", false, { page == Page.BEHAVIOR }, description = "Activates InventoryManager on enable")
    private val toggleAutoObsidian by setting("Toggle AutoObsidian", true, { page == Page.BEHAVIOR }, description = "Activates AutoObsidian on enable")
    private val taskTimeout by setting("Task Timeout", 8, 0..20, 1, { page == Page.BEHAVIOR }, description = "Timeout for waiting for the server to try again")
    private val rubberbandTimeout by setting("Rubberband Timeout", 50, 5..100, 5, { page == Page.BEHAVIOR }, description = "Timeout for pausing after a lag")
    private val maxReach by setting("Max Reach", 4.9f, 1.0f..6.0f, 0.1f, { page == Page.BEHAVIOR }, description = "Sets the range of the blueprint. Decrease when tasks fail!")
    private val maxBreaks by setting("Multi Break", 1, 1..5, 1, { page == Page.BEHAVIOR }, description = "EXPERIMENTAL: Breaks multiple instant breaking blocks per tick in view")
    private val limitFactor by setting("Limit Factor", 1.0f, 0.5f..2.0f, 0.01f, { page == Page.BEHAVIOR }, description = "EXPERIMENTAL: Factor for TPS witch acts as limit for maximum breaks per second.")
    private val placementSearch by setting("Place Deep Search", 2, 1..4, 1, { page == Page.BEHAVIOR }, description = "EXPERIMENTAL: Attempts to find a support block for placing against")

    // stat settings
    private val anonymizeStats by setting("Anonymize", false, { page == Page.STATS }, description = "Censors all coordinates in HUD and Chat.")
    private val simpleMovingAverageRange by setting("Moving Average", 60, 5..600, 5, { page == Page.STATS }, description = "Sets the timeframe of the average in seconds")
    private val showSession by setting("Show Session", true, { page == Page.STATS }, description = "Toggles the Session section in HUD")
    private val showLifeTime by setting("Show Lifetime", true, { page == Page.STATS }, description = "Toggles the Lifetime section in HUD")
    private val showPerformance by setting("Show Performance", true, { page == Page.STATS }, description = "Toggles the Performance section in HUD")
    private val showEnvironment by setting("Show Environment", true, { page == Page.STATS }, description = "Toggles the Environment section in HUD")
    private val showTask by setting("Show Task", true, { page == Page.STATS }, description = "Toggles the Task section in HUD")
    private val showEstimations by setting("Show Estimations", true, { page == Page.STATS }, description = "Toggles the Estimations section in HUD")
    private val resetStats = setting("Reset Stats", false, { page == Page.STATS }, description = "Resets the stats")

    // config
    private val fakeSounds by setting("Fake Sounds", true, { page == Page.CONFIG }, description = "Adds artificial sounds to the actions")
    private val info by setting("Show Info", true, { page == Page.CONFIG }, description = "Prints session stats in chat")
    private val printDebug by setting("Show Queue", false, { page == Page.CONFIG }, description = "Shows task queue in HUD")
    private val debugMessages by setting("Debug Messages", DebugMessages.IMPORTANT, { page == Page.CONFIG }, description = "Sets the debug log depth level")
    private val goalRender by setting("Goal Render", false, { page == Page.CONFIG }, description = "Renders the baritone goal")
    private val filled by setting("Filled", true, { page == Page.CONFIG }, description = "Renders colored task surfaces")
    private val outline by setting("Outline", true, { page == Page.CONFIG }, description = "Renders colored task outlines")
    private val aFilled by setting("Filled Alpha", 26, 0..255, 1, { filled && page == Page.CONFIG }, description = "Sets the opacity")
    private val aOutline by setting("Outline Alpha", 91, 0..255, 1, { outline && page == Page.CONFIG }, description = "Sets the opacity")

    private enum class Mode {
        HIGHWAY, FLAT, TUNNEL
    }

    private enum class Page {
        BUILD, BEHAVIOR, STATS, CONFIG
    }

    @Suppress("UNUSED")
    private enum class RotationMode {
        OFF, SPOOF, VIEW_LOCK
    }

    private enum class DebugMessages {
        OFF, IMPORTANT, ALL
    }

    // internal settings
    var material: Block
        get() = Block.getBlockFromName(materialSaved.value) ?: Blocks.OBSIDIAN
        set(value) {
            materialSaved.value = value.registryName.toString()
        }
    var fillerMat: Block
        get() = Block.getBlockFromName(fillerMatSaved.value) ?: Blocks.NETHERRACK
        set(value) {
            fillerMatSaved.value = value.registryName.toString()
        }
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
    private var extraPlaceDelay = 0

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
    private val packetLimiter = ArrayDeque<Long>()

    // Stats
    private val simpleMovingAveragePlaces = ArrayDeque<Long>()
    private val simpleMovingAverageBreaks = ArrayDeque<Long>()
    private val simpleMovingAverageDistance = ArrayDeque<Long>()
    private var totalBlocksPlaced = 0
    private var totalBlocksBroken = 0
    private var totalDistance = 0.0
    private var runtimeMilliSeconds = 0
    private var prevFood = 0
    private var foodLoss = 1
    private var materialLeft = 0
    private var fillerMatLeft = 0
    private var lastToolDamage = 0
    private var durabilityUsages = 0

    private val mutex = Mutex()
    private val renderer = ESPRenderer()

    override fun isActive(): Boolean {
        return isEnabled && active
    }

    init {
        shulkerList.forEach {
            ignoreBlocks.add(it.registryName.toString())
        }

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

                startingBlockPos = player.flooredPosition
                currentBlockPos = startingBlockPos
                startingDirection = Direction.fromEntity(player)

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
                totalDistance += startingBlockPos.distanceTo(currentBlockPos)

                printDisable()
            }
        }

        resetStats.consumers.add { _, it ->
            if (it) resetStats()
            false
        }
    }

    private fun printEnable() {
        if (info) {
            MessageSendHelper.sendRawChatMessage("    §9> §7Direction: §a${startingDirection.displayName} / ${startingDirection.displayNameXY}§r")

            if (!anonymizeStats) {
                if (startingDirection.isDiagonal) {
                    MessageSendHelper.sendRawChatMessage("    §9> §7Axis offset: §a${startingBlockPos.x} ${startingBlockPos.z}§r")

                    if (abs(startingBlockPos.x) != abs(startingBlockPos.z)) {
                        MessageSendHelper.sendRawChatMessage("    §9> §cYou may have an offset to diagonal highway position!")
                    }
                } else {
                    if (startingDirection == Direction.NORTH || startingDirection == Direction.SOUTH) {
                        MessageSendHelper.sendRawChatMessage("    §9> §7Axis offset: §a${startingBlockPos.x}§r")
                    } else {
                        MessageSendHelper.sendRawChatMessage("    §9> §7Axis offset: §a${startingBlockPos.z}§r")
                    }

                }
            }

            if (startingBlockPos.y != 120 && mode != Mode.TUNNEL) {
                MessageSendHelper.sendRawChatMessage("    §9> §cCheck altitude and make sure to build at Y: 120 for the correct height")
            }

            if (AntiHunger.isEnabled) {
                MessageSendHelper.sendRawChatMessage("    §9> §cAntiHunger does slow down block interactions.")
            }

            if (LagNotifier.isDisabled) {
                MessageSendHelper.sendRawChatMessage("    §9> §cYou should activate LagNotifier to make the bot stop on server lag.")
            }

            if (multiBuilding && Velocity.isDisabled) {
                MessageSendHelper.sendRawChatMessage("    §9> §cMake sure to enable Velocity to not get pushed from your mates.")
            }

            if (material == fillerMat) {
                MessageSendHelper.sendRawChatMessage("    §9> §cMake sure to use §aTunnel Mode§c instead of having same material for both main and filler!")
            }

        }
    }

    private fun printDisable() {
        if (info) {
            MessageSendHelper.sendRawChatMessage("    §9> §7Placed blocks: §a$totalBlocksPlaced§r")
            MessageSendHelper.sendRawChatMessage("    §9> §7Destroyed blocks: §a$totalBlocksBroken§r")
            MessageSendHelper.sendRawChatMessage("    §9> §7Distance: §a${startingBlockPos.distanceTo(currentBlockPos).toInt()}§r")
        }
    }

    init {
        safeListener<PacketEvent.Receive> {
            when (it.packet) {
                is SPacketBlockChange -> {
                    val pos = it.packet.blockPosition
                    if (!isInsideBlueprint(pos)) return@safeListener

                    val prev = world.getBlockState(pos).block
                    val new = it.packet.getBlockState().block

                    if (prev != new) {
                        val task = pendingTasks[pos] ?: return@safeListener

                        when (task.taskState) {
                            TaskState.PENDING_BREAK, TaskState.BREAKING -> {
                                if (new == Blocks.AIR) {
                                    runBlocking {
                                        mutex.withLock {
                                            task.updateState(TaskState.BROKEN)
                                        }
                                    }
                                }
                            }
                            TaskState.PENDING_PLACE -> {
                                if (task.block != Blocks.AIR && task.block == new) {
                                    runBlocking {
                                        mutex.withLock {
                                            task.updateState(TaskState.PLACED)
                                        }
                                    }
                                }
                            }
                            else -> {
                                // Ignored
                            }
                        }
                    }
                }
                is SPacketPlayerPosLook -> {
                    rubberbandTimer.reset()
                }
                is SPacketSetSlot -> {
                    val currentToolDamage = it.packet.stack.itemDamage
                    val durabilityUsage = currentToolDamage - lastToolDamage

                    if (durabilityUsage in 1..100) {
                        durabilityUsages += durabilityUsage
                    }

                    lastToolDamage = it.packet.stack.itemDamage
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

            if (!rubberbandTimer.tick(rubberbandTimeout.toLong(), false) ||
                PauseProcess.isActive ||
                AutoObsidian.isActive() ||
                (world.difficulty == EnumDifficulty.PEACEFUL &&
                    player.dimension == 1 &&
                    player.serverBrand.contains("2b2t"))) {
                refreshData()
                return@safeListener
            }

            if (!active) {
                active = true
                BaritoneUtils.primary?.pathingControlManager?.registerProcess(HighwayToolsProcess)
            } else {
                // Cant update at higher frequency
                if (runtimeMilliSeconds % 15000 == 0) {
                    connection.sendPacket(CPacketClientStatus(CPacketClientStatus.State.REQUEST_STATS))
                }
                runtimeMilliSeconds += 50
                updateDequeues()
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

//        renderer.add(world.getBlockState(currentBlockPos).getSelectedBoundingBox(world, currentBlockPos), ColorHolder(255, 255, 255))

        pendingTasks.values.forEach {
            if (it.taskState == TaskState.DONE) return@forEach
            renderer.add(world.getBlockState(it.blockPos).getSelectedBoundingBox(world, it.blockPos), it.taskState.color)
        }

        doneTasks.values.forEach {
            if (it.block == Blocks.AIR) return@forEach
            renderer.add(world.getBlockState(it.blockPos).getSelectedBoundingBox(world, it.blockPos), it.taskState.color)
        }
    }

    private fun SafeClientEvent.updateFood() {
        val currentFood = player.foodStats.foodLevel
        if (currentFood < 7.0) {
            MessageSendHelper.sendChatMessage("$chatName Out of food, disabling")
            mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
        }
        if (currentFood != prevFood) {
            if (currentFood < prevFood) foodLoss++
            prevFood = currentFood
        }
    }

    private fun updateDequeues() {
        val removeTime = System.currentTimeMillis() - simpleMovingAverageRange * 1000L

        updateDeque(simpleMovingAveragePlaces, removeTime)
        updateDeque(simpleMovingAverageBreaks, removeTime)
        updateDeque(simpleMovingAverageDistance, removeTime)

        updateDeque(packetLimiter, System.currentTimeMillis() - 1000L)
    }

    private fun updateDeque(deque: ArrayDeque<Long>, removeTime: Long) {
        while (deque.isNotEmpty() && deque.first() < removeTime) {
            deque.removeFirst()
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

        blueprint.forEach { (pos, block) ->
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
            isPlaceable(pos, true) -> {
                if (checkSupport(pos, block)) {
                    addTaskToDone(pos, block)
                } else {
                    addTaskToPending(pos, TaskState.PLACE, block)
                }
            }
            else -> {
                if (checkSupport(pos, block)) {
                    addTaskToDone(pos, block)
                } else {
                    addTaskToPending(pos, TaskState.BREAK, block)
                }
            }
        }
    }

    private fun SafeClientEvent.checkSupport(pos: BlockPos, block: Block): Boolean {
        return mode == Mode.HIGHWAY &&
            startingDirection.isDiagonal &&
            world.getBlockState(pos.up()).block == material &&
            block == fillerMat
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
                if (clearSpace) generateClear(thisPos, xDirection)
                if (mode == Mode.TUNNEL) {
                    if (cleanFloor) generateFloor(thisPos, xDirection)
                    if (cleanWalls) generateWalls(thisPos, xDirection)
                    if (cleanRoof) generateRoof(thisPos, xDirection)
                    if (cleanCorner && !cornerBlock) generateCorner(thisPos, xDirection)
                } else {
                    generateBase(thisPos, xDirection)
                }
            }
            if (mode == Mode.TUNNEL && !cleanFloor) {
                if (startingDirection.isDiagonal) {
                    for (x in 1..maxReach.floorToInt()) {
                        blueprint[basePos.add(zDirection.directionVec.multiply(x))] = fillerMat
                    }
                } else {
                    for (x in 1..maxReach.floorToInt()) {
                        val pos = basePos.add(zDirection.directionVec.multiply(x))
                        blueprint[pos] = fillerMat
                        blueprint[pos.add(startingDirection.clockwise(4).directionVec)] = fillerMat
                    }
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
                if (!cornerBlock && startingDirection.isDiagonal) blueprint[pos] = fillerMat
                val startHeight = if (cornerBlock) 0 else 1
                for (y in startHeight..railingHeight) {
                    blueprint[pos.up(y)] = material
                }
            } else {
                blueprint[pos] = material
            }
        }
    }

    private fun generateFloor(basePos: BlockPos, xDirection: Direction) {
        val wid = if (cornerBlock) {
            width
        } else {
            width - 2
        }
        for (w in 0 until wid) {
            val x = w - wid / 2
            val pos = basePos.add(xDirection.directionVec.multiply(x))
            blueprint[pos] = fillerMat
        }
    }

    private fun generateWalls(basePos: BlockPos, xDirection: Direction) {
        val cb = if (!cornerBlock) {
            1
        } else {
            0
        }
        for (h in cb until height) {
            blueprint[basePos.add(xDirection.directionVec.multiply(-1 - width / 2)).up(h + 1)] = fillerMat
            blueprint[basePos.add(xDirection.directionVec.multiply(width - width / 2)).up(h + 1)] = fillerMat
        }
    }

    private fun generateRoof(basePos: BlockPos, xDirection: Direction) {
        for (w in 0 until width) {
            val x = w - width / 2
            val pos = basePos.add(xDirection.directionVec.multiply(x))
            blueprint[pos.up(height + 1)] = fillerMat
        }
    }

    private fun generateCorner(basePos: BlockPos, xDirection: Direction) {
        blueprint[basePos.add(xDirection.directionVec.multiply(-1 - width / 2 + 1)).up()] = fillerMat
        blueprint[basePos.add(xDirection.directionVec.multiply(width - width / 2 - 1)).up()] = fillerMat
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

        if (!isTaskDoneOrNull(possiblePos, false) ||
            !isTaskDoneOrNull(possiblePos.up(), false) ||
            !isTaskDoneOrNull(possiblePos.down(), true)) return nextPos

        if (checkTasks(possiblePos.up())) nextPos = possiblePos

        if (currentBlockPos != nextPos) {
            for (x in 1..currentBlockPos.distanceTo(nextPos).toInt()) {
                simpleMovingAverageDistance.add(System.currentTimeMillis())
            }
            refreshData()
        }

        return nextPos
    }

    private fun SafeClientEvent.isTaskDoneOrNull(pos: BlockPos, solid: Boolean) =
        (pendingTasks[pos] ?: doneTasks[pos])?.let {
            it.taskState == TaskState.DONE
        } ?: run {
            if (solid) {
                !isPlaceable(pos, true)
            } else {
                world.isAirBlock(pos)
            }
        }

    private fun checkTasks(pos: BlockPos): Boolean {
        return pendingTasks.values.all {
            it.taskState == TaskState.DONE || pos.distanceTo(it.blockPos) < maxReach - 0.7
        }
    }

    private fun SafeClientEvent.runTasks() {
        if (pendingTasks.isEmpty()) {
            if (checkDoneTasks()) doneTasks.clear()
            refreshData()
        } else {
            waitTicks--

            pendingTasks.values.forEach {
                doTask(it, true)
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
            if (ignoreBlocks.contains(block.registryName.toString())) continue

            when {
                blockTask.block == material && block != material -> return false
                mode == Mode.TUNNEL && blockTask.block == fillerMat && block != fillerMat -> return false
                blockTask.block == Blocks.AIR && block != Blocks.AIR -> return false
            }

        }
        return true
    }

    private fun SafeClientEvent.sortTasks() {

        if (multiBuilding) {
            pendingTasks.values.forEach {
                it.shuffle()
            }

            runBlocking {
                mutex.withLock {
                    sortedTasks = pendingTasks.values.sortedWith(
                        compareBy<BlockTask> {
                            it.taskState.ordinal
                        }.thenBy {
                            it.stuckTicks
                        }.thenBy {
                            it.shuffle
                        }
                    )
                }
            }
        } else {
            val eyePos = player.getPositionEyes(1.0f)

            pendingTasks.values.forEach {
                it.prepareSortInfo(this, eyePos)
            }

            runBlocking {
                mutex.withLock {
                    sortedTasks = pendingTasks.values.sortedWith(
                        compareBy<BlockTask> {
                            it.taskState.ordinal
                        }.thenBy {
                            it.stuckTicks
                        }.thenByDescending {
                            it.sides
                        }.thenBy {
                            it.startDistance
                        }.thenBy {
                            it.eyeDistance
                        }.thenBy {
                            it.hitVecDistance
                        }
                    )
                }
            }
        }
    }

    private fun SafeClientEvent.checkStuckTimeout(blockTask: BlockTask): Boolean {
        val timeout = blockTask.taskState.stuckTimeout

        if (blockTask.stuckTicks > timeout) {
            when (blockTask.taskState) {
                TaskState.PENDING_BREAK -> {
                    blockTask.updateState(TaskState.BREAK)
                }
                TaskState.PENDING_PLACE -> {
                    blockTask.updateState(TaskState.PLACE)
                }
                else -> {
                    if (debugMessages != DebugMessages.OFF) {
                        if (!anonymizeStats) {
                            MessageSendHelper.sendChatMessage("$chatName Stuck while ${blockTask.taskState}@(${blockTask.blockPos.asString()}) for more then $timeout ticks (${blockTask.stuckTicks}), refreshing data.")
                        } else {
                            MessageSendHelper.sendChatMessage("$chatName Stuck while ${blockTask.taskState} for more then $timeout ticks (${blockTask.stuckTicks}), refreshing data.")
                        }
                    }

                    if (dynamicDelay && blockTask.taskState == TaskState.PLACE && extraPlaceDelay < 10) extraPlaceDelay += 1

                    refreshData()
                    return false
                }
            }
        }

        return true
    }

    private fun SafeClientEvent.doTask(blockTask: BlockTask, updateOnly: Boolean) {
        if (!updateOnly) blockTask.onTick()

        when (blockTask.taskState) {
            TaskState.DONE -> {
                doDone(blockTask)
            }
            TaskState.BREAKING -> {
                doBreaking(blockTask, updateOnly)
            }
            TaskState.BROKEN -> {
                doBroken(blockTask)
            }
            TaskState.PLACED -> {
                doPlaced(blockTask)
            }
            TaskState.BREAK -> {
                doBreak(blockTask, updateOnly)
            }
            TaskState.PLACE, TaskState.LIQUID_SOURCE, TaskState.LIQUID_FLOW -> {
                doPlace(blockTask, updateOnly)
            }
            TaskState.PENDING_BREAK, TaskState.PENDING_PLACE -> {
                if (!updateOnly && debugMessages == DebugMessages.ALL) {
                    MessageSendHelper.sendChatMessage("$chatName Currently waiting for blockState updates...")
                }
                blockTask.onStuck()
            }
        }
    }

    private fun doDone(blockTask: BlockTask) {
        pendingTasks[blockTask.blockPos]
        doneTasks[blockTask.blockPos] = blockTask
    }

    private fun SafeClientEvent.doBreaking(blockTask: BlockTask, updateOnly: Boolean) {
        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                waitTicks = breakDelay
                blockTask.updateState(TaskState.BROKEN)
                return
            }
            is BlockLiquid -> {
                val filler = if (player.allSlots.countBlock(fillerMat) == 0 || isInsideBlueprintBuild(blockTask.blockPos)) {
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

                return
            }
        }

        if (!updateOnly) {
            mineBlock(blockTask)
        }
    }

    private fun SafeClientEvent.doBroken(blockTask: BlockTask) {
        when (world.getBlockState(blockTask.blockPos).block) {
            Blocks.AIR -> {
                totalBlocksBroken++
                simpleMovingAverageBreaks.add(System.currentTimeMillis())

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

    private fun SafeClientEvent.doPlaced(blockTask: BlockTask) {
        val currentBlock = world.getBlockState(blockTask.blockPos).block

        when {
            blockTask.block == currentBlock && currentBlock != Blocks.AIR -> {
                totalBlocksPlaced++
                simpleMovingAveragePlaces.add(System.currentTimeMillis())

                if (dynamicDelay && extraPlaceDelay > 0) extraPlaceDelay -= 1

                blockTask.updateState(TaskState.DONE)
                if (fakeSounds) {
                    val soundType = currentBlock.getSoundType(world.getBlockState(blockTask.blockPos), world, blockTask.blockPos, player)
                    world.playSound(player, blockTask.blockPos, soundType.placeSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
                }
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

    private fun SafeClientEvent.doBreak(blockTask: BlockTask, updateOnly: Boolean) {
        val currentBlock = world.getBlockState(blockTask.blockPos).block

        if (ignoreBlocks.contains(currentBlock.registryName.toString())) {
            blockTask.updateState(TaskState.DONE)
        }

        when (blockTask.block) {
            fillerMat -> {
                if (world.getBlockState(blockTask.blockPos.up()).block == material ||
                    !isPlaceable(blockTask.blockPos)) {
                    blockTask.updateState(TaskState.DONE)
                    return
                }
            }
            material -> {
                if (currentBlock == material) {
                    blockTask.updateState(TaskState.DONE)
                    return
                }
            }
        }

        when (currentBlock) {
            Blocks.AIR -> {
                if (blockTask.block == Blocks.AIR) {
                    blockTask.updateState(TaskState.BROKEN)
                    return
                } else {
                    blockTask.updateState(TaskState.PLACE)
                    return
                }
            }
            is BlockLiquid -> {
                val filler = if (player.allSlots.countBlock(fillerMat) == 0 || isInsideBlueprintBuild(blockTask.blockPos)) material
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

        if (!updateOnly) {
            if (handleLiquid(blockTask)) return
            swapOrMoveBestTool(blockTask)
            mineBlock(blockTask)
        }
    }

    private fun SafeClientEvent.doPlace(blockTask: BlockTask, updateOnly: Boolean) {
        val currentBlock = world.getBlockState(blockTask.blockPos).block

        if (bridging && player.positionVector.distanceTo(currentBlockPos) < 1 && shouldBridge()) {
            val factor = if (startingDirection.isDiagonal) {
                0.555
            } else {
                0.505
            }
            val target = currentBlockPos.toVec3dCenter().add(Vec3d(startingDirection.directionVec).scale(factor))
            player.motionX = (target.x - player.posX).coerceIn(-0.2, 0.2)
            player.motionZ = (target.z - player.posZ).coerceIn(-0.2, 0.2)
        }

        if ((blockTask.taskState == TaskState.LIQUID_FLOW ||
                blockTask.taskState == TaskState.LIQUID_SOURCE) &&
            !isLiquid(blockTask.blockPos)) {
            blockTask.updateState(TaskState.DONE)
            return
        }

        when (blockTask.block) {
            material -> {
                if (currentBlock == material) {
                    blockTask.updateState(TaskState.PLACED)
                    return
                } else if (currentBlock != Blocks.AIR && !isLiquid(blockTask.blockPos)) {
                    blockTask.updateState(TaskState.BREAK)
                    return
                }
            }
            fillerMat -> {
                if (currentBlock == fillerMat) {
                    blockTask.updateState(TaskState.PLACED)
                    return
                } else if (currentBlock != fillerMat &&
                    mode == Mode.HIGHWAY &&
                    world.getBlockState(blockTask.blockPos.up()).block == material) {
                    blockTask.updateState(TaskState.DONE)
                    return
                }
            }
            Blocks.AIR -> {
                if (!isLiquid(blockTask.blockPos)) {
                    if (currentBlock != Blocks.AIR) {
                        blockTask.updateState(TaskState.BREAK)
                    } else {
                        blockTask.updateState(TaskState.BROKEN)
                    }
                    return
                }
            }
        }

        if (!updateOnly) {
            if (!isPlaceable(blockTask.blockPos)) {
                if (debugMessages == DebugMessages.ALL) {
                    if (!anonymizeStats) {
                        MessageSendHelper.sendChatMessage("$chatName Invalid place position: ${blockTask.blockPos}. Removing task")
                    } else {
                        MessageSendHelper.sendChatMessage("$chatName Invalid place position. Removing task")
                    }
                }
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
        val useBlock = when {
            player.allSlots.countBlock(blockTask.block) > 0 -> blockTask.block
            player.allSlots.countBlock(material) > 0 -> material
            player.allSlots.countBlock(fillerMat) > 0 && mode == Mode.TUNNEL -> fillerMat
            else -> blockTask.block
        }

        val success = swapToBlockOrMove(useBlock, predicateSlot = {
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
        val neighbours = if (illegalPlacements) {
            getBetterNeighbour(blockTask.blockPos, placementSearch, maxReach)
        } else {
            getBetterNeighbour(blockTask.blockPos, placementSearch, maxReach, true)
        }

        when (neighbours.size) {
            0 -> {
                if (debugMessages == DebugMessages.ALL) {
                    if (!anonymizeStats) {
                        MessageSendHelper.sendChatMessage("$chatName No neighbours found for ${blockTask.blockPos}")
                    } else {
                        MessageSendHelper.sendChatMessage("$chatName No neighbours found")
                    }
                }
                blockTask.onStuck(21)
                return
            }
            1 -> {
                lastHitVec = WorldUtils.getHitVec(neighbours.last().second, neighbours.last().first)
                rotateTimer.reset()

                placeBlockNormal(blockTask, neighbours.last())
            }
            else -> {
                neighbours.forEach {
                    addTaskToPending(it.second, TaskState.PLACE, fillerMat)
                }
            }
        }
    }

    private fun SafeClientEvent.placeBlockNormal(blockTask: BlockTask, pair: Pair<EnumFacing, BlockPos>) {
        val hitVecOffset = WorldUtils.getHitVecOffset(pair.first)
        val currentBlock = world.getBlockState(pair.second).block

        waitTicks = if (dynamicDelay) {
            placeDelay + extraPlaceDelay
        } else {
            placeDelay
        }
        blockTask.updateState(TaskState.PENDING_PLACE)

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
            if (blockTask.taskState == TaskState.PENDING_PLACE) {
                mutex.withLock {
                    blockTask.updateState(TaskState.PLACE)
                }
                if (dynamicDelay && extraPlaceDelay < 10) extraPlaceDelay += 1
            }
        }
    }

    private fun SafeClientEvent.shouldBridge(): Boolean {
        var containsPlace = false
        for (task in sortedTasks) {
            if (task.taskState == TaskState.PLACE) {
                containsPlace = true
                if (getBetterNeighbour(task.blockPos, placementSearch, maxReach, true).isNotEmpty()) return false
            }
        }
        return containsPlace
    }

    private fun SafeClientEvent.getBestTool(blockTask: BlockTask): Slot? {
        return player.inventorySlots.asReversed().maxByOrNull {
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
    }

    private fun SafeClientEvent.swapOrMoveBestTool(blockTask: BlockTask): Boolean {
        val slotFrom = getBestTool(blockTask)

        return if (slotFrom != null) {
//            if (emptyDisable && slotFrom.stack.item != Items.DIAMOND_PICKAXE) {
//                MessageSendHelper.sendChatMessage("$chatName No ${Items.DIAMOND_PICKAXE.registryName} was found in inventory, disable")
//                mc.soundHandler.playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
//                disable()
//            }
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
            val neighbourPos = blockTask.blockPos.offset(side)

            if (world.getBlockState(neighbourPos).block !is BlockLiquid) continue

            if (player.distanceTo(neighbourPos) > maxReach) {
                blockTask.updateState(TaskState.DONE)
                return true
            }

            foundLiquid = true

            val isFlowing = world.getBlockState(blockTask.blockPos).let {
                it.block is BlockLiquid && it.getValue(BlockLiquid.LEVEL) != 0
            }

            val filler = if (isInsideBlueprintBuild(neighbourPos)) material else fillerMat

            pendingTasks[neighbourPos]?.let {
                if (isFlowing) {
                    it.updateState(TaskState.LIQUID_FLOW)
                } else {
                    it.updateState(TaskState.LIQUID_FLOW)
                }

                it.updateMaterial(filler)
            } ?: run {
                if (isFlowing) {
                    addTaskToPending(neighbourPos, TaskState.LIQUID_FLOW, filler)
                } else {
                    addTaskToPending(neighbourPos, TaskState.LIQUID_SOURCE, filler)
                }
            }
        }

        return foundLiquid
    }

    private fun SafeClientEvent.mineBlock(blockTask: BlockTask) {
        val blockState = world.getBlockState(blockTask.blockPos)

        if (blockState.block == Blocks.FIRE) {
            val sides = getBetterNeighbour(blockTask.blockPos, 1, maxReach, true)
            if (sides.isEmpty()) {
                blockTask.updateState(TaskState.PLACE)
                return
            }

            lastHitVec = WorldUtils.getHitVec(sides.last().second, sides.last().first)
            rotateTimer.reset()

            mineBlockNormal(blockTask, sides.last().first)
        } else {
            val side = getMiningSide(blockTask.blockPos) ?: run {
                blockTask.onStuck()
                return
            }

            lastHitVec = WorldUtils.getHitVec(blockTask.blockPos, side)
            rotateTimer.reset()

            if (blockState.getPlayerRelativeBlockHardness(player, world, blockTask.blockPos) > 2.8) {
                mineBlockInstant(blockTask, side)
            } else {
                mineBlockNormal(blockTask, side)
            }
        }
    }

    private fun mineBlockInstant(blockTask: BlockTask, side: EnumFacing) {
        waitTicks = breakDelay
        packetLimiter.add(System.currentTimeMillis())
        blockTask.updateState(TaskState.PENDING_BREAK)

        defaultScope.launch {
            delay(20L)
            sendMiningPackets(blockTask.blockPos, side)

            if (maxBreaks > 1) {
                tryMultiBreak(blockTask)
            }

            delay(50L * taskTimeout)
            if (blockTask.taskState == TaskState.PENDING_BREAK) {
                mutex.withLock {
                    blockTask.updateState(TaskState.BREAK)
                }
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
                if (packetLimiter.size > TpsCalculator.tickRate * limitFactor) {
                    if (debugMessages == DebugMessages.ALL) {
                        MessageSendHelper.sendChatMessage("$chatName Dropped possible instant mine action @ TPS(${TpsCalculator.tickRate}) Actions(${packetLimiter.size})")
                    }
                    break
                }

                if (task == blockTask) continue
                if (task.taskState != TaskState.BREAK) continue
                if (world.getBlockState(task.blockPos).block != Blocks.NETHERRACK) continue

                val box = AxisAlignedBB(task.blockPos)
                val rayTraceResult = box.isInSight(eyePos, viewVec) ?: continue

                if (handleLiquid(task)) break
                breakCount++
                packetLimiter.add(System.currentTimeMillis())

                defaultScope.launch {
                    sendMiningPackets(task.blockPos, rayTraceResult.sideHit)

                    delay(50L * taskTimeout)
                    if (blockTask.taskState == TaskState.PENDING_BREAK) {
                        mutex.withLock {
                            blockTask.updateState(TaskState.BREAK)
                        }
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
            connection.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, side))
            player.swingArm(EnumHand.MAIN_HAND)
        }
    }

    private fun isInsideBlueprint(pos: BlockPos): Boolean {
        return blueprint.containsKey(pos)
    }

    private fun isInsideBlueprintBuild(pos: BlockPos): Boolean {
        return blueprint[pos]?.let { it == material } ?: false
    }

    fun printSettings() {
        StringBuilder(ignoreBlocks.size + 1).run {
            append("$chatName Settings" +
                "\n §9> §rMain material: §7${material.localizedName}" +
                "\n §9> §rFiller material: §7${fillerMat.localizedName}" +
                "\n §9> §rIgnored Blocks:")

            ignoreBlocks.forEach {
                append("\n     §9> §7$it")
            }

            MessageSendHelper.sendChatMessage(toString())
        }
    }

    fun SafeClientEvent.gatherStatistics(displayText: TextComponent) {
        val runtimeSec = (runtimeMilliSeconds / 1000) + 0.0001
        val distanceDone = startingBlockPos.distanceTo(currentBlockPos).toInt() + totalDistance

        if (showSession) gatherSession(displayText, runtimeSec)

        if (showLifeTime) gatherLifeTime(displayText)

        if (showPerformance) gatherPerformance(displayText, runtimeSec, distanceDone)

        if (showEnvironment) gatherEnvironment(displayText)

        if (showTask) gatherTask(displayText)

        if (showEstimations) gatherEstimations(displayText, runtimeSec, distanceDone)

//        displayText.addLine("by Constructor#9948 aka Avanatiker", primaryColor, size=8)

        if (printDebug) {
            displayText.addLine("Pending", primaryColor)
            addTaskComponentList(displayText, sortedTasks)

            displayText.addLine("Done", primaryColor)
            addTaskComponentList(displayText, doneTasks.values)
        }
    }

    private fun gatherSession(displayText: TextComponent, runtimeSec: Double) {
        val seconds = (runtimeSec % 60.0).toInt().toString().padStart(2, '0')
        val minutes = ((runtimeSec % 3600.0) / 60.0).toInt().toString().padStart(2, '0')
        val hours = (runtimeSec / 3600.0).toInt().toString().padStart(2, '0')

        displayText.addLine("Session", primaryColor)

        displayText.add("    Runtime:", primaryColor)
        displayText.addLine("$hours:$minutes:$seconds", secondaryColor)

        displayText.add("    Direction:", primaryColor)
        displayText.addLine("${startingDirection.displayName} / ${startingDirection.displayNameXY}", secondaryColor)

        if (!anonymizeStats) displayText.add("    Start:", primaryColor)
        if (!anonymizeStats) displayText.addLine("(${startingBlockPos.asString()})", secondaryColor)

        displayText.add("    Session placed / destroyed:", primaryColor)
        displayText.addLine("%,d".format(totalBlocksPlaced) + " / " + "%,d".format(totalBlocksBroken), secondaryColor)



    }

    private fun SafeClientEvent.gatherLifeTime(displayText: TextComponent) {
        val matMined = StatList.getObjectUseStats(material.item)?.let {
            player.statFileWriter.readStat(it)
        } ?: 0
        val enderMined = StatList.getBlockStats(Blocks.ENDER_CHEST)?.let {
            player.statFileWriter.readStat(it)
        } ?: 0
        val netherrackMined = StatList.getBlockStats(Blocks.NETHERRACK)?.let {
            player.statFileWriter.readStat(it)
        } ?: 0
        val pickaxeBroken = StatList.getObjectBreakStats(Items.DIAMOND_PICKAXE)?.let {
            player.statFileWriter.readStat(it)
        } ?: 0

        if (matMined + enderMined + netherrackMined + pickaxeBroken > 0) {
            displayText.addLine("Lifetime", primaryColor)
        }

        if (mode == Mode.HIGHWAY || mode == Mode.FLAT) {
            if (matMined > 0) {
                displayText.add("    ${material.localizedName} placed:", primaryColor)
                displayText.addLine("%,d".format(matMined), secondaryColor)
            }

            if (enderMined > 0) {
                displayText.add("    ${Blocks.ENDER_CHEST.localizedName} mined:", primaryColor)
                displayText.addLine("%,d".format(enderMined), secondaryColor)
            }
        }

        if (netherrackMined > 0) {
            displayText.add("    ${Blocks.NETHERRACK.localizedName} mined:", primaryColor)
            displayText.addLine("%,d".format(netherrackMined), secondaryColor)
        }

        if (pickaxeBroken > 0) {
            displayText.add("    Diamond Pickaxe broken:", primaryColor)
            displayText.addLine("%,d".format(pickaxeBroken), secondaryColor)
        }
    }

    private fun gatherPerformance(displayText: TextComponent, runtimeSec: Double, distanceDone: Double) {
        displayText.addLine("Performance", primaryColor)

        displayText.add("    Placements / s: ", primaryColor)
        displayText.addLine("%.2f SMA(%.2f)".format(totalBlocksPlaced / runtimeSec, simpleMovingAveragePlaces.size / simpleMovingAverageRange.toDouble()), secondaryColor)

        displayText.add("    Breaks / s:", primaryColor)
        displayText.addLine("%.2f SMA(%.2f)".format(totalBlocksBroken / runtimeSec, simpleMovingAverageBreaks.size / simpleMovingAverageRange.toDouble()), secondaryColor)

        displayText.add("    Distance km / h:", primaryColor)
        displayText.addLine("%.3f SMA(%.3f)".format((distanceDone / runtimeSec * 60.0 * 60.0) / 1000.0, (simpleMovingAverageDistance.size / simpleMovingAverageRange * 60.0 * 60.0) / 1000.0), secondaryColor)

        displayText.add("    Food level loss / h:", primaryColor)
        displayText.addLine("%.2f".format(totalBlocksBroken / foodLoss.toDouble()), secondaryColor)

        displayText.add("    Pickaxes / h:", primaryColor)
        displayText.addLine("%.2f".format((durabilityUsages / runtimeSec) * 60.0 * 60.0 / 1561.0), secondaryColor)
    }

    private fun gatherEnvironment(displayText: TextComponent) {
        displayText.addLine("Environment", primaryColor)

        displayText.add("    Materials:", primaryColor)
        displayText.addLine("Main(${material.localizedName}) Filler(${fillerMat.localizedName})", secondaryColor)

        displayText.add("    Dimensions:", primaryColor)
        displayText.addLine("Width($width) Height($height)", secondaryColor)

        displayText.add("    Delays:", primaryColor)
        if (dynamicDelay) {
            displayText.addLine("Place(${placeDelay + extraPlaceDelay}) Break($breakDelay)", secondaryColor)
        } else {
            displayText.addLine("Place($placeDelay) Break($breakDelay)", secondaryColor)
        }
    }

    private fun gatherTask(displayText: TextComponent) {
        sortedTasks.firstOrNull()?.let {
            displayText.addLine("Task", primaryColor)

            displayText.add("    Status:", primaryColor)
            displayText.addLine("${it.taskState}", secondaryColor)

            displayText.add("    Target block:", primaryColor)
            displayText.addLine(it.block.localizedName, secondaryColor)

            if (!anonymizeStats) displayText.add("    Position:", primaryColor)
            if (!anonymizeStats) displayText.addLine("(${it.blockPos.asString()})", secondaryColor)

            displayText.add("    Ticks stuck:", primaryColor)
            displayText.addLine("${it.stuckTicks}", secondaryColor)
        }
    }

    private fun SafeClientEvent.gatherEstimations(displayText: TextComponent, runtimeSec: Double, distanceDone: Double) {
        when (mode) {
            Mode.HIGHWAY, Mode.FLAT -> {
                materialLeft = player.allSlots.countBlock(material)
                fillerMatLeft = player.allSlots.countBlock(fillerMat)
                val indirectMaterialLeft = 8 * player.allSlots.countBlock(Blocks.ENDER_CHEST)

                val pavingLeft = materialLeft / (totalBlocksPlaced.coerceAtLeast(1) / distanceDone.coerceAtLeast(1.0))

                // ToDo: Cache shulker count

//                  val pavingLeftAll = (materialLeft + indirectMaterialLeft) / ((totalBlocksPlaced + 0.001) / (distanceDone + 0.001))

                val secLeft = (pavingLeft).coerceAtLeast(0.0) / (startingBlockPos.distanceTo(currentBlockPos).toInt() / runtimeSec)
                val secondsLeft = (secLeft % 60).toInt().toString().padStart(2, '0')
                val minutesLeft = ((secLeft % 3600) / 60).toInt().toString().padStart(2, '0')
                val hoursLeft = (secLeft / 3600).toInt().toString().padStart(2, '0')

                displayText.addLine("Next refill", primaryColor)
                displayText.add("    ${material.localizedName}:", primaryColor)

                if (material == Blocks.OBSIDIAN) {
                    displayText.addLine("Direct($materialLeft) Indirect($indirectMaterialLeft)", secondaryColor)
                } else {
                    displayText.addLine("$materialLeft", secondaryColor)
                }

                displayText.add("    ${fillerMat.localizedName}:", primaryColor)
                displayText.addLine("$fillerMatLeft", secondaryColor)

                displayText.add("    Distance left:", primaryColor)
                displayText.addLine("${pavingLeft.toInt()}", secondaryColor)

                if (!anonymizeStats) displayText.add("    Destination:", primaryColor)
                if (!anonymizeStats) displayText.addLine("(${currentBlockPos.add(startingDirection.directionVec.multiply(pavingLeft.toInt())).asString()})", secondaryColor)

                displayText.add("    ETA:", primaryColor)
                displayText.addLine("$hoursLeft:$minutesLeft:$secondsLeft", secondaryColor)
            }
            Mode.TUNNEL -> {
                val pickaxesLeft = player.allSlots.countItem<ItemPickaxe>()

                val tunnelingLeft = (pickaxesLeft * 1561) / (durabilityUsages.coerceAtLeast(1) / distanceDone.coerceAtLeast(1.0))

                val secLeft = tunnelingLeft.coerceAtLeast(0.0) / (startingBlockPos.distanceTo(currentBlockPos).toInt() / runtimeSec)
                val secondsLeft = (secLeft % 60).toInt().toString().padStart(2, '0')
                val minutesLeft = ((secLeft % 3600) / 60).toInt().toString().padStart(2, '0')
                val hoursLeft = (secLeft / 3600).toInt().toString().padStart(2, '0')

                displayText.addLine("Destination:", primaryColor)

                displayText.add("    Pickaxes:", primaryColor)
                displayText.addLine("$pickaxesLeft", secondaryColor)

                displayText.add("    Distance left:", primaryColor)
                displayText.addLine("${tunnelingLeft.toInt()}", secondaryColor)

                if (!anonymizeStats) displayText.add("    Destination:", primaryColor)
                if (!anonymizeStats) displayText.addLine("(${currentBlockPos.add(startingDirection.directionVec.multiply(tunnelingLeft.toInt())).asString()})", secondaryColor)

                displayText.add("    ETA:", primaryColor)
                displayText.addLine("$hoursLeft:$minutesLeft:$secondsLeft", secondaryColor)
            }
        }
    }

    private fun resetStats() {
        simpleMovingAveragePlaces.clear()
        simpleMovingAverageBreaks.clear()
        simpleMovingAverageDistance.clear()
        totalBlocksPlaced = 0
        totalBlocksBroken = 0
        totalDistance = 0.0
        runtimeMilliSeconds = 0
        prevFood = 0
        foodLoss = 1
        materialLeft = 0
        fillerMatLeft = 0
        lastToolDamage = 0
        durabilityUsages = 0
    }

    private fun addTaskComponentList(displayText: TextComponent, tasks: Collection<BlockTask>) {
        tasks.forEach {
            displayText.addLine("    ${it.block.localizedName}@(${it.blockPos.asString()}) State: ${it.taskState} Timings: (Threshold: ${it.taskState.stuckThreshold} Timeout: ${it.taskState.stuckTimeout}) Priority: ${it.taskState.ordinal} Stuck: ${it.stuckTicks}")
        }
    }

    class BlockTask(
        val blockPos: BlockPos,
        var taskState: TaskState,
        var block: Block
    ) {
        private var ranTicks = 0
        var stuckTicks = 0; private set
        var shuffle = 0; private set
        var sides = 0; private set
        var startDistance = 0.0; private set
        var eyeDistance = 0.0; private set
        var hitVecDistance = 0.0; private set

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

        fun onStuck(weight: Int = 1) {
            stuckTicks += weight
        }

        fun prepareSortInfo(event: SafeClientEvent, eyePos: Vec3d) {
            sides = when (taskState) {
                TaskState.PLACE -> event.getBetterNeighbour(blockPos, placementSearch, maxReach, true).size
                //TaskState.BREAK ->
                else -> 0
            }

            // ToDo: We need a function that makes a score out of those 3 parameters
            startDistance = startingBlockPos.distanceTo(blockPos)
            eyeDistance = eyePos.distanceTo(blockPos)
            hitVecDistance = (lastHitVec?.distanceTo(blockPos) ?: 0.0)
        }

        fun shuffle() {
            shuffle = nextInt(0, 1000)
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
        LIQUID_SOURCE(100, 100, ColorHolder(114, 27, 255)),
        LIQUID_FLOW(100, 100, ColorHolder(68, 27, 255)),
        BREAKING(100, 100, ColorHolder(240, 222, 60)),
        BREAK(20, 20, ColorHolder(222, 0, 0)),
        PLACE(20, 20, ColorHolder(35, 188, 254)),
        PENDING_BREAK(100, 100, ColorHolder(0, 0, 0)),
        PENDING_PLACE(100, 100, ColorHolder(0, 0, 0))
    }

}