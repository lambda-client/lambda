package com.lambda.client.module.modules.render

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderRadarEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.BaritoneUtils
import com.lambda.client.util.EntityUtils.getInterpolatedPos
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.graphics.RenderUtils2D
import com.lambda.client.util.math.Vec2d
import com.lambda.client.util.math.VectorUtils.distanceTo
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import net.minecraft.client.Minecraft
import net.minecraft.client.renderer.vertex.DefaultVertexFormats
import net.minecraft.network.play.server.SPacketChunkData
import net.minecraft.util.math.ChunkPos
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.apache.commons.lang3.SystemUtils
import org.lwjgl.opengl.GL11.GL_LINE_LOOP
import org.lwjgl.opengl.GL11.glLineWidth
import java.io.*
import java.nio.file.Files
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

object NewChunks : Module(
    name = "NewChunks",
    description = "Highlights newly generated chunks",
    category = Category.RENDER
) {
    private val relative by setting("Relative", false, description = "Renders the chunks at relative Y level to player")
    private val renderMode by setting("Render Mode", RenderMode.BOTH)
    private val chunkGridColor by setting("Grid Color", ColorHolder(255, 0, 0, 100), true, { renderMode != RenderMode.WORLD }, description = "Might cause performance issues on high zoom. Disable @ Client>HudEditor>Radar>Chunk Lines")
    private val oldChunkColor by setting("Old Chunk Color", ColorHolder(100, 100, 100, 100), true, { renderMode != RenderMode.WORLD }, description = "Old chunks. (Baritone cache + Fullchunk() = True). Some distant Chunks might be false positives.")
    private val newChunkColor by setting("New Chunk Color", ColorHolder(255, 0, 0, 100), true, { renderMode != RenderMode.WORLD })
    private val saveChunks by setting("Save Chunks", false)
    private val saveOption by setting("Save Option", SaveOption.EXTRA_FOLDER, { saveChunks })
    private val alsoSaveNormalCoords by setting("Save Normal Coords", false, { saveChunks })
    private val saveMode by setting("Save Mode", SaveMode.OLD, { saveChunks }, description = "Save old / new chunks")

    private val closeFile by setting("Close file", false, { saveChunks }, consumer = { _, _ ->
        logWriterClose()
        MessageSendHelper.sendChatMessage("$chatName Saved file to $path!")
        false
    })
    private val openNewChunksFolder by setting("Open NewChunks Folder...", false, { saveChunks }, consumer = { _, _ ->
        FolderUtils.openFolder(FolderUtils.newChunksFolder)
        false
    })
    private val yOffset by setting("Y Offset", 0, -256..256, 4, fineStep = 1, description = "Render offset in Y axis")
    private val color by setting("Color", ColorHolder(255, 64, 64, 200), description = "World highlighting color.")
    private val thickness by setting("Thickness", 1.5f, 0.1f..4.0f, 0.1f, description = "Thickness of the highlighting square")
    private val range by setting("Render Range", 512, 64..2048, 32,{ renderMode != RenderMode.RADAR }, description = "Maximum range for chunks to be highlighted in the world.")
    private val removeMode by setting("Remove Mode", RemoveMode.AGE, description = "Mode to use for removing chunks. Unload might generate bad data on saving feature and never will cause performance issues eventually.")
    private val maxAge by setting("Max age", 10, 1..600, 1, { removeMode == RemoveMode.AGE }, description = "Maximum age of chunks since recording", unit = "m")
    private val maxDist by setting("Max Distance", 10000, 1000..100000, 1000, { removeMode == RemoveMode.DIST }, description = "Maximum distance to chunks until unload.", unit = "Meter / Blocks")

    private var lastSetting = LastSetting()
    private var logWriter: PrintWriter? = null
    private val newChunks = ConcurrentHashMap<ChunkPos, Long>()
    private val oldChunks = ConcurrentHashMap<ChunkPos, Long>()
    private val savedChunks = ConcurrentHashMap<ChunkPos, Long>()
    private val timer = TickTimer(TimeUnit.SECONDS)

    init {
        onDisable {
            logWriterClose()
            oldChunks.clear()
            newChunks.clear()
            savedChunks.clear()
            MessageSendHelper.sendChatMessage("$chatName Saved and cleared chunks!")
        }

        onEnable {
            timer.reset()
        }
    }

    init {
        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.END
                && removeMode == RemoveMode.AGE
                && timer.tick(5)
            ) {
                val currentTime = System.currentTimeMillis()
                newChunks.values.removeIf { chunkAge -> currentTime - chunkAge > maxAge * 60 * 1000 }
                oldChunks.values.removeIf { chunkAge -> currentTime - chunkAge > maxAge * 60 * 1000 }
                savedChunks.values.removeIf { chunkAge -> currentTime - chunkAge > maxAge * 60 * 1000 }
            }
            else if (it.phase == TickEvent.Phase.END
                    && removeMode == RemoveMode.DIST
                    && timer.tick(5)
                    ){
                newChunks.keys.removeIf { chunkKey -> player.distanceTo(chunkKey) > maxDist }
                oldChunks.keys.removeIf { chunkKey -> player.distanceTo(chunkKey) > maxDist }
                savedChunks.keys.removeIf { chunkKey -> player.distanceTo(chunkKey) > maxDist }
            }
        }

        safeListener<RenderWorldEvent> {
            if (renderMode == RenderMode.RADAR) return@safeListener
            val y = yOffset.toDouble() + if (relative) getInterpolatedPos(player, LambdaTessellator.pTicks()).y else 0.0

            glLineWidth(thickness)
            GlStateUtils.depth(false)

            val buffer = LambdaTessellator.buffer

            newChunks.filter { player.distanceTo(it.key) < range }.keys.forEach { chunkPos ->
                buffer.begin(GL_LINE_LOOP, DefaultVertexFormats.POSITION_COLOR)
                buffer.pos(chunkPos.xStart.toDouble(), y, chunkPos.zStart.toDouble()).color(color.r, color.g, color.b, color.a).endVertex()
                buffer.pos(chunkPos.xEnd + 1.toDouble(), y, chunkPos.zStart.toDouble()).color(color.r, color.g, color.b, color.a).endVertex()
                buffer.pos(chunkPos.xEnd + 1.toDouble(), y, chunkPos.zEnd + 1.toDouble()).color(color.r, color.g, color.b, color.a).endVertex()
                buffer.pos(chunkPos.xStart.toDouble(), y, chunkPos.zEnd + 1.toDouble()).color(color.r, color.g, color.b, color.a).endVertex()
                LambdaTessellator.render()
            }

            glLineWidth(1.0f)
            GlStateUtils.depth(true)
        }

        safeListener<RenderRadarEvent> {
            if (renderMode == RenderMode.WORLD) return@safeListener
            val playerOffset = Vec2d((player.posX - (player.chunkCoordX shl 4)), (player.posZ - (player.chunkCoordZ shl 4)))
            val chunkDist = (it.radius * it.scale).toInt() shr 4
            val newChunkRects: MutableList<Pair<Vec2d, Vec2d>> = mutableListOf()
            // at high zooms (further zoomed out) there will be thousands of rects being rendered
            // buffering rects here to reduce GL calls and improve FPS
            val oldChunkRects: MutableList<Pair<Vec2d, Vec2d>> = mutableListOf()
            val chunkGridRects: MutableList<Pair<Vec2d, Vec2d>> = mutableListOf()
            for (chunkX in -chunkDist..chunkDist) {
                for (chunkZ in -chunkDist..chunkDist) {
                    val pos0 = getChunkPos(chunkX, chunkZ, playerOffset, it.scale)
                    val pos1 = getChunkPos(chunkX + 1, chunkZ + 1, playerOffset, it.scale)
                    if (isSquareInRadius(pos0, pos1, it.radius)) {
                        val chunk = world.getChunk(player.chunkCoordX + chunkX, player.chunkCoordZ + chunkZ)
                        val chunkPos = ChunkPos((player.chunkCoordX + chunkX), (player.chunkCoordZ + chunkZ))
                        val isCachedChunk =
                            BaritoneUtils.primary?.worldProvider?.currentWorld?.cachedWorld?.isCached(
                                (player.chunkCoordX + chunkX) shl 4, (player.chunkCoordZ + chunkZ) shl 4
                            ) ?: false

                        if (newChunks.containsKey(chunkPos)){
                            newChunkRects.add(Pair(pos0, pos1))
                        }
                        else if (!chunk.isLoaded && isCachedChunk){
                            oldChunkRects.add(Pair(pos0, pos1))
                        }
                        else if (chunk.isLoaded) {
                            oldChunkRects.add(Pair(pos0, pos1))
                        }
                        if (it.chunkLines) chunkGridRects.add(Pair(pos0, pos1))
                    }
                }
            }
            if (oldChunkRects.isNotEmpty()) RenderUtils2D.drawRectFilledList(it.vertexHelper, oldChunkRects, oldChunkColor)
            if (it.chunkLines && chunkGridRects.isNotEmpty()) RenderUtils2D.drawRectOutlineList(it.vertexHelper, chunkGridRects, 0.3f, chunkGridColor)
            if (newChunkRects.isNotEmpty()) RenderUtils2D.drawRectFilledList(it.vertexHelper, newChunkRects, newChunkColor)
        }

        safeListener<PacketEvent.PostReceive> { event ->
            if (event.packet is SPacketChunkData
            ) {
                val chunkPos = ChunkPos(event.packet.chunkX, event.packet.chunkZ)
                if (!event.packet.isFullChunk) {
                    oldChunks.remove(chunkPos)
                    newChunks.putIfAbsent(chunkPos, System.currentTimeMillis())
                }
                else if (!newChunks.containsKey(chunkPos)){
                    oldChunks.putIfAbsent(chunkPos, System.currentTimeMillis())
                }
            }
        }

        safeListener<ChunkEvent.Unload> {
            if (removeMode == RemoveMode.UNLOAD) {
                newChunks.remove(it.chunk.pos)
                oldChunks.remove(it.chunk.pos)
                savedChunks.remove(it.chunk.pos)
            }
            if (saveChunks && !savedChunks.containsKey(it.chunk.pos)) {
                if (saveMode == SaveMode.OLD && oldChunks.containsKey(it.chunk.pos)) {
                    saveChunk(it.chunk.pos)
                    savedChunks[it.chunk.pos] = System.currentTimeMillis()
                }
                else if (saveMode == SaveMode.NEW && newChunks.containsKey(it.chunk.pos)) {
                    saveChunk(it.chunk.pos)
                    savedChunks[it.chunk.pos] = System.currentTimeMillis()
                }
            }
        }
    }

    // needs to be synchronized so no data gets lost
    private fun SafeClientEvent.saveChunk(chunk: ChunkPos) {
        saveChunk(testAndGetLogWriter(), getNewChunkInfo(chunk))
    }

    private fun getNewChunkInfo(chunk: ChunkPos): String {
        var chunkInfo = String.format("%d,%d,%d", System.currentTimeMillis(), chunk.x, chunk.z)
        if (alsoSaveNormalCoords) {
            chunkInfo += String.format(",%d,%d", chunk.x * 16 + 8, chunk.z * 16 + 8)
        }
        return chunkInfo
    }

    private fun SafeClientEvent.testAndGetLogWriter(): PrintWriter? {
        if (lastSetting.testChangeAndUpdate(this)) {
            logWriterClose()
            logWriterOpen()
        }
        return logWriter
    }

    private fun logWriterClose() {
        if (logWriter != null) {
            logWriter!!.close()
            logWriter = null
            lastSetting = LastSetting() // what if the settings stay the same?
        }
    }

    private fun logWriterOpen() {
        val fileWriter = try {
            FileWriter(path.toString(), true)
        } catch (e: IOException) {
            e.printStackTrace()
            LambdaMod.LOG.error(chatName + " some exception happened when trying to start the logging -> " + e.message)
            MessageSendHelper.sendErrorMessage("$chatName Can't access $path")
            disable()
            return
        }

        PrintWriter(BufferedWriter(fileWriter), true).let {
            logWriter = it

            var head = "timestamp,ChunkX,ChunkZ"
            if (alsoSaveNormalCoords) {
                head += ",x,z"
            }

            it.println(head)
        }
    }

    private val path: Path
        get() {
            // code from baritone (https://github.com/cabaletta/baritone/blob/master/src/main/java/baritone/cache/WorldProvider.java)

            var file: File? = null
            val dimension = mc.player.dimension

            // If there is an integrated server running (Aka Singleplayer) then do magic to find the world save file
            if (mc.isSingleplayer) {
                try {
                    file = mc.integratedServer?.getWorld(dimension)?.chunkSaveLocation
                } catch (e: Exception) {
                    e.printStackTrace()
                    LambdaMod.LOG.error("some exception happened when getting canonicalFile -> " + e.message)
                    MessageSendHelper.sendErrorMessage(chatName + " onGetPath: " + e.message)
                    disable()
                }

                // Gets the "depth" of this directory relative to the game's run directory, 2 is the location of the world
                if (file?.toPath()?.relativize(mc.gameDir.toPath())?.nameCount != 2) {
                    // subdirectory of the main save directory for this world
                    file = file?.parentFile
                }
            } else { // Otherwise, the server must be remote...
                file = makeMultiplayerDirectory().toFile()
            }

            // We will actually store the world data in a subfolder: "DIM<id>"
            if (dimension != 0) { // except if it's the overworld
                file = File(file, "DIM$dimension")
            }

            file = File(file, "logs")
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            file = File(file, mc.session.username + "_" + date + ".csv") // maybe don't safe the name, actually. But I also don't want to make another option...
            val filePath = file.toPath()
            try {
                if (!Files.exists(filePath)) {
                    Files.createDirectories(filePath.parent)
                    Files.createFile(filePath)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                LambdaMod.LOG.error("some exception happened when trying to make the file -> " + e.message)
                MessageSendHelper.sendErrorMessage(chatName + " onCreateFile: " + e.message)
                disable()
            }
            return filePath
        }

    private fun makeMultiplayerDirectory(): Path {
        var rV = Minecraft.getMinecraft().gameDir
        var folderName: String
        when (saveOption) {
            SaveOption.EXTRA_FOLDER -> {
                folderName = mc.currentServerData?.serverName + "-" + mc.currentServerData?.serverIP
                if (SystemUtils.IS_OS_WINDOWS) {
                    folderName = folderName.replace(":", "_")
                }
                rV = File(FolderUtils.newChunksFolder)
                rV = File(rV, folderName)
            }
            SaveOption.LITE_LOADER_WDL -> {
                folderName = mc.currentServerData?.serverName ?: "Offline"
                rV = File(rV, "saves")
                rV = File(rV, folderName)
            }
        }
        return rV.toPath()
    }

    // p2.x > p1.x and p2.y > p1.y is assumed
    private fun isSquareInRadius(p1: Vec2d, p2: Vec2d, radius: Float): Boolean {
        val x = if (p1.x + p2.x > 0) p2.x else p1.x
        val y = if (p1.y + p2.y > 0) p2.y else p1.y
        return Vec2d(x, y).length() < radius
    }

    private fun getChunkPos(x: Int, z: Int, playerOffset: Vec2d, scale: Float): Vec2d {
        return Vec2d((x shl 4).toDouble(), (z shl 4).toDouble()).minus(playerOffset).div(scale.toDouble())
    }

    private fun saveChunk(log: PrintWriter?, data: String) {
        log!!.println(data)
    }

    private enum class SaveOption {
        EXTRA_FOLDER, LITE_LOADER_WDL
    }

    @Suppress("unused")
    private enum class RemoveMode {
        UNLOAD, AGE, DIST, NEVER
    }

    enum class RenderMode {
        WORLD, RADAR, BOTH
    }

    enum class SaveMode {
        NEW, OLD
    }

    private class LastSetting {
        var lastSaveOption: SaveOption? = null
        var lastSaveNormal = false
        var dimension = 0
        var ip: String? = null
        fun testChangeAndUpdate(event: SafeClientEvent): Boolean {
            if (testChange(event)) {
                // so we don't have to do this process again next time
                update(event)
                return true
            }
            return false
        }

        fun testChange(event: SafeClientEvent): Boolean {
            // these somehow include the test whether its null
            return saveOption != lastSaveOption
                || (alsoSaveNormalCoords && alsoSaveNormalCoords != lastSaveNormal)
                || dimension != event.player.dimension
                || mc.currentServerData?.serverIP != ip
        }

        private fun update(event: SafeClientEvent) {
            lastSaveOption = saveOption
            lastSaveNormal = alsoSaveNormalCoords
            dimension = event.player.dimension
            ip = mc.currentServerData?.serverIP
        }
    }
}