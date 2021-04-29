package com.lambda.client.module.modules.render

import com.lambda.client.module.Category
import com.lambda.client.module.Module
import net.minecraft.block.Block
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.item.Item
import net.minecraft.item.ItemStack
import net.minecraft.util.EnumParticleTypes
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.BlockPos.MutableBlockPos
import net.minecraft.world.GameType
import net.minecraft.world.World
import java.util.Random

internal object BarrierVision : Module(
    name = "BarrierVision",
    description = "Highlight invisible blocks",
    category = Category.MISC
) {
    fun doVoidFogParticles(posX: Int, posY: Int, posZ: Int) {
        val random = Random()
        val itemstack: ItemStack = mc.player.heldItemMainhand
        val flag = mc.playerController.currentGameType == GameType.CREATIVE && !itemstack.isEmpty && itemstack.item === Item.getItemFromBlock(Blocks.BARRIER as Block)
        val mutableBlockPos = MutableBlockPos()
        for (j in 0..600) {
            showBarrierParticles(posX, posY, posZ, 16, random, flag, mutableBlockPos)
            showBarrierParticles(posX, posY, posZ, 32, random, flag, mutableBlockPos)
        }
    }

    private fun showBarrierParticles(x: Int, y: Int, z: Int, offset: Int, random: Random, holdingBarrier: Boolean, pos: MutableBlockPos) {
        val i: Int = x + mc.world.rand.nextInt(offset) - mc.world.rand.nextInt(offset)
        val j: Int = y + mc.world.rand.nextInt(offset) - mc.world.rand.nextInt(offset)
        val k: Int = z + mc.world.rand.nextInt(offset) - mc.world.rand.nextInt(offset)
        pos.setPos(i, j, k)
        val iblockstate: IBlockState = mc.world.getBlockState(pos as BlockPos)
        iblockstate.block.randomDisplayTick(iblockstate, mc.world as World, pos as BlockPos, random)
        if (!holdingBarrier && iblockstate.block === Blocks.BARRIER) {
            mc.world.spawnParticle(EnumParticleTypes.BARRIER, (i.toFloat() + 0.5f).toDouble(), (j.toFloat() + 0.5f).toDouble(), (k.toFloat() + 0.5f).toDouble(), 0.0, 0.0, 0.0, *IntArray(0))
        }
    }
}
