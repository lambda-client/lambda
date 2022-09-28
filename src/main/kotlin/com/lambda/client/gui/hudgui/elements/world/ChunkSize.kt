package com.lambda.client.gui.hudgui.elements.world

import com.lambda.client.commons.utils.MathUtils.round
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.gui.hudgui.LabelHud
import com.lambda.client.mixin.extension.writeChunkToNBT
import net.minecraft.nbt.CompressedStreamTools
import net.minecraft.nbt.NBTTagCompound
import net.minecraft.util.datafix.DataFixer
import net.minecraft.world.chunk.storage.AnvilChunkLoader
import java.io.*
import java.util.zip.DeflaterOutputStream

internal object ChunkSize : LabelHud(
    name = "ChunkSize",
    category = Category.WORLD,
    description = "Display the current size of a chunk"
) {

    private val updateSpeed = setting("Update Ticks", 4, 1..20, 1, description = "How many times to poll the chunk size. May crash the game or kick you if too low!")

    private var currentChunkSize = 0

    override fun SafeClientEvent.updateText() {
        updateChunkSize()
        displayText.add("${round(currentChunkSize / 1000.0, 2)}", primaryColor)
        displayText.add("KB", secondaryColor)
    }

    private fun SafeClientEvent.updateChunkSize() {
        if (player.ticksExisted % updateSpeed.value == 0) {
            currentChunkSize = getChunkSize()
        }
    }

    private fun SafeClientEvent.getChunkSize(): Int {
        val chunk = world.getChunk(player.position)
        if (chunk.isEmpty) return 0

        val root = NBTTagCompound()
        val level = NBTTagCompound()

        root.setTag("Level", level)
        root.setInteger("DataVersion", 6969)

        try {
            val loader = AnvilChunkLoader(File("lambda"), DataFixer(0))
            loader.writeChunkToNBT(chunk, world, level)
        } catch (e: Throwable) {
            e.printStackTrace()
            return 0 // couldn't save
        }

        val compressed = DataOutputStream(BufferedOutputStream(DeflaterOutputStream(ByteArrayOutputStream(8096))))

        return try {
            CompressedStreamTools.write(root, compressed)
            compressed.size()
        } catch (e: IOException) {
            0 // couldn't save
        }
    }
}