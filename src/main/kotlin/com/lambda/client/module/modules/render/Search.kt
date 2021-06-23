package com.lambda.client.module.modules.render

import com.lambda.client.command.CommandManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.TickTimer
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.ShaderHelper
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.formatValue
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import net.minecraft.block.state.IBlockState
import net.minecraft.init.Blocks
import net.minecraft.util.math.AxisAlignedBB
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.math.Vec3d
import net.minecraft.world.chunk.Chunk
import java.util.*
import kotlin.collections.set

object Search : Module(
    name = "Search",
    description = "Highlights blocks in the world",
    category = Category.RENDER
) {
    private val defaultSearchList = linkedSetOf("minecraft:portal", "minecraft:end_portal_frame", "minecraft:bed")

    private val updateDelay by setting("Update Delay", 1000, 500..3000, 50)
    private val range by setting("Search Range", 128, 0..256, 8)
    private val yRangeBottom by setting("Top Y", 256, 0..256, 1)
    private val yRangeTop by setting("Bottom Y", 0, 0..256, 1)
    private val maximumBlocks by setting("Maximum Blocks", 256, 16..4096, 128)
    private val filled by setting("Filled", true)
    private val outline by setting("Outline", true)
    private val tracer by setting("Tracer", true)
    private val customColors by setting("Custom Colors", false)
    private val customColor by setting("Custom Color", ColorHolder(155, 144, 255), visibility = { customColors })
    private val aFilled by setting("Filled Alpha", 31, 0..255, 1, { filled })
    private val aOutline by setting("Outline Alpha", 127, 0..255, 1, { outline })
    private val aTracer by setting("Tracer Alpha", 200, 0..255, 1, { tracer })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f)

    var overrideWarning by setting("Override Warning", false, { false })
    val searchList = setting(CollectionSetting("Search List", defaultSearchList, { false }))

    private val renderer = ESPRenderer()
    private val updateTimer = TickTimer()

    override fun getHudInfo(): String {
        return renderer.size.toString()
    }

    init {
        onEnable {
            if (!overrideWarning && ShaderHelper.isIntegratedGraphics) {
                MessageSendHelper.sendErrorMessage("$chatName Warning: Running Search with an Intel Integrated GPU is not recommended, as it has a &llarge&r impact on performance.")
                MessageSendHelper.sendWarningMessage("$chatName If you're sure you want to try, run the ${formatValue("${CommandManager.prefix}search override")} command")
                disable()
                return@onEnable
            }
        }

        safeListener<RenderWorldEvent> {
            renderer.render(false)

            if (updateTimer.tick(updateDelay.toLong())) {
                updateRenderer()
            }
        }
    }

    private fun SafeClientEvent.updateRenderer() {
        defaultScope.launch {
            val posMap = TreeMap<Double, Pair<BlockPos, IBlockState>>()

            coroutineScope {
                launch {
                    updateAlpha()
                }
                launch {
                    val eyePos = player.getPositionEyes(1f)
                    getBlockPosList(eyePos, posMap)
                }
            }

            val renderList = ArrayList<Triple<AxisAlignedBB, ColorHolder, Int>>()
            val sides = GeometryMasks.Quad.ALL

            for ((index, pair) in posMap.values.withIndex()) {
                if (index >= maximumBlocks) break
                val bb = pair.second.getSelectedBoundingBox(world, pair.first)
                val color = getBlockColor(pair.first, pair.second)

                renderList.add(Triple(bb, color, sides))
            }

            renderer.replaceAll(renderList)
        }
    }

    private fun updateAlpha() {
        renderer.aFilled = if (filled) aFilled else 0
        renderer.aOutline = if (outline) aOutline else 0
        renderer.aTracer = if (tracer) aTracer else 0
        renderer.thickness = thickness
    }

    private suspend fun SafeClientEvent.getBlockPosList(
        eyePos: Vec3d,
        map: MutableMap<Double, Pair<BlockPos, IBlockState>>
    ) {
        val renderDist = mc.gameSettings.renderDistanceChunks
        val playerChunkPos = ChunkPos(player.position)
        val chunkPos1 = ChunkPos(playerChunkPos.x - renderDist, playerChunkPos.z - renderDist)
        val chunkPos2 = ChunkPos(playerChunkPos.x + renderDist, playerChunkPos.z + renderDist)

        coroutineScope {
            for (x in chunkPos1.x..chunkPos2.x) for (z in chunkPos1.z..chunkPos2.z) {
                val chunk = world.getChunk(x, z)
                if (!chunk.isLoaded) continue
                if (player.distanceTo(chunk.pos) > range + 16) continue

                launch {
                    findBlocksInChunk(chunk, eyePos, map)
                }
                delay(1L)
            }
        }
    }

    private fun findBlocksInChunk(chunk: Chunk, eyePos: Vec3d, map: MutableMap<Double, Pair<BlockPos, IBlockState>>) {
        val yRange = yRangeTop..yRangeBottom
        val xRange = (chunk.x shl 4)..(chunk.x shl 4) + 15
        val zRange = (chunk.z shl 4)..(chunk.z shl 4) + 15

        for (y in yRange) for (x in xRange) for (z in zRange) {
            val pos = BlockPos(x, y, z)
            val blockState = chunk.getBlockState(pos)
            val block = blockState.block

            if (block == Blocks.AIR) continue
            if (!searchList.contains(block.registryName.toString())) continue

            val dist = eyePos.distanceTo(pos)
            if (dist > range) continue

            synchronized(map) {
                map[dist] = (pos to blockState)
            }
        }
    }

    private fun SafeClientEvent.getBlockColor(pos: BlockPos, blockState: IBlockState): ColorHolder {
        val block = blockState.block

        return if (!customColors) {
            if (block == Blocks.PORTAL) {
                ColorHolder(82, 49, 153)
            } else {
                val colorInt = blockState.getMapColor(world, pos).colorValue
                ColorHolder((colorInt shr 16), (colorInt shr 8 and 255), (colorInt and 255))
            }
        } else {
            customColor
        }
    }

}