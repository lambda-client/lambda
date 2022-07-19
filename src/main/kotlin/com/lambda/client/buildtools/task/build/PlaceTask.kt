package com.lambda.client.buildtools.task.build

import com.lambda.client.LambdaMod
import com.lambda.client.buildtools.Statistics
import com.lambda.client.buildtools.pathfinding.Navigator
import com.lambda.client.buildtools.pathfinding.strategies.ScaffoldStrategy
import com.lambda.client.buildtools.task.BuildTask
import com.lambda.client.buildtools.task.RestockHandler.getShulkerWith
import com.lambda.client.buildtools.task.RestockHandler.restockItem
import com.lambda.client.buildtools.task.TaskProcessor
import com.lambda.client.buildtools.task.TaskProcessor.addTask
import com.lambda.client.buildtools.task.TaskProcessor.convertTo
import com.lambda.client.buildtools.task.TaskProcessor.interactionLimitNotReached
import com.lambda.client.buildtools.task.TaskProcessor.waitPlace
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools
import com.lambda.client.module.modules.client.BuildTools.anonymizeLog
import com.lambda.client.module.modules.client.BuildTools.defaultFillerMat
import com.lambda.client.module.modules.client.BuildTools.fakeSounds
import com.lambda.client.module.modules.client.BuildTools.illegalPlacements
import com.lambda.client.module.modules.client.BuildTools.leastEnder
import com.lambda.client.module.modules.client.BuildTools.maxReach
import com.lambda.client.module.modules.client.BuildTools.placeDelay
import com.lambda.client.module.modules.client.BuildTools.placementSearch
import com.lambda.client.module.modules.client.BuildTools.taskTimeout
import com.lambda.client.module.modules.player.InventoryManager.ejectList
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.items.*
import com.lambda.client.util.math.CoordinateConverter.asString
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.world.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.block.Block
import net.minecraft.block.Block.getBlockFromName
import net.minecraft.block.BlockEnderChest
import net.minecraft.block.BlockLiquid
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.inventory.Slot
import net.minecraft.network.play.client.CPacketEntityAction
import net.minecraft.network.play.client.CPacketPlayerTryUseItemOnBlock
import net.minecraft.util.EnumFacing
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d

