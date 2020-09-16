package me.zeroeightsix.kami.module.modules.misc

import baritone.api.BaritoneAPI
import me.zeroeightsix.kami.event.events.RenderEvent
import me.zeroeightsix.kami.manager.mangers.PlayerPacketManager
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
import me.zeroeightsix.kami.util.math.Vec2f
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
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * @author Avanatiker
 * @since 20/08/2020
 */

@Module.Info(
        name = "HighwayTools",
        description = "Be the grief a step a head.",
        category = Module.Category.MISC
)
object HighwayTools : Module() {

    private val mode = register(Settings.e<Mode>("Mode", Mode.HIGHWAY))
    private val page = register(Settings.e<Page>("Page", Page.MAIN))

    // main settings
    private val blocksPerTick = register(Settings.integerBuilder("BlocksPerTick").withMinimum(1).withValue(1).withMaximum(10).withVisibility { page.value == Page.MAIN }.build())
    private val tickDelayPlace = register(Settings.integerBuilder("TickDelayPlace").withMinimum(0).withValue(0).withMaximum(15).withVisibility { page.value == Page.MAIN }.build())
    private val tickDelayBreak = register(Settings.integerBuilder("TickDelayBreak").withMinimum(0).withValue(0).withMaximum(15).withVisibility { page.value == Page.MAIN }.build())
    val baritoneMode = register(Settings.booleanBuilder("Automatic").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val maxReach = register(Settings.doubleBuilder("Reach").withValue(4.4).withMinimum(1.0).withVisibility { page.value == Page.MAIN }.build())
    private val noViewReset = register(Settings.booleanBuilder("NoViewReset").withValue(false).withVisibility { page.value == Page.MAIN }.build())
    //private val spoofRotations = register(Settings.booleanBuilder("SpoofRotations").withValue(true).withVisibility { page.value == Page.MAIN }.build())
    private val spoofing = register(Settings.enumBuilder(SpoofMode::class.java).withName("SpoofMode").withValue(SpoofMode.VIEWLOCK).withVisibility { page.value == Page.CONFIG }.build())
    //private val spoofLookAt = register(Settings.booleanBuilder("SpoofLookAt").withValue(true).withVisibility { page.value == Page.MAIN }.build())

    // build settings
    val clearSpace = register(Settings.booleanBuilder("ClearSpace").withValue(true).withVisibility { page.value == Page.BUILD }.build())
    var clearHeight = register(Settings.integerBuilder("ClearHeight").withMinimum(1).withValue(4).withMaximum(6).withVisibility { page.value == Page.BUILD && clearSpace.value }.build())
    private var buildWidth = register(Settings.integerBuilder("BuildWidth").withMinimum(1).withValue(7).withMaximum(9).withVisibility { page.value == Page.BUILD }.build())
    private val rims = register(Settings.booleanBuilder("Rims").withValue(true).withVisibility { page.value == Page.BUILD }.build())
    private var rimHeight = register(Settings.integerBuilder("RimHeight").withMinimum(1).withValue(1).withMaximum(clearHeight.value).withVisibility { rims.value && page.value == Page.BUILD }.build())
    private val cornerBlock = register(Settings.booleanBuilder("CornerBlock").withValue(true).withVisibility { page.value == Page.BUILD }.build())

    // config settings
    private val info = register(Settings.booleanBuilder("ShowInfo").withValue(true).withVisibility { page.value == Page.CONFIG }.build())
    private val printDebug = register(Settings.booleanBuilder("ShowQueue").withValue(false).withVisibility { page.value == Page.CONFIG }.build())
    private val debugMessages = register(Settings.enumBuilder(DebugMessages::class.java).withName("Debug").withValue(DebugMessages.IMPORTANT).withVisibility { page.value == Page.CONFIG }.build())
    private val goalRender = register(Settings.booleanBuilder("GoalRender").withValue(false).withVisibility { page.value == Page.CONFIG }.build())
    private val filled = register(Settings.booleanBuilder("Filled").withValue(true).withVisibility { page.value == Page.CONFIG }.build())
    private val outline = register(Settings.booleanBuilder("Outline").withValue(true).withVisibility { page.value == Page.CONFIG }.build())
    private val aFilled = register(Settings.integerBuilder("FilledAlpha").withMinimum(0).withValue(31).withMaximum(255).withVisibility { filled.value && page.value == Page.CONFIG }.build())
    private val aOutline = register(Settings.integerBuilder("OutlineAlpha").withMinimum(0).withValue(127).withMaximum(255).withVisibility { outline.value && page.value == Page.CONFIG }.build())

    // other settings
    var ignoreBlocks = mutableListOf(Blocks.STANDING_SIGN, Blocks.WALL_SIGN, Blocks.STANDING_BANNER, Blocks.WALL_BANNER, Blocks.BEDROCK, Blocks.PORTAL)
    var material: Block = Blocks.OBSIDIAN
    var fillerMat: Block = Blocks.NETHERRACK
    private var playerHotbarSlot = -1
    private var lastHotbarSlot = -1
    private lateinit var buildDirectionSaved: Cardinal
    private var baritoneSettingAllowPlace = false
    private var baritoneSettingRenderGoal = false

    // runtime vars
    private val compareByPriority: Comparator<BlockTask> = compareBy { it.priority }
    var blockQueue: PriorityQueue<BlockTask> = PriorityQueue(compareByPriority)
    private val doneQueue: Queue<BlockTask> = LinkedList<BlockTask>()
    private var blockOffsets = mutableListOf<Pair<BlockPos, Boolean>>()
    private var waitTicks = 0
    private var blocksPlaced = 0
    var pathing = false
    private var isSneaking = false
    private var stuckDetector = 0
    private lateinit var currentBlockPos: BlockPos
    private lateinit var startingBlockPos: BlockPos

    // stats
    private var totalBlocksPlaced = 0
    private var totalBlocksDestroyed = 0
    private var totalBlocksDistance = 0

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

        if (baritoneMode.value) {
            baritoneSettingAllowPlace = BaritoneAPI.getSettings().allowPlace.value
            BaritoneAPI.getSettings().allowPlace.value = false
            if (!goalRender.value) {
                baritoneSettingRenderGoal = BaritoneAPI.getSettings().renderGoal.value
                BaritoneAPI.getSettings().renderGoal.value = false
            }
        }

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


        if (baritoneMode.value) {
            BaritoneAPI.getSettings().allowPlace.value = baritoneSettingAllowPlace
            if (!goalRender.value) BaritoneAPI.getSettings().renderGoal.value = baritoneSettingRenderGoal
            val baritoneProcess = BaritoneAPI.getProvider().primaryBaritone.pathingControlManager.mostRecentInControl()
            if (baritoneProcess.isPresent && baritoneProcess.get() == HighwayToolsProcess) {
                baritoneProcess.get().onLostControl()
            }
        }
        printDisable()
        totalBlocksPlaced = 0
        totalBlocksDestroyed = 0
        totalBlocksDistance = 0
    }

