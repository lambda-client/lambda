package com.lambda.interaction.construction.verify

import com.lambda.interaction.material.ContainerManager.findDisposable
import com.lambda.module.modules.client.TaskFlow
import com.lambda.util.BlockUtils.blockState
import com.lambda.util.item.ItemUtils.block
import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.item.Items
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.Direction

sealed class TargetState : StateMatcher {
    data object Air : TargetState() {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.isAir
        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            ItemStack.EMPTY
    }

    data object Solid : TargetState() {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.isSolidBlock(world, pos)
        override fun getStack(world: ClientWorld, pos: BlockPos) =
            findDisposable()?.stacks?.firstOrNull {
                it.item.block in TaskFlow.disposables
            } ?: ItemStack(Items.NETHERRACK)
    }

    data class Support(val direction: Direction) : TargetState() {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            pos.offset(direction).blockState(world).isSolidBlock(world, pos.offset(direction))
                    || state.isSolidBlock(world, pos)

        override fun getStack(world: ClientWorld, pos: BlockPos) =
            findDisposable()?.stacks?.firstOrNull {
                it.item.block in TaskFlow.disposables
            } ?: ItemStack(Items.NETHERRACK)
    }

    data class State(val blockState: BlockState) : TargetState() {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state == blockState
        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            blockState.block.getPickStack(world, pos, blockState)
    }

    data class Block(val block: net.minecraft.block.Block) : TargetState() {
        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.block == block
        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            block.getPickStack(world, pos, block.defaultState)
    }

    data class Stack(val itemStack: ItemStack) : TargetState() {
        private val block = itemStack.item.block

        override fun matches(state: BlockState, pos: BlockPos, world: ClientWorld) =
            state.block == block

        override fun getStack(world: ClientWorld, pos: BlockPos): ItemStack =
            itemStack
    }
}