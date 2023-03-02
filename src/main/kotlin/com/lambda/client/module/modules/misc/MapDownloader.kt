package com.lambda.client.module.modules.misc

import com.lambda.client.LambdaMod
import com.lambda.client.event.SafeClientEvent
import com.lambda.client.module.Category
import com.lambda.client.module.Module
import com.lambda.client.util.FolderUtils
import com.lambda.client.util.TickTimer
import com.lambda.client.util.TimeUnit
import com.lambda.client.util.items.inventorySlots
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
    name = "MapDownloader",
    category = Category.MISC,
    description = "Downloads maps in item frames within your render distance to file"
) {

    private val scale by setting("Scale", 1, 1..20, 1, description = "Higher scale results in higher storage use!")
    private val saveMapsFromEntity by setting("Save maps from entity", true)
    private val saveMapsFromInventory by setting("Save maps from inventory", true, description = "When rendering a new map it will save one image for every map update!")
    private val saveDelay by setting("Save delay", 0.2, 0.1..2.0, 0.1, unit = " s")
    private val openImageFolder = setting("Open Image Folder...", false, consumer = { _, _ ->
        FolderUtils.openFolder(FolderUtils.mapImagesFolder)
        false
    })
    private val pendingHashes = mutableSetOf<String>()
    private var existingHashes = mutableSetOf<String>()
    private var pendingTasks = mutableSetOf<MapInfo>()
    private val secTimer = TickTimer(TimeUnit.SECONDS)
    private val milliSecTimer = TickTimer(TimeUnit.MILLISECONDS)

    init {
        existingHashes = getExistingHashes()

        safeListener<TickEvent.ClientTickEvent> {
            if (it.phase != TickEvent.Phase.START) return@safeListener

            if (secTimer.tick(10)) existingHashes = getExistingHashes()

            if (pendingTasks.isNotEmpty()
                && milliSecTimer.tick((saveDelay * 1000).toInt())
            ) {
                val directory = File(FolderUtils.mapImagesFolder)
                if (!directory.exists()) {
                    directory.mkdir()
                }

                pendingTasks.firstOrNull()?.let { mapInfo ->
                    defaultScope.launch {
                        runSafe {
                            renderAndSaveMapImage(mapInfo)
                            LambdaMod.LOG.info("Saved map - name: ${mapInfo.name} id: ${mapInfo.id}")
                        }
                    }
                    pendingTasks.remove(mapInfo)
                }
            }

            getMaps()
        }
    }

    private fun getExistingHashes(): MutableSet<String> {
        val alreadyConverted = mutableSetOf<String>()

        File(FolderUtils.mapImagesFolder).walk().filter {
            it.name.endsWith(".png")
        }.forEach { file ->
            val nameArr = file.name.split("_")
            if (nameArr.isNotEmpty()) {
                alreadyConverted.add(nameArr[0])
            }
        }

        // to exclude the empty map
        alreadyConverted.add("ce338fe6899778aacfc28414f2d9498b")

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
        if (saveMapsFromEntity) world.loadedEntityList
            .filterIsInstance<EntityItemFrame>()
            .filter { it.displayedItem.item == Items.FILLED_MAP }
            .forEach {
                (it.displayedItem.item as ItemMap).getMapData(it.displayedItem, world)?.let { mapData ->
                    handleMap(mapData, it.displayedItem.displayName, it.displayedItem.itemDamage)
                }
            }

        if (saveMapsFromInventory) player.inventorySlots.forEach {
            if (it.stack.item is ItemMap) {
                (it.stack.item as ItemMap).getMapData(it.stack, world)?.let { mapData ->
                    handleMap(mapData, it.stack.displayName, it.stack.itemDamage)
                }
            }
        }
    }

    private fun handleMap(data: MapData, name: String, id: Int) {
        MessageDigest.getInstance("MD5")?.let { md ->
            val hash = md.digest(data.colors).toHex()

            if (!existingHashes.contains(hash) && !pendingHashes.contains(hash)) {
                pendingHashes.add(hash)
                pendingTasks.add(MapInfo(data, name, id, hash))
            }
        } ?: run {
            MessageSendHelper.sendChatMessage("$chatName Can't find MD5 instance.")
            disable()
        }
    }

    private fun SafeClientEvent.renderAndSaveMapImage(mapInfo: MapInfo) {
        val finalSize = 128 * scale

        var countPos = 0
        val img = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)
        val g1 = img.createGraphics()

        g1.background = Color(0, 0, 0, 0)

        repeat(128) { i ->
            repeat(128) { j ->
                mapInfo.data.colors[countPos].toUByte().let {
                    mapColor(it).let { color -> img.setRGB(j, i, color.rgb) }
                }
                countPos++
            }
        }

        try {
            val resized = BufferedImage(finalSize, finalSize, img.type)
            val g = resized.createGraphics()
            val loc = "${FolderUtils.mapImagesFolder}${mapInfo.hash}_name_${mapInfo.name.replace(File.separator, "")}_id_${mapInfo.id}_${if (mc.isIntegratedServerRunning) "local" else "server_${player.connection.networkManager.remoteAddress.toString().replace("/", "_").replace(":", "_")}"}.png"
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
            g.drawImage(img, 0, 0, finalSize, finalSize, 0, 0, img.width,
                img.height, null)
            g.dispose()
            ImageIO.write(resized, "png", File(loc))
        } catch (ex: IOException) {
            ex.printStackTrace()
        }

        pendingHashes.remove(mapInfo.hash)
        existingHashes.add(mapInfo.hash)
    }

    private fun ByteArray.toHex(): String = joinToString(separator = "") { eachByte -> "%02x".format(eachByte) }

    data class MapInfo(val data: MapData, val name: String, val id: Int, val hash: String)
}
