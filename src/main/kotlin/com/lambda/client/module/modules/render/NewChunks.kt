package com.lambda.client.module.modules.render

import com.lambda.client.LambdaMod
import com.lambda.client.event.events.PacketEvent
import com.lambda.client.event.events.RenderWorldEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.EntityUtils.getInterpolatedPos
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.color.ColorHolder
import com.lambda.client.util.graphics.GlStateUtils
import com.lambda.client.util.graphics.LambdaTessellator
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.safeListener
import com.lambda.client.event.listener.listener
import com.lambda.client.util.Wrapper.world
import com.lambda.client.util.threads.safeAsyncListener
import net.minecraft.client.Minecraft
import net.minecraft.network.play.server.SPacketChunkData
import net.minecraft.world.chunk.Chunk
import net.minecraftforge.event.world.ChunkEvent
import net.minecraftforge.fml.common.gameevent.TickEvent
import org.apache.commons.lang3.SystemUtils
import org.lwjgl.opengl.GL11.GL_LINE_LOOP
import org.lwjgl.opengl.GL11.glLineWidth
import java.io.BufferedWriter
import java.io.FileWriter
import java.io.IOException
import java.io.PrintWriter
import java.nio.file.Files
import java.io.File
import java.lang.Math.sqrt
import java.nio.file.Path
import java.text.SimpleDateFormat
import java.util.Date

