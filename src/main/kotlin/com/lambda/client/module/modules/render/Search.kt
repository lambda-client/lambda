package com.lambda.client.module.modules.render

import com.lambda.client.command.CommandManager
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.ChunkDataEvent
import com.lambda.client.event.events.ConnectionEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.module.modules.client.Hud
import com.lambda.client.setting.settings.impl.collection.CollectionSetting
import com.lambda.client.util.EntityUtils
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.ESPRenderer
import com.lambda.client.util.graphics.GeometryMasks
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.graphics.ShaderHelper
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.text.formatValue
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import com.lambda.client.util.world.isWater
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import net.minecraft.block.BlockEnderChest
import net.minecraft.block.BlockShulkerBox
import net.minecraft.block.BlockStandingSign
import net.minecraft.block.BlockWallSign
import net.minecraft.block.state.BlockStateContainer.StateImplementation
import net.minecraft.block.state.IBlockState
import net.minecraft.entity.EntityList
import net.minecraft.entity.item.EntityItemFrame
import net.minecraft.init.Blocks
import net.minecraft.network.play.server.SPacketBlockChange
import net.minecraft.network.play.server.SPacketMultiBlockChange
import net.minecraft.tileentity.TileEntitySign
import net.minecraft.util.math.BlockPos
import net.minecraft.util.math.ChunkPos
import net.minecraft.util.text.TextComponentString
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import kotlin.collections.set
import kotlin.math.max

