/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.util

import com.lambda.context.SafeContext
import com.lambda.util.player.gamemode
import net.minecraft.block.AbstractCauldronBlock
import net.minecraft.block.AbstractFurnaceBlock
import net.minecraft.block.AbstractSignBlock
import net.minecraft.block.AnvilBlock
import net.minecraft.block.BarrelBlock
import net.minecraft.block.BeaconBlock
import net.minecraft.block.BedBlock
import net.minecraft.block.BeehiveBlock
import net.minecraft.block.BellBlock
import net.minecraft.block.BlockState
import net.minecraft.block.Blocks
import net.minecraft.block.BrewingStandBlock
import net.minecraft.block.ButtonBlock
import net.minecraft.block.CakeBlock
import net.minecraft.block.CampfireBlock
import net.minecraft.block.CandleBlock
import net.minecraft.block.CandleCakeBlock
import net.minecraft.block.CartographyTableBlock
import net.minecraft.block.CaveVinesBodyBlock
import net.minecraft.block.CaveVinesHeadBlock
import net.minecraft.block.ChestBlock
import net.minecraft.block.ChiseledBookshelfBlock
import net.minecraft.block.CommandBlock
import net.minecraft.block.ComparatorBlock
import net.minecraft.block.ComposterBlock
import net.minecraft.block.CrafterBlock
import net.minecraft.block.CraftingTableBlock
import net.minecraft.block.DaylightDetectorBlock
import net.minecraft.block.DecoratedPotBlock
import net.minecraft.block.DispenserBlock
import net.minecraft.block.DoorBlock
import net.minecraft.block.DragonEggBlock
import net.minecraft.block.EnchantingTableBlock
import net.minecraft.block.EnderChestBlock
import net.minecraft.block.FenceBlock
import net.minecraft.block.FenceGateBlock
import net.minecraft.block.FletchingTableBlock
import net.minecraft.block.FlowerPotBlock
import net.minecraft.block.GrindstoneBlock
import net.minecraft.block.HopperBlock
import net.minecraft.block.JigsawBlock
import net.minecraft.block.JukeboxBlock
import net.minecraft.block.LecternBlock
import net.minecraft.block.LeverBlock
import net.minecraft.block.LightBlock
import net.minecraft.block.LoomBlock
import net.minecraft.block.NoteBlock
import net.minecraft.block.PistonExtensionBlock
import net.minecraft.block.PumpkinBlock
import net.minecraft.block.RedstoneOreBlock
import net.minecraft.block.RedstoneWireBlock
import net.minecraft.block.RepeaterBlock
import net.minecraft.block.RespawnAnchorBlock
import net.minecraft.block.ShulkerBoxBlock
import net.minecraft.block.SmithingTableBlock
import net.minecraft.block.StonecutterBlock
import net.minecraft.block.StructureBlock
import net.minecraft.block.SweetBerryBushBlock
import net.minecraft.block.TntBlock
import net.minecraft.block.TrapdoorBlock
import net.minecraft.entity.player.PlayerEntity
import net.minecraft.fluid.FluidState
import net.minecraft.fluid.Fluids
import net.minecraft.item.ItemStack
import net.minecraft.state.property.Property
import net.minecraft.util.math.*
import net.minecraft.world.BlockView

object BlockUtils {

    val signs = setOf(
        Blocks.OAK_SIGN,
        Blocks.BIRCH_SIGN,
        Blocks.ACACIA_SIGN,
        Blocks.CHERRY_SIGN,
        Blocks.JUNGLE_SIGN,
        Blocks.DARK_OAK_SIGN,
        Blocks.MANGROVE_SIGN,
        Blocks.BAMBOO_SIGN,
        Blocks.CRIMSON_SIGN,
        Blocks.WARPED_SIGN,
        Blocks.SPRUCE_SIGN
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
        Blocks.CRIMSON_WALL_SIGN,
        Blocks.WARPED_WALL_SIGN,
        Blocks.SPRUCE_WALL_SIGN
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
        Blocks.CRIMSON_HANGING_SIGN,
        Blocks.WARPED_HANGING_SIGN,
        Blocks.SPRUCE_HANGING_SIGN
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
        Blocks.CRIMSON_WALL_HANGING_SIGN,
        Blocks.WARPED_WALL_HANGING_SIGN,
        Blocks.SPRUCE_WALL_HANGING_SIGN
    )

