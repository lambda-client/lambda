package com.lambda.client.module.modules.misc

import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.text.MessageSendHelper
import com.lambda.client.util.threads.defaultScope
import com.lambda.client.util.threads.runSafe
import com.lambda.client.util.threads.safeListener
import kotlinx.coroutines.launch
import net.minecraft.block.material.MapColor
import net.minecraft.entity.item.EntityItemFrame
import net.minecraft.init.Items
import net.minecraft.item.ItemMap
import net.minecraft.world.storage.MapData
import net.minecraftforge.fml.common.gameevent.TickEvent
import java.awt.Color
import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import javax.imageio.ImageIO

internal object MapDownloader : Module(
    name = "Map Downloader",
    category = Category.MISC,
    description = "Downloads maps in item frames in your render distance to file."
) {

    private var scale by setting("Scale", 1, 1..20, 1)
    private var saveDelay by setting("Save delay", 0.2, 0.1..2.0, 0.1)
    private val pendingHashes = mutableSetOf<String>()
    private var existingHashes = mutableSetOf<String>()
    private var pendingTasks = mutableSetOf<Triple<MapData, String, Int>>()
    private val mapPath = "mapImages${File.separator}"
    private val secTimer = TickTimer(TimeUnit.SECONDS)
    private val milliSecTimer = TickTimer(TimeUnit.MILLISECONDS)

    init {
        val directory = File(mapPath)
        if (!directory.exists()) {
            directory.mkdir()
        }

        existingHashes = getExistingHashes()

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase == TickEvent.Phase.START) {
                if (secTimer.tick(10)) existingHashes = getExistingHashes()
                if (pendingTasks.isNotEmpty()
                    && milliSecTimer.tick((saveDelay * 1000).toInt())) {
                    pendingTasks.firstOrNull()?.let { triple ->
                        defaultScope.launch {
                            runSafe {
                                renderAndSaveMapImage(triple.first, triple.second, triple.third)
                            }
                        }
                        pendingTasks.remove(triple)
                    }
                }

                getMaps()
            }
        }
    }

    private fun getExistingHashes(): MutableSet<String> {
        val alreadyConverted = mutableSetOf<String>()

        File(mapPath).walk().filter {
            it.name.endsWith(".png")
        }.forEach { file ->
            val nameArr = file.name.split("_")
            if (nameArr.isNotEmpty()) {
                alreadyConverted.add(nameArr[0])
            }
        }
        return alreadyConverted
    }

    private fun mapColor(byteColor: UByte): Color {
        val color = MapColor.COLORS[(byteColor / 4u).toInt()]
        return if (color == MapColor.AIR) {
            Color(0,0,0,0)
        } else {
            Color(color.getMapColor((byteColor % 4u).toInt()))
        }
    }

    private fun SafeClientEvent.getMaps() {
        world.loadedEntityList
            .filterIsInstance<EntityItemFrame>()
            .filter { it.displayedItem.item == Items.FILLED_MAP }
            .forEach {
                (it.displayedItem.item as ItemMap).getMapData(it.displayedItem, world)?.let { mapData ->
                    MessageDigest.getInstance("MD5")?.let { md ->
                        val hash = md.digest(mapData.colors).toHex()

                        if (!existingHashes.contains(hash) && !pendingHashes.contains(hash)) {
                            pendingHashes.add(hash)
                            pendingTasks.add(Triple(mapData, hash, it.displayedItem.itemDamage))
                        }
                    } ?: run {
                        MessageSendHelper.sendChatMessage("$chatName Can't find MD5 instance.")
                        disable()
                    }
                }
            }
    }

    private fun SafeClientEvent.renderAndSaveMapImage(mapData: MapData, hash: String, mapID: Int) {
        val finalSize = 128 * scale

        var countPos = 0
        val img = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        val g1 = img.createGraphics()

        g1.background = Color(0, 0, 0, 0)

        repeat(128) { i ->
            repeat(128) { j ->
                mapData.colors[countPos].toUByte().let { mapColor(it).let { it1 -> img.setRGB(j, i, it1.rgb) } }
                countPos++
            }
        }

        try {
            val resized = BufferedImage(finalSize, finalSize, img.type)
            val g = resized.createGraphics()
            val loc = "${mapPath}${hash}_id_${mapID}_server_${player.connection.networkManager.remoteAddress.toString().split('/')[0]?:"local"}.png"
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(img, 0, 0, finalSize, finalSize, 0, 0, img.width,
                img.height, null)
            g.dispose()
            ImageIO.write(resized, "png", File(loc))
        } catch (ex: IOException) {
            ex.printStackTrace()
        }

        pendingHashes.remove(hash)
        existingHashes.add(hash)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }
}