class PlaceTask(
    blockPos: BlockPos,
    targetBlock: Block,
    isFillerTask: Boolean = false,
    isContainerTask: Boolean = false,
    isSupportTask: Boolean = false
) : BuildTask(blockPos, targetBlock, isFillerTask, isContainerTask, isSupportTask) {
    private var state = State.INVALID
    var placeInfo: PlaceInfo? = null
    private val SafeClientEvent.isLiquidSource get() = isLiquidBlock && currentBlockState.getValue(BlockLiquid.LEVEL) == 0
    private val SafeClientEvent.placeInfoSequence get() = getNeighbourSequence(blockPos, placementSearch, maxReach, !illegalPlacements)

    override var priority = 2
    override var timeout = 20
    override var threshold = 20
    override var color = state.colorHolder
    override var hitVec3d: Vec3d? = null

    enum class State(val colorHolder: ColorHolder, val prioOffset: Int) {
        INVALID(ColorHolder(16, 74, 94), 10),
        VALID(ColorHolder(35, 188, 254), 0),
        GET_BLOCK(ColorHolder(252, 3, 207), 0),
        PLACE(ColorHolder(35, 188, 254), 0),
        PENDING(ColorHolder(42, 0, 0), 20),
        ACCEPTED(ColorHolder(53, 222, 66), 0)
    }

    override fun SafeClientEvent.isValid() =
        interactionLimitNotReached
            && placeInfo != null
            && world.checkNoEntityCollision(aabb, player)

    override fun SafeClientEvent.update(): Boolean {
        var wasUpdated = true

        priority = 2 + state.prioOffset + if (isLiquidSource) 10 else 0
        color = state.colorHolder
        if (placeInfoSequence.size == 1) placeInfo = placeInfoSequence.firstOrNull()
        hitVec3d = placeInfo?.hitVec

        if (isValid()
            && state == State.INVALID
        ) state = State.VALID

        when {
            currentBlock == targetBlock -> {
                state = State.ACCEPTED
                return false
            }
            isSupportTask && alreadyIsSupported() -> {
                convertTo<DoneTask>()
            }
            isFillerTask && alreadyIsFilled() -> {
                convertTo<DoneTask>()
            }
            !currentBlockState.isReplaceable -> {
                convertTo<BreakTask>()
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
//                if (placeInfoSequence.isNotEmpty()) {
//                    placeInfoSequence.filter {
//                        !TaskProcessor.tasks.containsKey(it.placedPos)
//                    }.forEach {
//                        addTask(PlaceTask(it.placedPos, defaultFillerMat, isFillerTask = true))
//                    }
//                    return
//                }
//
//                Navigator.changeStrategy<ScaffoldStrategy>()
            }
            State.VALID -> {
                state = State.GET_BLOCK
                execute()
            }
            State.GET_BLOCK -> {
                if (targetBlock is BlockEnderChest
                    && player.inventorySlots.countBlock<BlockEnderChest>() <= leastEnder
                ) {
                    restockItem(Blocks.ENDER_CHEST.item)
                    return
                }

                if (equipBlockToPlace()) {
                    state = State.PLACE
                    execute()
                }
            }
            State.PLACE -> {
                placeInfo?.let {
                    waitPlace = placeDelay

                    state = State.PENDING

                    sendPlacingPackets(it.pos, it.side, getHitVecOffset(it.side))
                }
            }
            State.PENDING -> {
                /* Wait */
            }
            State.ACCEPTED -> {
                Statistics.totalBlocksPlaced++
                Statistics.simpleMovingAveragePlaces.add(System.currentTimeMillis())

                if (fakeSounds) {
                    val soundType = currentBlock.soundType
                    world.playSound(
                        player,
                        blockPos,
                        soundType.placeSound,
                        SoundCategory.BLOCKS,
                        (soundType.getVolume() + 1.0f) / 2.0f,
                        soundType.getPitch() * 0.8f
                    )
                }

                TaskProcessor.tasks.values.filterIsInstance<PlaceTask>().forEach {
                    it.timesFailed = 0
                }

                if (isContainerTask) {
                    if (destroyAfterPlace) {
                        convertTo<BreakTask>()
                    } else {
                        convertTo<RestockTask>()
                    }
                    return
                }

                convertTo<DoneTask>()
            }
        }
    }

    private fun SafeClientEvent.equipBlockToPlace(): Boolean {
        if (isContainerTask) {
            getShulkerWith(player.inventorySlots, desiredItem)?.let {
                swapToSlotOrMove(it)
                return true
            }
        }

        if (swapToItemOrMove(BuildTools, targetBlock.item)) {
            return true
        }

        if (isFillerTask) {
            ejectList.forEach { stringName ->
                getBlockFromName(stringName)?.let {
                    if (swapToBlockOrMove(BuildTools, it)) return true
                }
            }
        }

        restockItem(targetBlock.item)
        return false
    }

    private fun SafeClientEvent.swapToSlotOrMove(slot: Slot) {
        slot.toHotbarSlotOrNull()?.let {
            swapToSlot(it)
        } ?: run {
            val slotTo = player.hotbarSlots.firstEmpty()?.hotbarSlot ?: 0
            moveToHotbar(BuildTools, slot.slotNumber, slotTo)
        }
    }

    private fun SafeClientEvent.sendPlacingPackets(blockPos: BlockPos, side: EnumFacing, hitVecOffset: Vec3d) {
        val isBlackListed = currentBlock in blockBlacklist

        if (isBlackListed) {
            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
        }

        val placePacket = CPacketPlayerTryUseItemOnBlock(blockPos, side, EnumHand.MAIN_HAND, hitVecOffset.x.toFloat(), hitVecOffset.y.toFloat(), hitVecOffset.z.toFloat())
        connection.sendPacket(placePacket)
        player.swingArm(EnumHand.MAIN_HAND)

        if (isBlackListed) {
            connection.sendPacket(CPacketEntityAction(player, CPacketEntityAction.Action.START_SNEAKING))
        }

        defaultScope.launch {
            delay(threshold * 50L)

            if (state == State.PENDING) {
                state = State.INVALID
                waitPlace += taskTimeout
            }
        }
    }

    fun acceptPacketState(packetBlockState: IBlockState) {
        if (state == State.PENDING
            && (targetBlock == packetBlockState.block || isFillerTask)
        ) {
            state = State.ACCEPTED
        }
    }

    private fun SafeClientEvent.alreadyIsFilled() = world.getCollisionBox(blockPos) != null
    private fun alreadyIsSupported() = TaskProcessor.tasks[blockPos.up()] is DoneTask

    override fun gatherInfoToString() = "state=$state"

    override fun gatherDebugInfo(): MutableList<Pair<String, String>> {
        val data: MutableList<Pair<String, String>> = mutableListOf()

        data.add(Pair("state", state.name))

        placeInfo?.let {
            if (!anonymizeLog) data.add(Pair("pos", it.pos.asString()))
            data.add(Pair("side", it.side.name))
            data.add(Pair("distance", "%.2f".format(it.dist)))
            data.add(Pair("hitVecOffset", it.hitVecOffset.toString()))
            if (!anonymizeLog) data.add(Pair("hitVec", it.hitVec.toString()))
            if (!anonymizeLog) data.add(Pair("placedPos", it.placedPos.asString()))
        }

        return data
    }
}