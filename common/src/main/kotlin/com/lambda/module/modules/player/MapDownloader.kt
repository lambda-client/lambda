/*
 * Copyright 2025 Lambda
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.lambda.module.modules.player

import com.lambda.event.events.TickEvent
import com.lambda.event.listener.SafeListener.Companion.listen
import com.lambda.module.Module
import com.lambda.module.tag.ModuleTag
import com.lambda.util.FolderRegister
import com.lambda.util.FolderRegister.locationBoundDirectory
import com.lambda.util.StringUtils.hash
import com.lambda.util.player.SlotUtils.combined
import com.lambda.util.world.entitySearch
import net.minecraft.block.MapColor
import net.minecraft.entity.decoration.ItemFrameEntity
import net.minecraft.item.FilledMapItem
import net.minecraft.item.map.MapState
import java.awt.image.BufferedImage
import javax.imageio.ImageIO

object MapDownloader : Module(
    name = "MapDownloader",
    description = "Save map data to your computer",
    defaultTags = setOf(ModuleTag.PLAYER),
) {
    init {
        listen<TickEvent.Pre> {
            val mapStates = entitySearch<ItemFrameEntity>(128.0)
                .mapNotNull { FilledMapItem.getMapState(it.heldItemStack, world) } +
                    player.combined.mapNotNull { FilledMapItem.getMapState(it, world) }

            mapStates.forEach { map ->
                val name = map.hash
                val image = map.toBufferedImage()

                val file = FolderRegister.maps.toFile().locationBoundDirectory().resolve("$name.png")
                if (file.exists()) return@listen

                ImageIO.write(image, "png", file)
            }
        }
    }

    private val MapState.hash: String
        get() = colors.hash("SHA-256")

    fun MapState.toBufferedImage(): BufferedImage {
        val image = BufferedImage(128, 128, BufferedImage.TYPE_INT_ARGB)

        repeat(128) { x ->
            repeat(128) { y ->
                val index = colors[x + y * 128].toInt()
                val color = MapColor.getRenderColor(index)

                val b = (color shr 16) and 0xFF
                val g = (color shr 8) and 0xFF
                val r = (color shr 0) and 0xFF

                val argb = -0x1000000 or (r shl 16) or (g shl 8) or (b shl 0)
                image.setRGB(x, y, argb)
            }
        }

        return image
    }
}
