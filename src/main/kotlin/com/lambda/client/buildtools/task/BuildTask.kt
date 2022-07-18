package com.lambda.client.buildtools.task

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.modules.client.BuildTools.anonymizeLog
import com.lambda.client.module.modules.client.BuildTools.fillerMaterials
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.math.CoordinateConverter.asString
import net.minecraft.block.Block
import net.minecraft.block.BlockLiquid
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Items
import net.minecraft.inventory.Slot
import net.minecraft.item.Item
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Vec3d
import java.sql.Timestamp
import java.util.*

abstract class BuildTask(
    val blockPos: BlockPos,
    val targetBlock: Block,
    var isFillerTask: Boolean = false,
    var isContainerTask: Boolean = false,
    var isSupportTask: Boolean = false
) {
    abstract var priority: Int // low value is high priority
    abstract val timeout: Int
    abstract var threshold: Int
    abstract val color: ColorHolder
    abstract var hitVec3d: Vec3d?

    var timeStamp = System.currentTimeMillis()
    var toRemove = false
    var timeTicking = 0
    var timesFailed = 0
    var aabb = AxisAlignedBB(blockPos)
    private var debugInfo: MutableList<Pair<String, String>> = mutableListOf()

    val slotToUseForPlace: Slot? = null
    val desiredItem: Item = Items.AIR
    var destroyAfterPlace = false
    var pickupAfterBreak = false
    val itemIdToPickup = 0

    /**
     * checks if requirements are met for the task
     * @return [Boolean] is true when all requirements are met
     */
    abstract fun SafeClientEvent.isValid(): Boolean

    /**
     * checks for changed circumstances
     * @return [Boolean] is true when changes were made and next task needs to be reconsidered
     */
    abstract fun SafeClientEvent.update(): Boolean

    /**
     * executes the task
     */
    abstract fun SafeClientEvent.execute()

    fun SafeClientEvent.runUpdate(): Boolean {
        aabb = axisAlignedBB
        debugInfo = gatherAllDebugInfo()

        return update()
    }

    fun SafeClientEvent.runExecute() {
        timeTicking++
        execute()
    }

    fun gatherAllDebugInfo(): MutableList<Pair<String, String>> {
        val info: MutableList<Pair<String, String>> = mutableListOf()

        if (!anonymizeLog) info.add(Pair("blockPos", blockPos.asString()))
        info.add(Pair("targetBlock", targetBlock.localizedName))
        if (isFillerTask) info.add(Pair("isFillerTask", ""))
        if (isContainerTask) info.add(Pair("isContainerTask", ""))
        if (isSupportTask) info.add(Pair("isSupportTask", ""))
        info.add(Pair("priority", priority.toString()))
        info.add(Pair("timeout", timeout.toString()))
        info.add(Pair("threshold", threshold.toString()))
        info.add(Pair("color", color.toString()))
        info.add(Pair("hitVec3d", hitVec3d.toString()))
        info.add(Pair("timeStamp", Date(Timestamp(timeStamp).time).toString()))
        info.add(Pair("timeTicking", timeTicking.toString()))

        info.addAll(gatherDebugInfo())

        return info
    }

    override fun toString() = "${javaClass.simpleName} blockPos=(${blockPos.asString()}) targetBlock=${targetBlock.localizedName}${if (isFillerTask) " isFillerTask" else ""}${if (isContainerTask) " isContainerTask" else ""}${if (isSupportTask) " isSupportTask" else ""} ${gatherInfoToString()}"

    /* helper functions */
    val SafeClientEvent.currentBlockState: IBlockState get() = world.getBlockState(blockPos)
    val SafeClientEvent.currentBlock: Block get() = currentBlockState.block
    val SafeClientEvent.isLiquidBlock get() = currentBlock is BlockLiquid
    private val SafeClientEvent.axisAlignedBB: AxisAlignedBB get() = currentBlockState.getSelectedBoundingBox(world, blockPos).also { aabb = it }
    fun itemIsFillerMaterial(item: Item) = item.registryName.toString() in fillerMaterials

    abstract fun gatherInfoToString(): String

    abstract fun gatherDebugInfo(): MutableList<Pair<String, String>>
}