object Search : Module(
    name = "Search",
    description = "Highlights blocks in the world",
    category = Category.RENDER
) {
    private val defaultSearchList = linkedSetOf("minecraft:portal", "minecraft:end_portal_frame", "minecraft:bed")

    private val entitySearch by setting("Entity Search", true)
    private val blockSearch by setting("Block Search", true)
    private val illegalBedrock = setting("Illegal Bedrock", false)
    private val illegalNetherWater = setting("Illegal Nether Water", false)
    private val oldSigns = setting("Old Signs", true)
    private val oldSignsColor by setting("Old Signs Color", ColorHolder(220, 0, 0, 110), visibility = { oldSigns.value })
    private val range by setting("Search Range", 512, 0..4096, 8)
    private val yRangeBottom by setting("Top Y", 256, 0..256, 1)
    private val yRangeTop by setting("Bottom Y", 0, 0..256, 1)
    private val maximumBlocks by setting("Maximum Blocks", 256, 1..4096, 128, visibility = { blockSearch })
    private val maximumEntities by setting("Maximum Entities", 256, 1..4096, 128, visibility = { entitySearch })
    private val filled by setting("Filled", true)
    private val outline by setting("Outline", true)
    private val tracer by setting("Tracer", true)
    private val entitySearchColor by setting("Entity Search Color", Hud.secondaryColor, visibility = { entitySearch })
    private val autoBlockColor by setting("Block Search Auto Color", true)
    private val customBlockColor by setting("Block Search Custom Color", Hud.secondaryColor, visibility = { !autoBlockColor })
    private val aFilled by setting("Filled Alpha", 31, 0..255, 1, { filled })
    private val aOutline by setting("Outline Alpha", 127, 0..255, 1, { outline })
    private val aTracer by setting("Tracer Alpha", 200, 0..255, 1, { tracer })
    private val thickness by setting("Line Thickness", 2.0f, 0.25f..5.0f, 0.25f)
    private val hideF1 by setting("Hide on F1", true)

    var overrideWarning by setting("Override Warning", false, { false })
    val blockSearchList = setting(CollectionSetting("Search List", defaultSearchList, { false }))
    val entitySearchList = setting(CollectionSetting("Entity Search List", linkedSetOf(EntityList.getKey((EntityItemFrame::class.java))!!.path), { false }))
    val blockSearchDimensionFilter = setting(CollectionSetting("Block Dimension Filter", linkedSetOf(), entryType = DimensionFilter::class.java, visibility = { false }))
    val entitySearchDimensionFilter = setting(CollectionSetting("Entity Dimension Filter", linkedSetOf(), entryType = DimensionFilter::class.java, visibility = { false }))

    private val blockRenderer = ESPRenderer()
    private val entityRenderer = ESPRenderer()
    private val foundBlockMap: ConcurrentMap<BlockPos, IBlockState> = ConcurrentHashMap()
    private var blockRenderUpdateJob: Job? = null
    private var entityRenderUpdateJob: Job? = null
    private var blockSearchJob: Job? = null
    private var prevDimension = -2

    override fun getHudInfo(): String {
        return (blockRenderer.size + entityRenderer.size).toString()
    }

    init {
        blockSearchList.editListeners.add { blockSearchListUpdateListener(isEnabled) }
        illegalBedrock.listeners.add { blockSearchListUpdateListener(illegalBedrock.value) }
        illegalNetherWater.listeners.add { blockSearchListUpdateListener(illegalNetherWater.value) }
        oldSigns.listeners.add { blockSearchListUpdateListener(oldSigns.value) }

        onEnable {
            if (!overrideWarning && ShaderHelper.isIntegratedGraphics) {
                MessageSendHelper.sendErrorMessage("$chatName Warning: Running Search with an Intel Integrated GPU is not recommended, as it has a &llarge&r impact on performance.")
                MessageSendHelper.sendWarningMessage("$chatName If you're sure you want to try, run the ${formatValue("${CommandManager.prefix}search override")} command")
                disable()
            } else {
                runSafe { searchAllLoadedChunks() }
            }
        }

        onDisable {
            blockRenderUpdateJob?.cancel()
            entityRenderUpdateJob?.cancel()
            blockSearchJob?.cancel()
            blockRenderer.clear()
            entityRenderer.clear()
            foundBlockMap.clear()
        }

        safeListener<RenderWorldEvent> {
            if (player.dimension != prevDimension) {
                prevDimension = player.dimension
                foundBlockMap.clear()
            }
            if (blockSearch) {
                if (!(hideF1 && mc.gameSettings.hideGUI)) {
                    blockRenderer.render(false)
                }
            }
            if (entitySearch) {
                if (!(hideF1 && mc.gameSettings.hideGUI)) {
                    entityRenderer.render(false)
                }
            }
        }

        safeListener<TickEvent.ClientTickEvent> {
            if (blockRenderUpdateJob == null || blockRenderUpdateJob?.isCompleted == true) {
                blockRenderUpdateJob = defaultScope.launch {
                    blockRenderUpdate()
                }
            }
            if (entityRenderUpdateJob == null || entityRenderUpdateJob?.isCompleted == true) {
                entityRenderUpdateJob = defaultScope.launch {
                    searchLoadedEntities()
                }
            }
        }

        safeListener<ChunkDataEvent> {
            // We avoid listening to SPacketChunkData directly here, as even on PostReceive the chunk is not always
            // fully loaded into the world. Chunk load is handled on a separate thread in mc code.
            // i.e. world.getChunk(x, z) can and will return an empty chunk in the packet event
            defaultScope.launch {
                findBlocksInChunk(it.chunk)
                    .forEach { block -> foundBlockMap[block.first] = block.second }
            }
        }

        safeListener<PacketEvent.Receive> {
            when (it.packet) {
                is SPacketMultiBlockChange -> {
                    it.packet.changedBlocks
                        .forEach { changedBlock -> handleBlockChange(changedBlock.pos, changedBlock.blockState) }
                }

                is SPacketBlockChange -> {
                    handleBlockChange(it.packet.blockPosition, it.packet.getBlockState())
                }
            }
        }

        safeListener<ConnectionEvent.Disconnect> {
            blockRenderer.clear()
            entityRenderer.clear()
            foundBlockMap.clear()
        }
    }

    private fun blockSearchListUpdateListener(newBool: Boolean) {
        foundBlockMap.entries
            .filterNot { blockSearchList.contains(it.value.block.registryName.toString()) }
            .forEach { foundBlockMap.remove(it.key) }
        if (newBool) runSafe { searchAllLoadedChunks() }
    }

    private fun SafeClientEvent.searchLoadedEntities() {
        val renderList = world.loadedEntityList
            .asSequence()
            .filter {
                EntityList.getKey(it)?.path?.let { entityName ->
                    entitySearchList.contains(entityName)
                } ?: false
            }
            .filter {
                EntityList.getKey(it)?.path?.let { entityName ->
                    entitySearchDimensionFilter.value.find { dimFilter -> dimFilter.searchKey == entityName }?.dim
                }?.contains(player.dimension) ?: true
            }
            .sortedBy { it.distanceTo(player.getPositionEyes(1f)) }
            .take(maximumEntities)
            .filter { it.distanceTo(player.getPositionEyes(1f)) < range }
            .map {
                Triple(
                    it.renderBoundingBox.offset(EntityUtils.getInterpolatedAmount(it, LambdaTessellator.pTicks())),
                    entitySearchColor,
                    GeometryMasks.Quad.ALL
                )
            }
            .toMutableList()
        entityRenderer.replaceAll(renderList)
    }

    private fun SafeClientEvent.searchAllLoadedChunks() {
        val renderDist = mc.gameSettings.renderDistanceChunks
        val playerChunkPos = ChunkPos(player.position)
        val chunkPos1 = ChunkPos(playerChunkPos.x - renderDist, playerChunkPos.z - renderDist)
        val chunkPos2 = ChunkPos(playerChunkPos.x + renderDist, playerChunkPos.z + renderDist)

        if (blockSearchJob?.isActive != true) {
            blockSearchJob = defaultScope.launch {
                for (x in chunkPos1.x..chunkPos2.x) for (z in chunkPos1.z..chunkPos2.z) {
                    if (!isActive) return@launch
                    runSafe {
                        val chunk = world.getChunk(x, z)
                        if (!chunk.isLoaded) return@runSafe

                        findBlocksInChunk(chunk).forEach { pair ->
                            foundBlockMap[pair.first] = pair.second
                        }
                    }
                }
            }
        }
    }

    private fun SafeClientEvent.handleBlockChange(pos: BlockPos, state: IBlockState) {
        if (searchQuery(state, pos)) {
            foundBlockMap[pos] = state
        } else {
            foundBlockMap.remove(pos)
        }
    }

    private fun SafeClientEvent.blockRenderUpdate() {
        updateAlpha()
        val playerPos = player.position
        // unload rendering on block pos > range
        foundBlockMap
            .filter {
                playerPos.distanceTo(it.key) > max(mc.gameSettings.renderDistanceChunks * 16, range)
            }
            .map { it.key }
            .forEach { foundBlockMap.remove(it) }

        val renderList = foundBlockMap
            .filter {
                blockSearchDimensionFilter.value
                    .find {
                        dimFilter -> dimFilter.searchKey == it.value.block.registryName.toString()
                    }?.dim?.contains(player.dimension) ?: true
            }
            .map {
                player.getPositionEyes(1f).distanceTo(it.key) to it.key
            }
            .filter { it.first < range }
            .take(maximumBlocks)
            .flatMap { pair ->
                foundBlockMap[pair.second]?.let { bb ->
                    return@flatMap listOf(
                        Triple(bb.getSelectedBoundingBox(world, pair.second),
                            getBlockColor(pair.second, bb),
                            GeometryMasks.Quad.ALL
                        )
                    )
                } ?: run {
                    return@flatMap emptyList()
                }
            }
            .toMutableList()
        blockRenderer.replaceAll(renderList)
    }

    private fun updateAlpha() {
        blockRenderer.aFilled = if (filled) aFilled else 0
        blockRenderer.aOutline = if (outline) aOutline else 0
        blockRenderer.aTracer = if (tracer) aTracer else 0
        blockRenderer.thickness = thickness
        entityRenderer.aFilled = if (filled) aFilled else 0
        entityRenderer.aOutline = if (outline) aOutline else 0
        entityRenderer.aTracer = if (tracer) aTracer else 0
        entityRenderer.thickness = thickness
    }

    private fun SafeClientEvent.findBlocksInChunk(chunk: Chunk): ArrayList<Pair<BlockPos, IBlockState>> {
        val yRange = yRangeTop..yRangeBottom
        val xRange = (chunk.x shl 4)..(chunk.x shl 4) + 15
        val zRange = (chunk.z shl 4)..(chunk.z shl 4) + 15

        val blocks: ArrayList<Pair<BlockPos, IBlockState>> = ArrayList()
        for (y in yRange) for (x in xRange) for (z in zRange) {
            val pos = BlockPos(x, y, z)
            val blockState = chunk.getBlockState(pos)
            if (isOldSign(blockState, pos)) {
                val signState = if (blockState.block == Blocks.STANDING_SIGN) {
                    OldStandingSign(blockState)
                } else {
                    OldWallSign(blockState)
                }
                blocks.add(pos to signState)
                continue // skip searching for regular sign at this pos
            }
            if (searchQuery(blockState, pos)) blocks.add(pos to blockState)
        }
        return blocks
    }

    private fun SafeClientEvent.searchQuery(state: IBlockState, pos: BlockPos): Boolean {
        val block = state.block
        if (block == Blocks.AIR) return false
        return (blockSearchList.contains(block.registryName.toString())
            && blockSearchDimensionFilter.value.find { dimFilter ->
            dimFilter.searchKey == block.registryName.toString()
        }?.dim?.contains(player.dimension) ?: true)
            || isIllegalBedrock(state, pos)
            || isIllegalWater(state)
    }

    private fun SafeClientEvent.isIllegalBedrock(state: IBlockState, pos: BlockPos): Boolean {
        if (!illegalBedrock.value) return false
        if (state.block != Blocks.BEDROCK) return false
        return when (player.dimension) {
            0 -> pos.y >= 5
            -1 -> pos.y in 5..122
            else -> false
        }
    }

    private fun SafeClientEvent.isIllegalWater(state: IBlockState): Boolean {
        if (!illegalNetherWater.value) return false
        return player.dimension == -1 && state.isWater
    }

    private fun SafeClientEvent.isOldSign(state: IBlockState, pos: BlockPos): Boolean {
        if (!oldSigns.value) return false
        return (state.block == Blocks.STANDING_SIGN || state.block == Blocks.WALL_SIGN) && isOldSignText(pos)
    }

    private fun SafeClientEvent.isOldSignText(pos: BlockPos): Boolean {
        // Explanation: Old signs on 2b2t (pre-2015 <1.9 ?) have older style NBT text tags.
        // We can tell them apart by checking if there are siblings in the tag.
        // Old signs won't have siblings.
        val signTextComponents = listOf(world.getTileEntity(pos))
            .filterIsInstance<TileEntitySign>()
            .flatMap { it.signText.toList() }
            .filterIsInstance<TextComponentString>()
            .toList()
        return signTextComponents.isNotEmpty()
            && signTextComponents.all { it.siblings.size == 0 }
            && !signTextComponents.all { it.text.isEmpty() }
    }

    private fun SafeClientEvent.getBlockColor(pos: BlockPos, blockState: IBlockState): ColorHolder {
        val block = blockState.block
        return if (autoBlockColor) {
            when (block) {
                Blocks.PORTAL -> {
                    ColorHolder(82, 49, 153)
                }
                is BlockShulkerBox -> {
                    val colorInt = block.color.colorValue
                    ColorHolder((colorInt shr 16), (colorInt shr 8 and 255), (colorInt and 255))
                }
                is BlockEnderChest -> {
                    ColorHolder(64, 49, 114)
                }
                is BlockOldStandingSign -> {
                    oldSignsColor
                }
                is BlockOldWallSign -> {
                    oldSignsColor
                }
                else -> {
                    val colorInt = blockState.getMapColor(world, pos).colorValue
                    ColorHolder((colorInt shr 16), (colorInt shr 8 and 255), (colorInt and 255))
                }
            }
        } else {
            customBlockColor
        }
    }

    data class DimensionFilter(val searchKey: String, val dim: LinkedHashSet<Int>) {
        override fun toString(): String {
            return "$searchKey -> $dim"
        }
    }

    class OldWallSign(blockStateIn: IBlockState): StateImplementation(BlockOldWallSign, blockStateIn.properties)
    class OldStandingSign(blockStateIn: IBlockState): StateImplementation(BlockOldStandingSign, blockStateIn.properties)
    object BlockOldWallSign: BlockWallSign()
    object BlockOldStandingSign: BlockStandingSign()
}