    val allSigns = signs + wallSigns + hangingSigns + hangingWallSigns

    val interactionBlocks = setOf(
        AbstractCauldronBlock::class,
        AbstractFurnaceBlock::class,
        AbstractSignBlock::class,
        AnvilBlock::class,
        BarrelBlock::class,
        BeaconBlock::class,
        BedBlock::class,
        BeehiveBlock::class,
        BellBlock::class,
        BrewingStandBlock::class,
        ButtonBlock::class,
        CakeBlock::class,
        CampfireBlock::class,
        CandleBlock::class,
        CandleCakeBlock::class,
        CartographyTableBlock::class,
        CaveVinesBodyBlock::class,
        CaveVinesHeadBlock::class,
        ChestBlock::class,
        ChiseledBookshelfBlock::class,
        CommandBlock::class,
        ComparatorBlock::class,
        ComposterBlock::class,
        CrafterBlock::class,
        CraftingTableBlock::class,
        DaylightDetectorBlock::class,
        DecoratedPotBlock::class,
        DispenserBlock::class,
        DoorBlock::class,
        DragonEggBlock::class,
        EnchantingTableBlock::class,
        EnderChestBlock::class,
        FenceBlock::class,
        FenceGateBlock::class,
        FletchingTableBlock::class,
        FlowerPotBlock::class,
        GrindstoneBlock::class,
        HopperBlock::class,
        JigsawBlock::class,
        JukeboxBlock::class,
        LecternBlock::class,
        LeverBlock::class,
        LightBlock::class,
        LoomBlock::class,
        NoteBlock::class,
        PistonExtensionBlock::class,
        PumpkinBlock::class,
        RedstoneOreBlock::class,
        RedstoneWireBlock::class,
        RepeaterBlock::class,
        RespawnAnchorBlock::class,
        ShulkerBoxBlock::class,
        SmithingTableBlock::class,
        StonecutterBlock::class,
        StructureBlock::class,
        SweetBerryBushBlock::class,
        TntBlock::class,
        TrapdoorBlock::class
    )

    val fluids = listOf(
        Fluids.LAVA,
        Fluids.FLOWING_LAVA,
        Fluids.WATER,
        Fluids.FLOWING_WATER,
        Fluids.EMPTY,
    )

    fun SafeContext.blockState(pos: BlockPos): BlockState = world.getBlockState(pos)
    fun SafeContext.fluidState(pos: BlockPos): FluidState = world.getFluidState(pos)
    fun SafeContext.blockEntity(pos: BlockPos) = world.getBlockEntity(pos)

    fun BlockState.matches(state: BlockState, ignoredProperties: Collection<Property<*>> = emptySet()) =
         this.block == state.block && this.properties.all {
             this[it] == state[it] || it in ignoredProperties
        }

    fun SafeContext.instantBreakable(blockState: BlockState, blockPos: BlockPos, breakThreshold: Float): Boolean {
        val ticksNeeded = 1 / (blockState.calcBlockBreakingDelta(player, world, blockPos) / breakThreshold)
        return (ticksNeeded <= 1 && ticksNeeded != 0f) || gamemode.isCreative
    }

    fun SafeContext.instantBreakable(blockState: BlockState, blockPos: BlockPos, item: ItemStack, breakThreshold: Float): Boolean {
        val ticksNeeded = 1 / (blockState.calcItemBlockBreakingDelta(player, world, blockPos, item) / breakThreshold)
        return (ticksNeeded <= 1 && ticksNeeded != 0f) || gamemode.isCreative
    }

    fun BlockState.calcItemBlockBreakingDelta(
        player: PlayerEntity,
        world: BlockView,
        blockPos: BlockPos,
        item: ItemStack
    ): Float = 0f

    val BlockState.isEmpty get() = matches(emptyState)
    val BlockState.isNotEmpty get() = !isEmpty
    val BlockState.hasFluid get() = !fluidState.isEmpty
    val BlockState.emptyState: BlockState get() = fluidState.blockState
    fun isBroken(oldState: BlockState, newState: BlockState) = oldState.isNotEmpty && oldState.emptyState.matches(newState)
    fun isNotBroken(oldState: BlockState, newState: BlockState) = !isBroken(oldState, newState)
}
