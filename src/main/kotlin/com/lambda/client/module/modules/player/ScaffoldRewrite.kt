package com.lambda.client.module.modules.player

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.PlayerTravelEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.manager.managers.HotbarManager.serverSideItem
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.flooredPosition
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.*
import net.minecraft.block.BlockBarrier
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.item.ItemBlock
import net.minecraft.util.EnumHand
import net.minecraft.util.SoundCategory
import net.minecraft.util.math.BlockPos

object ScaffoldRewrite : Module(
    name = "ScaffoldRewrite",
    description = "Places blocks under you",
    category = Category.PLAYER,
    modulePriority = 500
) {
    private val page by setting("Page", Page.SETTINGS)

    private val filled by setting("Filled", true, { page == Page.RENDER }, description = "Renders surfaces")
    private val outline by setting("Outline", true, { page == Page.RENDER }, description = "Renders outline")
    private val alphaFilled by setting("Alpha Filled", 26, 0..255, 1, { filled && page == Page.RENDER }, description = "Alpha for surfaces")
    private val alphaOutline by setting("Alpha Outline", 26, 0..255, 1, { outline && page == Page.RENDER }, description = "Alpha for outline")
    private val thickness by setting("Outline Thickness", 2f, .25f..4f, .25f, { outline && page == Page.RENDER }, description = "Changes thickness of the outline")

    private enum class Page {
        SETTINGS, RENDER
    }

    val renderer by lazy { ESPRenderer() }

//    var currentStructure: Set<BlockPos> = emptySet()
    var baseBlock: BlockPos = BlockPos.ORIGIN

    init {
        safeListener<PlayerTravelEvent> {
            val basePos = player.flooredPosition.down()

            if (world.isPlaceable(basePos)) {
//                LambdaMod.LOG.error(player.position.down())
                val placeSequence = getNeighbourSequence(basePos, 1, visibleSideCheck = true)

                if (placeSequence.isNotEmpty()) {
                    placeSequence.firstOrNull()?.let {
                        world.setBlockState(basePos, Blocks.BARRIER.defaultState)

                        connection.sendPacket(it.toPlacePacket(EnumHand.MAIN_HAND))
                        player.swingArm(EnumHand.MAIN_HAND)

                        val itemStack = player.serverSideItem
                        val block = (itemStack.item as? ItemBlock?)?.block ?: return@safeListener
                        val metaData = itemStack.metadata
                        val blockState = block.getStateForPlacement(world, it.pos, it.side, it.hitVecOffset.x.toFloat(), it.hitVecOffset.y.toFloat(), it.hitVecOffset.z.toFloat(), metaData, player, EnumHand.MAIN_HAND)
                        val soundType = blockState.block.getSoundType(blockState, world, it.pos, player)
                        world.playSound(player, it.pos, soundType.placeSound, SoundCategory.BLOCKS, (soundType.getVolume() + 1.0f) / 2.0f, soundType.getPitch() * 0.8f)
                    }
                }

//                if (baseBlock != basePos) {
//
//                }
                baseBlock = basePos
//                world.setBlockState(basePos, Blocks.BARRIER.defaultState)
            }
        }

        safeListener<RenderWorldEvent> {
            renderer.aFilled = if (filled) alphaFilled else 0
            renderer.aOutline = if (outline) alphaOutline else 0
            renderer.thickness = thickness

            renderer.add(baseBlock, ColorHolder(0, 255, 0))

            renderer.render(clear = true)
        }
    }

}