    override fun onUpdate() {
        if (mc.playerController == null) return

        if (baritoneMode.value) {
            pathing = BaritoneAPI.getProvider().primaryBaritone.pathingBehavior.isPathing
            if (!isDone()) {
                if (!doTask()) {
                    if (!pathing && !ModuleManager.getModuleT(LagNotifier::class.java)!!.paused && !ModuleManager.getModuleT(AutoObsidian::class.java)!!.active) {
                        stuckDetector += 1
                        var tmpQueue: Queue<BlockTask> = LinkedList<BlockTask>(blockQueue)
                        tmpQueue = LinkedList<BlockTask>(tmpQueue.shuffled())
                        blockQueue.clear()
                        blockQueue.addAll(tmpQueue)
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
                if (checkTasks() && !pathing) {
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
        } else {
            if (currentBlockPos == mcPlayerPosFloored(mc)) {
                if (!doTask()) {
                    var tmpQueue: Queue<BlockTask> = LinkedList<BlockTask>(blockQueue)
                    tmpQueue = LinkedList<BlockTask>(tmpQueue.shuffled())
                    blockQueue.clear()
                    blockQueue.addAll(tmpQueue)
                }
            } else {
                currentBlockPos = mcPlayerPosFloored(mc)
                if (abs((buildDirectionSaved.ordinal - getPlayerCardinal(mc).ordinal) % 8) == 4) buildDirectionSaved = getPlayerCardinal(mc)
                refreshData()
            }
        }

        if (printDebug.value) {
            printDebug()
        }
    }

    private fun addTask(blockPos: BlockPos, taskState: TaskState, material: Block, priority: Int) {
        blockQueue.add(BlockTask(blockPos, taskState, material, priority))
    }

    private fun addDoneTask(blockPos: BlockPos, material: Block) {
        doneQueue.add(BlockTask(blockPos, TaskState.DONE, material, 1))
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
                        if (mc.world.rayTraceBlocks(mc.player.getPositionEyes(1f), Vec3d(blockAction.blockPos).add(0.5, 0.5, 0.5)) == null) return false

                        val block = mc.world.getBlockState(blockAction.blockPos).block

                        for (b in ignoreBlocks) {
                            if (block::class == b!!::class) {
                                blockQueue.poll()
                                blockAction.taskState = TaskState.DONE
                                blockAction.priority = 1
                                doneQueue.add(blockAction)
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

                                if (!found && BlockUtils.hasNeighbour(neighbour) && sqrt(mc.player.getDistanceSqToCenter(neighbour)) < maxReach.value) addTask(neighbour, TaskState.PLACE, fillerMat, 2)
                            }
                        }

                        when (block) {
                            is BlockAir -> {
                                blockAction.taskState = TaskState.BROKE
                                doTask()
                            }
                            is BlockLiquid -> {

                                var insideBuild = false
                                for ((pos, build) in blockOffsets) {
                                    if (blockAction.blockPos == pos && build) {
                                        insideBuild = true
                                    }
                                }

                                blockQueue.poll()
                                blockAction.taskState = TaskState.PLACE
                                blockAction.priority = 2
                                if (insideBuild) {
                                    blockAction.block = Blocks.OBSIDIAN
                                } else {
                                    blockAction.block = Blocks.NETHERRACK
                                }
                                blockQueue.add(blockAction)
                                doTask()
                            }
                            else -> {
                                if(!mineBlock(blockAction.blockPos, true)) return false
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
                                blockQueue.poll()
                                blockAction.taskState = TaskState.PLACE
                                blockAction.priority = 4
                                blockQueue.add(blockAction)
                            } else {
                                blockQueue.poll()
                                blockAction.taskState = TaskState.DONE
                                blockAction.priority = 1
                                doneQueue.add(blockAction)
                            }

                            doTask()
                        } else {
                            blockAction.taskState = TaskState.BREAK
                        }
                    }
                    TaskState.PLACE -> {
                        if (mc.world.rayTraceBlocks(mc.player.getPositionEyes(1f), Vec3d(blockAction.blockPos).add(0.5, 0.5, 0.5)) == null) return false

                        val block = mc.world.getBlockState(blockAction.blockPos).block

                        if (blockAction.block is BlockAir && block !is BlockLiquid) {
                            blockQueue.poll()
                            return true
                        }

                        if (placeBlock(blockAction.blockPos, blockAction.block)) {
                            blockAction.taskState = TaskState.PLACED
                            if (blocksPerTick.value > blocksPlaced + 1) {
                                blocksPlaced++
                                doTask()
                            } else {
                                blocksPlaced = 0
                            }

                            waitTicks = tickDelayPlace.value
                            totalBlocksPlaced++
                        } else {
                            return false
                        }
                    }
                    TaskState.PLACED -> {
                        if (blockAction.block::class == material::class) {
                            val block = mc.world.getBlockState(blockAction.blockPos).block

                            if (block !is BlockAir) {
                                blockQueue.poll()
                                blockAction.taskState = TaskState.DONE
                                blockAction.priority = 1
                                doneQueue.add(blockAction)
                            } else {
                                blockAction.taskState = TaskState.PLACE
                            }
                        } else {
                            blockQueue.poll()
                            blockAction.taskState = TaskState.BREAK
                            blockAction.priority = 3
                            blockQueue.add(blockAction)
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
                (!b && block in ignoreBlocks) -> addDoneTask(a, getBlockById(0))
                (b && block::class == material::class) -> addDoneTask(a, material)
                (!b && block is BlockAir) -> addDoneTask(a, getBlockById(0))
                (b && block !is BlockAir && block::class != material::class) -> addTask(a, TaskState.BREAK, material, 3)
                (!b && block !is BlockAir) -> addTask(a, TaskState.BREAK, getBlockById(0), 3)
                (b && block is BlockAir) -> addTask(a, TaskState.PLACE, material, 4)
            }
        }
    }

    private fun mineBlock(pos: BlockPos, pre: Boolean): Boolean {
        if (InventoryUtils.getSlotsHotbar(278) == null && InventoryUtils.getSlotsNoHotbar(278) != null) {
            InventoryUtils.moveToHotbar(278, 130)
            return false
        } else if (InventoryUtils.getSlots(0, 35, 278) == null) {
            MessageSendHelper.sendChatMessage("$chatName No Pickaxe was found in inventory")
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            return false
        }
        InventoryUtils.swapSlotToItem(278)

        val rayTrace = mc.world.rayTraceBlocks(mc.player.getPositionEyes(1f), Vec3d(pos).add(0.5, 0.5, 0.5)) ?: return false
        when (spoofing.value) {
            SpoofMode.SPOOF -> {
                val lookAt = RotationUtils.getRotationTo(Vec3d(pos).add(0.5, 0.5, 0.5).add(Vec3d(rayTrace.sideHit.directionVec).scale(0.5)), true)
                PlayerPacketManager.addPacket(this, PlayerPacketManager.PlayerPacket(rotating = true, rotation = Vec2f(lookAt.x.toFloat(), lookAt.y.toFloat())))
            }
            SpoofMode.VIEWLOCK -> {
                val lookAt = RotationUtils.getRotationTo(Vec3d(pos).add(0.5, 0.5, 0.5).add(Vec3d(rayTrace.sideHit.directionVec).scale(0.5)), true)
                mc.player.rotationYaw = lookAt.x.toFloat()
                mc.player.rotationPitch = lookAt.y.toFloat()
            }
        }

        if (pre) {
            mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.START_DESTROY_BLOCK, pos, rayTrace.sideHit))
        } else {
            mc.connection!!.sendPacket(CPacketPlayerDigging(CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK, pos, rayTrace.sideHit))
        }
        mc.player.swingArm(EnumHand.MAIN_HAND)
        return true
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
                InventoryUtils.quickMoveSlot(x)
            }
            //InventoryUtils.quickMoveSlot(1, (tickDelay.value * 16).toLong())
        } else if (InventoryUtils.getSlots(0, 35, getIdFromBlock(mat)) == null) {
            MessageSendHelper.sendChatMessage("$chatName No ${mat.localizedName} was found in inventory")
            mc.getSoundHandler().playSound(PositionedSoundRecord.getRecord(SoundEvents.ENTITY_EXPERIENCE_ORB_PICKUP, 1.0f, 1.0f))
            disable()
            return false
        }
        InventoryUtils.swapSlotToItem(getIdFromBlock(mat))
        //val hitVec = Vec3d(neighbour).add(0.5, 0.5, 0.5).add(Vec3d(opposite.directionVec).scale(0.5))
        val hitVec = Vec3d(neighbour).add(0.5, 0.5, 0.5).add(Vec3d(opposite.directionVec).scale(0.5))
        val neighbourBlock = mc.world.getBlockState(neighbour).block
        if (!isSneaking && BlockUtils.blackList.contains(neighbourBlock) || BlockUtils.shulkerList.contains(neighbourBlock)) {
            mc.player.connection.sendPacket(CPacketEntityAction(mc.player, CPacketEntityAction.Action.START_SNEAKING))
            isSneaking = true
        }

