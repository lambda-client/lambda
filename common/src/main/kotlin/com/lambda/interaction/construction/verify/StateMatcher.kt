package com.lambda.interaction.construction.verify

import net.minecraft.block.BlockState
import net.minecraft.client.world.ClientWorld
import net.minecraft.item.ItemStack
import net.minecraft.util.math.BlockPos

interface StateMatcher {
    fun matches(state: BlockState, pos: BlockPos, world: ClientWorld): Boolean
    fun getStack(world: ClientWorld, pos: BlockPos): ItemStack
}