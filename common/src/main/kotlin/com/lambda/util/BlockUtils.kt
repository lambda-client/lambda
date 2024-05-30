package com.lambda.util

import com.lambda.context.SafeContext
import com.lambda.util.item.ItemUtils.block
import com.lambda.util.item.ItemUtils.shulkerBoxes
import net.minecraft.block.Block
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.client.world.ClientWorld
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.item.Item
import net.minecraft.util.math.*
import kotlin.math.floor

object BlockUtils {
    private val shulkerBlocks = shulkerBoxes.map { it.block }

    val interactionBlacklist = mutableSetOf(
        Blocks.CHEST,
        Blocks.TRAPPED_CHEST,
        Blocks.ENDER_CHEST,
        Blocks.BARREL,
        Blocks.REPEATER,
        Blocks.COMPARATOR,
        Blocks.DISPENSER,
        Blocks.DROPPER,
        Blocks.HOPPER,
        Blocks.BREWING_STAND,
        Blocks.FURNACE,
        Blocks.BLAST_FURNACE,
        Blocks.SMOKER,
        Blocks.CRAFTING_TABLE,
        Blocks.ANVIL,
        Blocks.CHIPPED_ANVIL,
        Blocks.DAMAGED_ANVIL,
        Blocks.ENCHANTING_TABLE,
        Blocks.BEACON,
        Blocks.BELL,
        Blocks.CAMPFIRE,
        Blocks.SOUL_CAMPFIRE,
        Blocks.JUKEBOX,
        Blocks.NOTE_BLOCK,
    ).apply { addAll(shulkerBlocks) }

    val signs = setOf(
        Blocks.OAK_SIGN,
        Blocks.BIRCH_SIGN,
        Blocks.ACACIA_SIGN,
        Blocks.CHERRY_SIGN,
        Blocks.JUNGLE_SIGN,
        Blocks.DARK_OAK_SIGN,
        Blocks.MANGROVE_SIGN,
        Blocks.BAMBOO_SIGN,
    )

    val wallSigns = setOf(
        Blocks.OAK_WALL_SIGN,
        Blocks.BIRCH_WALL_SIGN,
        Blocks.ACACIA_WALL_SIGN,
        Blocks.CHERRY_WALL_SIGN,
        Blocks.JUNGLE_WALL_SIGN,
        Blocks.DARK_OAK_WALL_SIGN,
        Blocks.MANGROVE_WALL_SIGN,
        Blocks.BAMBOO_WALL_SIGN,
    )

    val hangingSigns = setOf(
        Blocks.OAK_HANGING_SIGN,
        Blocks.BIRCH_HANGING_SIGN,
        Blocks.ACACIA_HANGING_SIGN,
        Blocks.CHERRY_HANGING_SIGN,
        Blocks.JUNGLE_HANGING_SIGN,
        Blocks.DARK_OAK_HANGING_SIGN,
        Blocks.MANGROVE_HANGING_SIGN,
        Blocks.BAMBOO_HANGING_SIGN,
    )

    val hangingWallSigns = setOf(
        Blocks.OAK_WALL_HANGING_SIGN,
        Blocks.BIRCH_WALL_HANGING_SIGN,
        Blocks.ACACIA_WALL_HANGING_SIGN,
        Blocks.CHERRY_WALL_HANGING_SIGN,
        Blocks.JUNGLE_WALL_HANGING_SIGN,
        Blocks.DARK_OAK_WALL_HANGING_SIGN,
        Blocks.MANGROVE_WALL_HANGING_SIGN,
        Blocks.BAMBOO_WALL_HANGING_SIGN,
    )

    val fluids = listOf(
        Fluids.LAVA,
        Fluids.FLOWING_LAVA,
        Fluids.WATER,
        Fluids.FLOWING_WATER,
        Fluids.EMPTY,
    )

    val allSigns = signs + wallSigns + hangingSigns + hangingWallSigns

    fun BlockPos.blockState(world: ClientWorld): BlockState = world.getBlockState(this)
    fun BlockPos.fluidState(world: ClientWorld): FluidState = world.getFluidState(this)
    fun BlockPos.blockEntity(world: ClientWorld) = world.getBlockEntity(this)
    fun SafeContext.instantBreakable(blockState: BlockState, blockPos: BlockPos): Boolean {
        val ticksNeeded = 1 / blockState.calcBlockBreakingDelta(player, world, blockPos)
//        info("State: $blockState Ticks to break: $ticksNeeded")
        return ticksNeeded <= 1 && ticksNeeded != 0f
    }
    val Vec3i.blockPos: BlockPos get() = BlockPos(this)
    val Block.item: Item get() = asItem()
    val Vec3d.flooredPos: BlockPos get() = BlockPos(floor(x).toInt(), floor(y).toInt(), floor(z).toInt())
    fun BlockPos.vecOf(direction: Direction): Vec3d = toCenterPos().add(Vec3d.of(direction.vector).multiply(0.5))
    fun BlockPos.offset(eightWayDirection: EightWayDirection, amount: Int): BlockPos =
        add(eightWayDirection.offsetX * amount, 0, eightWayDirection.offsetZ * amount)
}