        when (spoofing.value) {
            SpoofMode.SPOOF -> {
                val lookAt = RotationUtils.getRotationTo(hitVec, true)
                PlayerPacketManager.addPacket(this, PlayerPacketManager.PlayerPacket(rotating = true, rotation = Vec2f(lookAt.x.toFloat(), lookAt.y.toFloat())))
            }
            SpoofMode.VIEWLOCK -> {
                val lookAt = RotationUtils.getRotationTo(hitVec, true)
                mc.player.rotationYaw = lookAt.x.toFloat()
                mc.player.rotationPitch = lookAt.y.toFloat()
            }
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
                    "\n    §9> §7Direction: §a${buildDirectionSaved.cardinalName}§r"

            message += if (buildDirectionSaved.isDiagonal) {
                "\n    §9> §7Coordinates: §a${startingBlockPos.x} ${startingBlockPos.z}§r"
            } else {
                if (buildDirectionSaved == Cardinal.NEG_Z || buildDirectionSaved == Cardinal.POS_Z) {
                    "\n    §9> §7Coordinate: §a${startingBlockPos.x}§r"
                } else {
                    "\n    §9> §7Coordinate: §a${startingBlockPos.z}§r"
                }
            }
        } else {
            message += "$chatName Module started."
        }
        MessageSendHelper.sendChatMessage(message)
    }

    private fun printDisable() {
        var message = ""
        if (info.value) {
            message += "$chatName Module stopped." +
                    "\n    §9> §7Placed blocks: §a$totalBlocksPlaced§r" +
                    "\n    §9> §7Destroyed blocks: §a$totalBlocksDestroyed§r"
            if (baritoneMode.value) message += "\n    §9> §7Distance: §a$totalBlocksDistance§r"
        } else {
            message += "$chatName Module stopped."
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

                if (baritoneMode.value) {
                    cursor = relativeDirection(cursor, 1, 0)
                    blockOffsets.add(Pair(cursor, true))
                }
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
        HIGHWAY, FLAT
    }

    private enum class SpoofMode {
        NONE, SPOOF, VIEWLOCK
    }

    private enum class Page {
        MAIN, BUILD, CONFIG
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

