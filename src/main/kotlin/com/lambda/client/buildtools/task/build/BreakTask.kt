package com.lambda.client.buildtools.task.build

import com.lambda.client.buildtools.Statistics
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.RestockHandler.handleRestock
import com.lambda.client.buildtools.task.TaskFactory
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.buildtools.task.TaskProcessor.addTask
import com.lambda.client.buildtools.task.TaskProcessor.convertTo
import com.lambda.client.buildtools.task.TaskProcessor.interactionLimitNotReached
import com.lambda.client.buildtools.task.TaskProcessor.packetLimiter
import com.lambda.client.buildtools.task.TaskProcessor.waitBreak
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.breakDelay
import com.lambda.client.module.modules.client.BuildTools.defaultFillerMat
import com.lambda.client.module.modules.client.BuildTools.ignoreBlocks
import com.lambda.client.module.modules.client.BuildTools.illegalPlacements
import com.lambda.client.module.modules.client.BuildTools.leastTools
import com.lambda.client.module.modules.client.BuildTools.maxReach
import com.lambda.client.module.modules.client.BuildTools.miningSpeedFactor
import com.lambda.client.module.modules.client.BuildTools.multiBreak
import com.lambda.client.module.modules.client.BuildTools.packetFlood
import com.lambda.client.module.modules.client.BuildTools.pickupRadius
import com.lambda.client.module.modules.client.BuildTools.taskTimeout
import com.lambda.client.util.EntityUtils.getDroppedItems
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.*
import com.lambda.client.util.math.VectorUtils
import com.lambda.client.util.math.isInSight
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.world.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.block.Block
import net.minecraft.block.BlockAir
import net.minecraft.block.BlockFire
import net.minecraft.block.BlockLiquid
import net.minecraft.block.state.IBlockState
import net.minecraft.enchantment.EnchantmentHelper
import net.minecraft.init.Enchantments
import net.minecraft.item.ItemPickaxe
import net.minecraft.item.ItemStack
import net.minecraft.network.play.client.CPacketPlayerDigging
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import kotlin.math.ceil