object NewChunks : Module(
    name = "NewChunks",
    description = "Highlights newly generated chunks",
    category = Category.RENDER
) {
    private val relative by setting("Relative", false, description = "Renders the chunks at relative Y level to player")
    private val renderMode by setting("Render Mode", RenderMode.BOTH)
    private val saveNewChunks by setting("Save New Chunks", false)
    private val saveOption by setting("SaveOption", SaveOption.EXTRA_FOLDER, { saveNewChunks })
    private val saveInRegionFolder by setting("InRegion", false, { saveNewChunks })
    private val alsoSaveNormalCoords by setting("SaveNormalCoords", false, { saveNewChunks })
    private val closeFile = setting("CloseFile", false, { saveNewChunks })
    private val chunkGridColor by setting("Grid Color", ColorHolder(255, 0, 0, 100), true, { renderMode != RenderMode.WORLD })
    private val distantChunkColor by setting("Distant Chunk Color", ColorHolder(100, 100, 100, 100), true, { renderMode != RenderMode.WORLD }, "Chunks that are not in render distance and not in baritone cache")
    private val newChunkColor by setting("New Chunk Color", ColorHolder(255, 0, 0, 100), true, { renderMode != RenderMode.WORLD })
    private val yOffset by setting("Y Offset", 0, -256..256, 4, fineStep = 1, description = "Render offset in Y axis")
    private val color by setting("Color", ColorHolder(255, 64, 64, 200), description = "Highlighting color")
    private val thickness by setting("Thickness", 1.5f, 0.1f..4.0f, 0.1f, description = "Thickness of the highlighting square")
    private val range by setting("Render Range", 512, 64..2048, 32, description = "Maximum range for chunks to be highlighted")
    private val autoClear by setting("Auto Clear", false, description = "Clears the new chunks every 10 minutes")
    private val removeMode by setting("Remove Mode", RemoveMode.MAX_NUM, description = "Mode to use for removing chunks")
    private val maxNumber by setting("Max Number", 5000, 1000..10000, 500, { removeMode == RemoveMode.MAX_NUM }, description = "Maximum number of chunks to keep")

    private var lastSetting = LastSetting()
    private var logWriter: PrintWriter? = null
    private val timer = TickTimer(TimeUnit.MINUTES)
    private val chunks = HashSet<Chunk>()

    init {
        onDisable {
            logWriterClose()
            chunks.clear()
            MessageSendHelper.sendChatMessage("$chatName Saved and cleared chunks!")
        }

        onEnable {
            timer.reset()
        }
    }

    init {
        safeListener<TickEvent> {
            if (it.phase == TickEvent.Phase.END && autoClear && timer.tick(10L)) {
                chunks.clear()
                MessageSendHelper.sendChatMessage("$chatName Cleared chunks!")
            }
        }

        listener<RenderWorldEvent> {
            if (renderMode == RenderMode.RADAR) return@listener
            val y = yOffset.toDouble() + if (relative) getInterpolatedPos(mc.player, LambdaTessellator.pTicks()).y else 0.0
            glLineWidth(2.0f)
            GlStateUtils.depth(false)
            val color = chunkGridColor
            val buffer = LambdaTessellator.buffer
            for (chunk in chunks) {
                if (sqrt(chunk.pos.getDistanceSq(mc.player)) > range) continue
                LambdaTessellator.begin(GL_LINE_LOOP)
                buffer.pos(chunk.pos.xStart.toDouble(), y, chunk.pos.zStart.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                buffer.pos(chunk.pos.xEnd + 1.toDouble(), y, chunk.pos.zStart.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                buffer.pos(chunk.pos.xEnd + 1.toDouble(), y, chunk.pos.zEnd + 1.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                buffer.pos(chunk.pos.xStart.toDouble(), y, chunk.pos.zEnd + 1.toDouble()).color(color.r, color.g, color.b, 255).endVertex()
                LambdaTessellator.render()
            }
            GlStateUtils.depth(true)
        }

        safeAsyncListener<PacketEvent.PostReceive> { event ->
            if (event.packet !is SPacketChunkData || event.packet.isFullChunk) return@safeAsyncListener
            val chunk = world.getChunk(event.packet.chunkX, event.packet.chunkZ)
            if (saveNewChunks) saveNewChunk(chunk)
            if (removeMode == RemoveMode.MAX_NUM && chunks.size > maxNumber) {
                var removeChunk = chunks.first()
                var maxDist = Double.MIN_VALUE
                chunks.forEach { c ->
                    if (c.pos.getDistanceSq(mc.player) > maxDist) {
                        maxDist = c.pos.getDistanceSq(mc.player)
                        removeChunk = c
                    }
                }
                chunks.remove(removeChunk)
            }
        }


        safeListener<ChunkEvent.Unload> {
            if (removeMode == RemoveMode.UNLOAD)
                chunks.remove(it.chunk)
        }
    }

    // needs to be synchronized so no data gets lost
    private fun saveNewChunk(chunk: Chunk) {
        saveNewChunk(testAndGetLogWriter(), getNewChunkInfo(chunk))
    }

    private fun getNewChunkInfo(chunk: Chunk): String {
        var rV = String.format("%d,%d,%d", System.currentTimeMillis(), chunk.x, chunk.z)
        if (alsoSaveNormalCoords) {
            rV += String.format(",%d,%d", chunk.x * 16 + 8, chunk.z * 16 + 8)
        }
        return rV
    }

    private fun testAndGetLogWriter(): PrintWriter? {
        if (lastSetting.testChangeAndUpdate()) {
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
        val filepath = path.toString()
        try {
            logWriter = PrintWriter(BufferedWriter(FileWriter(filepath, true)), true)
            var head = "timestamp,ChunkX,ChunkZ"
            if (alsoSaveNormalCoords) {
                head += ",x coordinate,z coordinate"
            }
            logWriter!!.println(head)
        } catch (e: Exception) {
            e.printStackTrace()
            LambdaMod.LOG.error(chatName + " some exception happened when trying to start the logging -> " + e.message)
            MessageSendHelper.sendErrorMessage(chatName + " onLogStart: " + e.message)
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
                }

                // Gets the "depth" of this directory relative the the game's run directory, 2 is the location of the world
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

            // maybe we want to save it in region folder
            if (saveInRegionFolder) {
                file = File(file, "region")
            }
            file = File(file, "newChunkLogs")
            val date = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss").format(Date())
            file = File(file, mc.getSession().username + "_" + date + ".csv") // maybe dont safe the name actually. But I also dont want to make another option...
            val rV = file.toPath()
            try {
                if (!Files.exists(rV)) { // ovsly always...
                    Files.createDirectories(rV.parent)
                    Files.createFile(rV)
                }
            } catch (e: IOException) {
                e.printStackTrace()
                LambdaMod.LOG.error("some exception happened when trying to make the file -> " + e.message)
                MessageSendHelper.sendErrorMessage(chatName + " onCreateFile: " + e.message)
            }
            return rV
        }

    private fun makeMultiplayerDirectory(): Path {
        var rV = Minecraft.getMinecraft().gameDir
        var folderName: String
        when (saveOption) {
            SaveOption.LITE_LOADER_WDL -> {
                folderName = mc.currentServerData?.serverName ?: "Offline"
                rV = File(rV, "saves")
                rV = File(rV, folderName)
            }
            SaveOption.NHACK_WDL -> {
                folderName = nHackInetName
                rV = File(rV, "config")
                rV = File(rV, "wdl-saves")
                rV = File(rV, folderName)

                // extra because name might be different
                if (!rV.exists()) {
                    MessageSendHelper.sendWarningMessage("$chatName nhack wdl directory doesnt exist: $folderName")
                    MessageSendHelper.sendWarningMessage("$chatName creating the directory now. It is recommended to update the ip")
                }
            }
            else -> {
                folderName = mc.currentServerData?.serverName + "-" + mc.currentServerData?.serverIP
                if (SystemUtils.IS_OS_WINDOWS) {
                    folderName = folderName.replace(":", "_")
                }
                rV = File(rV, "Lambda_NewChunks")
                rV = File(rV, folderName)
            }
        }
        return rV.toPath()
    }

    // if there is no port then we have to manually include the standard port..
    private val nHackInetName: String
        get() {
            var folderName = mc.currentServerData?.serverIP ?: "Offline"
            if (SystemUtils.IS_OS_WINDOWS) {
                folderName = folderName.replace(":", "_")
            }
            if (hasNoPort(folderName)) {
                folderName += "_25565" // if there is no port then we have to manually include the standard port..
            }
            return folderName
        }

    private fun hasNoPort(ip: String): Boolean {
        if (!ip.contains("_")) {
            return true
        }
        val sp = ip.split("_").toTypedArray()
        val ending = sp[sp.size - 1]
        // if it is numeric it means it might be a port...
        return ending.toIntOrNull() != null
    }

    private fun saveNewChunk(log: PrintWriter?, data: String) {
        log!!.println(data)
    }

    private enum class SaveOption {
        EXTRA_FOLDER, LITE_LOADER_WDL, NHACK_WDL
    }

    @Suppress("unused")
    private enum class RemoveMode {
        UNLOAD, MAX_NUM, NEVER
    }

    enum class RenderMode {
        WORLD, RADAR, BOTH
    }

    val isRadarMode get() = renderMode == RenderMode.BOTH || renderMode == RenderMode.RADAR
    private val isWorldMode get() = renderMode == RenderMode.BOTH || renderMode == RenderMode.WORLD

    private class LastSetting {
        var lastSaveOption: SaveOption? = null
        var lastInRegion = false
        var lastSaveNormal = false
        var dimension = 0
        var ip: String? = null
        fun testChangeAndUpdate(): Boolean {
            if (testChange()) {
                // so we dont have to do this process again next time
                update()
                return true
            }
            return false
        }

        fun testChange(): Boolean {
            // these somehow include the test whether its null
            return saveOption != lastSaveOption
                || saveInRegionFolder != lastInRegion
                || alsoSaveNormalCoords != lastSaveNormal
                || dimension != mc.player.dimension
                || mc.currentServerData?.serverIP != ip
        }

        private fun update() {
            lastSaveOption = saveOption as SaveOption
            lastInRegion = saveInRegionFolder
            lastSaveNormal = alsoSaveNormalCoords
            dimension = mc.player.dimension
            ip = mc.currentServerData?.serverIP
        }
    }

    init {
        closeFile.valueListeners.add { _, _ ->
            if (closeFile.value) {
                logWriterClose()
                MessageSendHelper.sendChatMessage("$chatName Saved file!")
                MessageSendHelper.sendChatMessage("$path")
                closeFile.value = false
            }
        }
    }
}