class BreakTask(
    blockPos: BlockPos,
    targetBlock: Block,
    isFillerTask: Boolean = false,
    isContainerTask: Boolean = false,
    isSupportTask: Boolean = false
) : BuildTask(blockPos, targetBlock, isFillerTask, isContainerTask, isSupportTask) {
    private var state = State.INVALID
    private var ticksMined = 0
    private var alreadyCheckedMultiBreak = false
    var breakInfo: BreakInfo? = null
    private var toolToUse = ItemStack.EMPTY
    var collectPos: BlockPos? = null

    override var priority = 1 + state.prioOffset
    override val timeout = 20
    override var threshold = 1
    override val color = state.colorHolder
    override var hitVec3d: Vec3d? = null

    private enum class State(val colorHolder: ColorHolder, val prioOffset: Int) {
        INVALID(ColorHolder(22, 0, 0), 20),
        VALID(ColorHolder(222, 0, 0), 0),
        GET_TOOL(ColorHolder(252, 3, 207), 0),
        BREAK(ColorHolder(222, 0, 0), 0),
        BREAKING(ColorHolder(240, 222, 60), -100),
        PENDING(ColorHolder(42, 0, 0), 20),
        ACCEPTED(ColorHolder(53, 222, 66), 0),
        PICKUP(ColorHolder(), 0)
    }

    override fun SafeClientEvent.isValid() =
        player.onGround
            && interactionLimitNotReached
            && breakInfo != null

    override fun SafeClientEvent.update(): Boolean {
        var wasUpdated = true

        if (isValid() && state == State.INVALID) state = State.VALID
//        priority = 1 + state.prioOffset
        threshold = 1 + ticksNeeded
        hitVec3d = breakInfo?.hitVec3d

        when {
            shouldBeIgnored() || isIllegal() -> {
                convertTo<DoneTask>()
            }
            currentBlock is BlockAir -> {
                convertTo<PlaceTask>()
            }
            currentBlock == targetBlock -> {
                convertTo<DoneTask>()
            }
            isLiquidBlock -> {
                convertTo<PlaceTask>()
            }
            state.ordinal < 2 && updateLiquidNeighbours() -> {
                /* Change already done */
            }
            state.ordinal < 2 && gatherBreakInformation() -> {
                /* Change already done */
            }
            else -> {
                wasUpdated = false
            }
        }

        return wasUpdated
    }

    override fun SafeClientEvent.execute() {
        when (state) {
            State.INVALID -> {
                /* Wait */
            }
            State.VALID -> {
                state = State.GET_TOOL
                execute()
            }
            State.GET_TOOL -> {
                if (equipBestTool(this@BreakTask)) {
                    state = State.BREAK
                    execute()
                }
            }
            State.BREAK -> {
                breakInfo?.let {
                    waitBreak = breakDelay
                    if (it.isInstant) {
                        state = State.PENDING

                        sendMiningPackets(it)

                        if (multiBreak && !alreadyCheckedMultiBreak) breakIntersectingBlocks()

                        defaultScope.launch {
                            delay(threshold * 50L)

                            if (state == State.PENDING) {
                                state = State.INVALID
                                waitBreak += taskTimeout
                            }
                        }
                    } else {
                        state = State.BREAKING

                        sendMiningPackets(it)
                    }

                    ticksMined++
                } ?: run {
                    state = State.INVALID
                }
            }
            State.BREAKING -> {
                breakInfo?.let {
                    sendMiningPackets(it)
                    ticksMined++
                } ?: run {
                    state = State.INVALID
                }
            }
            State.PENDING -> {
                /* Wait */
            }
            State.ACCEPTED -> {
                Statistics.totalBlocksBroken++
                Statistics.simpleMovingAverageBreaks.add(System.currentTimeMillis())

                TaskProcessor.tasks.values.filterIsInstance<BreakTask>().forEach {
                    it.timesFailed = 0
                }

                if (currentBlock is BlockAir) {
                    if (isContainerTask && pickupAfterBreak) {
                        state = State.PICKUP
                        execute()
                        return
                    }

                    convertTo<DoneTask>()
                } else {
                    convertTo<PlaceTask>()
                }
            }
            State.PICKUP -> {
                getCollectingPosition()?.let { goal ->
                    collectPos = goal

                    player.inventorySlots.firstByStack { itemIsFillerMaterial(it.item) }?.let {
                        if (timeTicking > 20) {
                            throwAllInSlot(BuildTools, it)
                            timeTicking = 0
                        }
                    }
                    return
                }

                collectPos = null
                convertTo<DoneTask>()
            }
        }
    }

    private fun SafeClientEvent.getLiquidNeighbours(): List<EnumFacing> =
        EnumFacing.values().filter {
            it != EnumFacing.DOWN
                && world.getBlockState(blockPos.offset(it)).block is BlockLiquid
        }


    private fun SafeClientEvent.updateLiquidNeighbours(): Boolean {
        val newTasks = getLiquidNeighbours().filter { !TaskProcessor.tasks.contains(blockPos.offset(it)) }

        if (newTasks.isNotEmpty()) {
            newTasks.forEach {
                addTask(PlaceTask(blockPos.offset(it), defaultFillerMat, isFillerTask = true))
            }
            return true
        }

        return false
    }

    private fun SafeClientEvent.gatherBreakInformation(): Boolean {
        if (currentBlock is BlockFire) {
            getNeighbour(blockPos, 1, maxReach, !illegalPlacements)?.let {
                breakInfo = BreakInfo(it.pos, it.side, getHitVec(it.pos, it.side), start = true)
                return false
            } ?: run {
                convertTo<PlaceTask>(isFillerTask = true)
                return true
            }
        }

        getMiningSide(blockPos)?.let { side ->
            val eyePos = player.getPositionEyes(1.0f)
            val hitVec = getHitVec(blockPos, side)

            if (eyePos.distanceTo(hitVec) > maxReach) {
                return false
            }

            breakInfo = when {
                ticksNeeded == 1 || ticksMined == 0 || player.capabilities.isCreativeMode -> BreakInfo(blockPos, side, hitVec, start = true, isInstant = true)
                ticksMined < ticksNeeded -> BreakInfo(blockPos, side, hitVec)
                else -> BreakInfo(blockPos, side, hitVec, stop = true)
            }
        }
        return false
    }

    private fun SafeClientEvent.breakIntersectingBlocks() {
        breakInfo?.let { breakInfo ->
            val eyePos = player.getPositionEyes(1.0f)
            val viewVec = breakInfo.hitVec3d.subtract(eyePos).normalize()

            TaskProcessor.tasks.values
                .filterIsInstance<BreakTask>()
                .filter {
                    it != this@BreakTask
                        && (ticksNeeded == 1 || player.capabilities.isCreativeMode)
                        && getLiquidNeighbours().isEmpty()
                }.forEach { foundInstantTask ->
                    val rayTraceResult = AxisAlignedBB(blockPos).isInSight(eyePos, viewVec, range = maxReach.toDouble(), tolerance = 0.0)
                        ?: return@forEach

                    foundInstantTask.breakInfo = BreakInfo(foundInstantTask.blockPos, rayTraceResult.sideHit, breakInfo.hitVec3d, start = true, isInstant = true)
                    foundInstantTask.state = State.BREAK
                    foundInstantTask.alreadyCheckedMultiBreak = true

                    with(foundInstantTask) {
                        execute()
                    }
                }
        }
    }

    private fun SafeClientEvent.sendMiningPackets(breakInfo: BreakInfo) {
        if (breakInfo.start || packetFlood) {
            sendAction(breakInfo, CPacketPlayerDigging.Action.START_DESTROY_BLOCK)
        }
        if (breakInfo.abort) {
            sendAction(breakInfo, CPacketPlayerDigging.Action.ABORT_DESTROY_BLOCK)
        }
        if (breakInfo.stop || packetFlood) {
            sendAction(breakInfo, CPacketPlayerDigging.Action.STOP_DESTROY_BLOCK)
        }
        player.swingArm(EnumHand.MAIN_HAND)
    }

    private fun SafeClientEvent.sendAction(breakInfo: BreakInfo, action: CPacketPlayerDigging.Action) {
        connection.sendPacket(CPacketPlayerDigging(action, breakInfo.pos, breakInfo.side))
        packetLimiter.add(System.currentTimeMillis())
    }

    private fun SafeClientEvent.equipBestTool(breakTask: BreakTask): Boolean {
        if (player.inventorySlots.countItem<ItemPickaxe>() <= leastTools) {
            handleRestock<ItemPickaxe>()
            return false
        }

        with(breakTask) {
            getSlotWithBestTool(currentBlockState)?.let { slotFrom ->
                toolToUse = slotFrom.stack

                slotFrom.toHotbarSlotOrNull()?.let {
                    swapToSlot(it)
                } ?: run {
                    val slotTo = player.hotbarSlots.firstEmpty()?.hotbarSlot ?: 0
                    moveToHotbar(BuildTools, slotFrom.slotNumber, slotTo)
                }
                return true
            }
        }

        return false
    }

    private fun SafeClientEvent.getSlotWithBestTool(blockState: IBlockState) =
        player.inventorySlots.asReversed().maxByOrNull {
            val stack = it.stack
            if (stack.isEmpty) {
                0.0f
            } else {
                var speed = stack.getDestroySpeed(blockState)

                if (speed > 1.0f) {
                    val efficiency = EnchantmentHelper.getEnchantmentLevel(Enchantments.EFFICIENCY, stack)
                    if (efficiency > 0) {
                        speed += efficiency * efficiency + 1.0f
                    }
                }

                speed
            }
        }

    private fun SafeClientEvent.getCollectingPosition(): BlockPos? {
        getDroppedItems(itemIdToPickup, range = pickupRadius.toFloat())
            .minByOrNull { player.getDistance(it) }
            ?.positionVector
            ?.let { itemVec ->
                return VectorUtils.getBlockPosInSphere(itemVec, pickupRadius.toFloat()).asSequence()
                    .filter { pos ->
                        world.isAirBlock(pos.up())
                            && world.isPlaceable(pos)
                            && !world.getBlockState(pos.down()).isReplaceable
                    }
                    .sortedWith(
                        compareBy<BlockPos> {
                            it.distanceSqToCenter(itemVec.x, itemVec.y, itemVec.z)
                        }.thenBy {
                            it.y
                        }
                    ).firstOrNull()
            }
        return null
    }

    fun acceptPacketState(packetBlockState: IBlockState) {
        if ((state == State.PENDING || state == State.BREAKING)
            && packetBlockState.block is BlockAir
        ) {
            state = State.ACCEPTED
        }
    }

    private val SafeClientEvent.relativeHardness: Float
        get() = currentBlockState.getPlayerRelativeBlockHardness(player, world, blockPos)

    private val SafeClientEvent.ticksNeeded: Int
        get() = ceil((1 / relativeHardness) * miningSpeedFactor).toInt()

    private fun SafeClientEvent.shouldBeIgnored() = ignoreBlocks.contains(currentBlock.registryName.toString())
        && !isContainerTask
        && !TaskFactory.isInsideBlueprintBuilding(blockPos)

    private fun SafeClientEvent.isIllegal() = currentBlockState.getBlockHardness(world, blockPos) == -1.0f

    override fun gatherInfoToString() = "state=${state.name} ticksMined=$ticksMined${if (alreadyCheckedMultiBreak) " alreadyCheckedMultiBreak" else ""}${if (!toolToUse.isEmpty) toolToUse.displayName else ""}"

    override fun gatherDebugInfo(): MutableList<Pair<String, String>> {
        val data: MutableList<Pair<String, String>> = mutableListOf()

        data.add(Pair("state", state.name))
        data.add(Pair("ticksMined", ticksMined.toString()))
        data.add(Pair("checkedMB", alreadyCheckedMultiBreak.toString()))
        breakInfo?.let { data.add(Pair("breakInfo", it.toString())) }
        if (!toolToUse.isEmpty) data.add(Pair("toolToUse", toolToUse.displayName))

        return data
    }

    data class BreakInfo(
        val pos: BlockPos,
        val side: EnumFacing,
        val hitVec3d: Vec3d,
        var start: Boolean = false,
        var stop: Boolean = false,
        val abort: Boolean = false,
        val isInstant: Boolean = false,
    )
}